package com.aufait.alpha.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.LinkedHashMap
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BluetoothMeshTransport(
    context: Context,
    private val externalScope: CoroutineScope,
    private val fallback: MeshTransport = LoopbackMeshTransport(externalScope)
) : MeshTransport {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()

    private val _inbound = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 32)
    private val _receipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 32)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _diagnostics = MutableStateFlow(TransportDiagnostics())
    private val peerTable = linkedMapOf<String, BtPeerRecord>()
    private val seenInboundEvents = LinkedHashMap<String, Long>(256, 0.75f, true)
    private val bleSeenAddresses = LinkedHashMap<String, Long>(256, 0.75f, true)

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inbound
    override val inboundReceipts: SharedFlow<InboundReceipt> = _receipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()
    val diagnostics: StateFlow<TransportDiagnostics> = _diagnostics.asStateFlow()

    @Volatile private var started = false
    @Volatile private var receiverRegistered = false
    @Volatile private var localAlias: String = "android-alpha"
    @Volatile private var localNodeId: String = ""
    @Volatile private var isDiscoveryActive = false
    @Volatile private var isBleScanActive = false
    @Volatile private var isBleAdvertisingActive = false
    @Volatile private var isServerListening = false
    @Volatile private var bleScanStarted = false
    @Volatile private var bleAdvertiseStarted = false

    private val bleServiceUuid = ParcelUuid(SERVICE_UUID)
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onBleScanResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::onBleScanResult)
        }
        override fun onScanFailed(errorCode: Int) {
            isBleScanActive = false
            updateBtError(IllegalStateException("BLE scan error: $errorCode"))
            refreshDiagnostics()
        }
    }
    private val bleAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isBleAdvertisingActive = true
            refreshDiagnostics()
        }
        override fun onStartFailure(errorCode: Int) {
            isBleAdvertisingActive = false
            updateBtError(IllegalStateException("BLE advertise error: $errorCode"))
            refreshDiagnostics()
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.parcelableBluetoothDevice(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    onDiscoveredDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isDiscoveryActive = true
                    refreshDiagnostics()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isDiscoveryActive = false
                    refreshDiagnostics()
                }
            }
        }
    }

    override suspend fun start(localAlias: String, localNodeId: String) {
        if (started) return
        started = true
        this.localAlias = localAlias
        this.localNodeId = localNodeId

        fallback.start(localAlias, localNodeId)
        externalScope.launch { fallback.inboundMessages.collect { _inbound.emit(it) } }
        externalScope.launch { fallback.inboundReceipts.collect { _receipts.emit(it) } }

        registerDiscoveryReceiverIfNeeded()
        refreshDiagnostics()

        scope.launch { refreshBondedPeersLoop() }
        scope.launch { bleScanLoop() }
        scope.launch { bleAdvertiseLoop() }
        scope.launch { discoveryLoop() }
        scope.launch { serverAcceptLoop() }
        scope.launch { helloLoop() }
    }

    override suspend fun updateLocalAlias(localAlias: String) {
        this.localAlias = localAlias.trim().ifBlank { this.localAlias }
    }

    override suspend fun sendMessage(toPeer: String, messageId: String, body: String) {
        val target = resolveTargetPeer(toPeer)
        if (target == null) {
            fallback.sendMessage(toPeer, messageId, body)
            return
        }
        if (!target.endpointIsBonded()) {
            updateBtError(SecurityException("Bluetooth cible non appairée"))
            fallback.sendMessage(toPeer, messageId, body)
            return
        }
        val payload = JSONObject()
            .put("type", "msg")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("messageId", messageId)
            .put("body", body.take(4000))
            .toString()

        if (!sendFrameWithRetry(target, payload, secure = true)) {
            fallback.sendMessage(toPeer, messageId, body)
        }
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) {
        val target = resolveTargetPeer(toPeer)
        if (target == null) {
            fallback.sendReceipt(toPeer, messageId, kind)
            return
        }
        if (!target.endpointIsBonded()) {
            updateBtError(SecurityException("Bluetooth cible non appairée"))
            fallback.sendReceipt(toPeer, messageId, kind)
            return
        }
        val payload = JSONObject()
            .put("type", "receipt")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("messageId", messageId)
            .put("receiptKind", kind.name.lowercase())
            .toString()

        if (!sendFrameWithRetry(target, payload, secure = true)) {
            fallback.sendReceipt(toPeer, messageId, kind)
        }
    }

    private suspend fun refreshBondedPeersLoop() {
        while (true) {
            refreshBondedPeers()
            delay(BONDED_REFRESH_MS)
        }
    }

    private suspend fun discoveryLoop() {
        while (true) {
            val adapter = bluetoothAdapterOrNull()
            if (adapter == null) {
                isDiscoveryActive = false
                refreshDiagnostics()
                delay(5_000L)
                continue
            }
            if (!adapter.isEnabled || !hasBluetoothScanPermission()) {
                isDiscoveryActive = false
                refreshDiagnostics()
                delay(5_000L)
                continue
            }

            if (!adapter.isDiscovering) {
                runCatching { adapter.startDiscovery() }
            }
            refreshDiagnostics()
            delay(DISCOVERY_RESTART_MS)
        }
    }

    private suspend fun bleScanLoop() {
        while (true) {
            val adapter = bluetoothAdapterOrNull()
            if (adapter == null || !adapter.isEnabled || !hasBluetoothScanPermission()) {
                stopBleScanIfNeeded(adapter)
                isBleScanActive = false
                refreshDiagnostics()
                delay(5_000L)
                continue
            }
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                isBleScanActive = false
                refreshDiagnostics()
                delay(10_000L)
                continue
            }
            if (!bleScanStarted) {
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(bleServiceUuid)
                        .build()
                )
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                runCatching {
                    scanner.startScan(filters, settings, bleScanCallback)
                    bleScanStarted = true
                    isBleScanActive = true
                }.onFailure {
                    isBleScanActive = false
                    updateBtError(it)
                }
                refreshDiagnostics()
            }
            delay(15_000L)
        }
    }

    private suspend fun bleAdvertiseLoop() {
        while (true) {
            val adapter = bluetoothAdapterOrNull()
            if (adapter == null || !adapter.isEnabled || !hasBluetoothAdvertisePermission()) {
                stopBleAdvertisingIfNeeded(adapter)
                isBleAdvertisingActive = false
                refreshDiagnostics()
                delay(5_000L)
                continue
            }
            val advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                isBleAdvertisingActive = false
                refreshDiagnostics()
                delay(10_000L)
                continue
            }
            if (!bleAdvertiseStarted) {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false)
                    .build()
                val data = AdvertiseData.Builder()
                    .addServiceUuid(bleServiceUuid)
                    .addServiceData(bleServiceUuid, buildBleAdvertPayload())
                    .setIncludeDeviceName(false)
                    .build()
                runCatching {
                    advertiser.startAdvertising(settings, data, bleAdvertiseCallback)
                    bleAdvertiseStarted = true
                }.onFailure {
                    isBleAdvertisingActive = false
                    updateBtError(it)
                }
                refreshDiagnostics()
            }
            delay(30_000L)
        }
    }

    private suspend fun helloLoop() {
        while (true) {
            val hello = JSONObject()
                .put("type", "hello")
                .put("nodeId", localNodeId)
                .put("alias", localAlias)
                .toString()
            _peers.value.forEach { peer ->
                if (peer.endpoint.startsWith(BT_ENDPOINT_PREFIX)) {
                    sendFrameWithRetry(peer, hello, secure = false)
                }
            }
            delay(HELLO_INTERVAL_MS)
        }
    }

    private fun refreshBondedPeers() {
        val adapter = bluetoothAdapterOrNull()
        if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
            prunePeersToDiscoveredOnly()
            refreshDiagnostics()
            return
        }

        val now = System.currentTimeMillis()
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrElse { emptySet() }
        synchronized(peerTable) {
            bonded.forEach { device ->
                if (!shouldKeepBondedDevice(device, now)) return@forEach
                val address = device.address ?: return@forEach
                val endpoint = endpointFor(address)
                val existing = peerTable[endpoint]
                peerTable[endpoint] = BtPeerRecord(
                    alias = existing?.alias?.takeIf { it.isNotBlank() }
                        ?: (safeDeviceName(device) ?: "Bluetooth ${address.takeLast(5)}"),
                    nodeId = existing?.nodeId ?: fallbackNodeIdForAddress(address),
                    endpoint = endpoint,
                    lastSeenMs = maxOf(existing?.lastSeenMs ?: 0L, now),
                    bonded = true
                )
            }
            peerTable.entries.removeIf { (_, record) ->
                !record.bonded && now - record.lastSeenMs > DISCOVERED_TTL_MS
            }
            publishPeersLocked()
        }
        refreshDiagnostics()
    }

    private fun prunePeersToDiscoveredOnly() {
        synchronized(peerTable) {
            val changed = peerTable.entries.removeIf { (_, record) ->
                !record.bonded && (System.currentTimeMillis() - record.lastSeenMs > DISCOVERED_TTL_MS)
            }
            if (changed) publishPeersLocked()
        }
    }

    private suspend fun serverAcceptLoop() {
        while (true) {
            val adapter = bluetoothAdapterOrNull()
            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                isServerListening = false
                refreshDiagnostics()
                delay(3_000L)
                continue
            }

            val accepted = runServerSockets(adapter)
            if (!accepted) {
                updateBtError(SecurityException("Impossible d'ouvrir un serveur Bluetooth RFCOMM"))
            }
            isServerListening = false
            refreshDiagnostics()
            delay(2_000L)
        }
    }

    private fun runServerSockets(adapter: BluetoothAdapter): Boolean {
        val factories = listOf<(BluetoothAdapter) -> BluetoothServerSocket>(
            { it.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID) },
            { it.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID) }
        )
        factories.forEach { createServer ->
            val result = runCatching {
                createServer(adapter).use { server ->
                    isServerListening = true
                    refreshDiagnostics()
                    while (true) {
                        val socket = server.accept() ?: continue
                        scope.launch { handleIncomingSocket(socket) }
                    }
                }
            }
            if (result.isSuccess) return true
            updateBtError(result.exceptionOrNull() ?: IllegalStateException("server failure"))
        }
        return false
    }

    private fun handleIncomingSocket(socket: BluetoothSocket) {
        runCatching {
            val remoteAddress = socket.remoteDevice?.address.orEmpty()
            if (remoteAddress.isBlank()) return@runCatching
            DataInputStream(socket.inputStream).use { input ->
                while (true) {
                    val frame = readFrame(input) ?: break
                    handleFrame(remoteAddress, frame)
                }
            }
        }.onFailure {
            updateBtError(it)
        }
        runCatching { socket.close() }
    }

    private fun readFrame(input: DataInputStream): ByteArray? {
        return runCatching {
            val length = input.readInt()
            if (length <= 0 || length > MAX_FRAME_BYTES) return null
            ByteArray(length).also { input.readFully(it) }
        }.getOrNull()
    }

    private fun handleFrame(remoteAddress: String, frameBytes: ByteArray) {
        if (frameBytes.isEmpty() || frameBytes.size > MAX_FRAME_BYTES) return
        val outerText = runCatching { frameBytes.toString(Charsets.UTF_8) }.getOrNull() ?: return
        if (outerText.length > MAX_JSON_CHARS) return
        val outerJson = runCatching { JSONObject(outerText) }.getOrNull() ?: return
        val effectiveJson = if (outerJson.optString("type") == "secure") {
            unwrapSecurePayload(outerJson) ?: return
        } else {
            outerJson
        }
        handlePayloadJson(remoteAddress, effectiveJson)
    }

    private fun handlePayloadJson(remoteAddress: String, json: JSONObject) {
        val type = json.optString("type")
        if (type !in ALLOWED_TYPES) return
        val nodeId = json.optString("nodeId").ifBlank { fallbackNodeIdForAddress(remoteAddress) }
            .take(MAX_NODE_ID_CHARS)
        if (nodeId == localNodeId) return
        val alias = json.optString("alias").ifBlank { "Bluetooth ${remoteAddress.takeLast(5)}" }
            .take(MAX_ALIAS_CHARS)
        if (type != "hello" && !addressIsBonded(remoteAddress)) {
            updateBtError(SecurityException("Trame Bluetooth refusée (device non appairé)"))
            return
        }
        upsertPeerFromTraffic(nodeId = nodeId, alias = alias, remoteAddress = remoteAddress)

        when (type) {
            "hello" -> Unit
            "msg" -> {
                val body = json.optString("body").take(MAX_BODY_CHARS)
                val messageId = json.optString("messageId")
                    .ifBlank { "bt-${System.currentTimeMillis()}" }
                    .take(MAX_MESSAGE_ID_CHARS)
                if (body.isBlank()) return
                if (isDuplicateInboundEvent("$nodeId|msg|$messageId")) return
                externalScope.launch {
                    _inbound.emit(
                        InboundTransportMessage(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            body = body,
                            channel = MessageTransportChannel.BLUETOOTH
                        )
                    )
                }
            }
            "receipt" -> {
                val messageId = json.optString("messageId").take(MAX_MESSAGE_ID_CHARS)
                val kind = when (json.optString("receiptKind").lowercase()) {
                    "delivered" -> ReceiptKind.DELIVERED
                    "read" -> ReceiptKind.READ
                    else -> null
                }
                if (messageId.isBlank() || kind == null) return
                if (isDuplicateInboundEvent("$nodeId|receipt|$messageId|${kind.name}")) return
                externalScope.launch {
                    _receipts.emit(
                        InboundReceipt(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            kind = kind,
                            channel = MessageTransportChannel.BLUETOOTH
                        )
                    )
                }
            }
        }
    }

    private fun onDiscoveredDevice(device: BluetoothDevice) {
        if (!isSupportedMobileDevice(device)) return
        if (!deviceMatchesEnFaitPrefix(device)) return
        val address = device.address ?: return
        val endpoint = endpointFor(address)
        synchronized(peerTable) {
            val existing = peerTable[endpoint]
            peerTable[endpoint] = BtPeerRecord(
                alias = existing?.alias ?: (safeDeviceName(device) ?: "Bluetooth ${address.takeLast(5)}"),
                nodeId = existing?.nodeId ?: fallbackNodeIdForAddress(address),
                endpoint = endpoint,
                lastSeenMs = System.currentTimeMillis(),
                bonded = existing?.bonded == true || device.bondState == BluetoothDevice.BOND_BONDED
            )
            publishPeersLocked()
        }
        refreshDiagnostics()
    }

    private fun onBleScanResult(result: ScanResult) {
        val device = result.device ?: return
        if (!isSupportedMobileDevice(device)) return
        val scanRecord = result.scanRecord ?: return
        val serviceData = scanRecord.getServiceData(bleServiceUuid) ?: return
        if (!isEnFaitBleAdvert(serviceData)) return
        val address = device.address ?: return
        val alias = parseBleAdvertAlias(serviceData)
            ?: safeDeviceName(device)
            ?: "${ENFAIT_DEVICE_PREFIX}${address.filter { it.isLetterOrDigit() }.takeLast(4)}"
        val now = System.currentTimeMillis()
        synchronized(bleSeenAddresses) {
            bleSeenAddresses[address] = now
            bleSeenAddresses.entries.removeIf { now - it.value > BLE_SEEN_TTL_MS }
            while (bleSeenAddresses.size > MAX_TRACKED_BLE_DEVICES) {
                val eldest = bleSeenAddresses.entries.iterator().next().key
                bleSeenAddresses.remove(eldest)
            }
        }
        val endpoint = endpointFor(address)
        synchronized(peerTable) {
            val existing = peerTable[endpoint]
            peerTable[endpoint] = BtPeerRecord(
                alias = alias.take(MAX_ALIAS_CHARS),
                nodeId = existing?.nodeId ?: fallbackNodeIdForAddress(address),
                endpoint = endpoint,
                lastSeenMs = now,
                bonded = existing?.bonded == true || device.bondState == BluetoothDevice.BOND_BONDED
            )
            publishPeersLocked()
        }
        refreshDiagnostics()
    }

    private fun isDuplicateInboundEvent(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(seenInboundEvents) {
            seenInboundEvents.entries.removeIf { now - it.value > INBOUND_EVENT_TTL_MS }
            val exists = seenInboundEvents.containsKey(key)
            if (!exists) {
                seenInboundEvents[key] = now
                while (seenInboundEvents.size > MAX_TRACKED_EVENTS) {
                    val eldestKey = seenInboundEvents.entries.iterator().next().key
                    seenInboundEvents.remove(eldestKey)
                }
            }
            return exists
        }
    }

    private suspend fun sendFrameWithRetry(
        target: DiscoveredPeer,
        payload: String,
        secure: Boolean
    ): Boolean {
        RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
            if (index > 0) delay(delayMs)
            if (sendFrameOnce(target, payload, secure)) return true
        }
        return false
    }

    private fun sendFrameOnce(target: DiscoveredPeer, payload: String, secure: Boolean): Boolean {
        val address = target.endpoint.removePrefix(BT_ENDPOINT_PREFIX)
        if (address == target.endpoint) return false
        val adapter = bluetoothAdapterOrNull() ?: return false
        if (!adapter.isEnabled || !hasBluetoothConnectPermission()) return false

        val frameBytes = buildFrameBytes(target, payload, secure) ?: return false
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        return connectAndSend(adapter, device, frameBytes)
    }

    private fun connectAndSend(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        frameBytes: ByteArray
    ): Boolean {
        val socketFactories = listOf<(BluetoothDevice) -> BluetoothSocket>(
            { it.createRfcommSocketToServiceRecord(SERVICE_UUID) },
            { it.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID) }
        )
        socketFactories.forEach { createSocket ->
            val sent = runCatching {
                runCatching { adapter.cancelDiscovery() }
                val socket = createSocket(device)
                socket.connect()
                DataOutputStream(socket.outputStream).use { output ->
                    output.writeInt(frameBytes.size)
                    output.write(frameBytes)
                    output.flush()
                }
                socket.close()
                true
            }.onFailure {
                updateBtError(it)
            }.getOrDefault(false)
            if (sent) return true
        }
        return false
    }

    private fun buildFrameBytes(target: DiscoveredPeer, payload: String, secure: Boolean): ByteArray? {
        if (!secure) return payload.toByteArray(Charsets.UTF_8)
        val remoteNodeId = target.nodeId.takeIf { it.isNotBlank() } ?: return payload.toByteArray(Charsets.UTF_8)
        val encrypted = encryptTransportPayload(remoteNodeId, payload) ?: return payload.toByteArray(Charsets.UTF_8)
        return JSONObject()
            .put("type", "secure")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("alg", "aes-gcm")
            .put("iv", encrypted.ivBase64)
            .put("cipher", encrypted.cipherBase64)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun unwrapSecurePayload(wrapper: JSONObject): JSONObject? {
        val remoteNodeId = wrapper.optString("nodeId")
        val iv = wrapper.optString("iv")
        val cipher = wrapper.optString("cipher")
        if (iv.length > MAX_BASE64_CHARS || cipher.length > MAX_BASE64_CHARS) return null
        if (remoteNodeId.isBlank() || iv.isBlank() || cipher.isBlank()) return null
        val plaintext = decryptTransportPayload(remoteNodeId, iv, cipher) ?: return null
        if (plaintext.length > MAX_JSON_CHARS) return null
        return runCatching { JSONObject(plaintext) }.getOrNull()
    }

    private fun encryptTransportPayload(remoteNodeId: String, plaintext: String): TransportEncryptedPayload? {
        if (localNodeId.isBlank()) return null
        return runCatching {
            val key = deriveTransportKey(localNodeId, remoteNodeId)
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            TransportEncryptedPayload(
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                cipherBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
        }.getOrNull()
    }

    private fun decryptTransportPayload(remoteNodeId: String, ivBase64: String, cipherBase64: String): String? {
        if (localNodeId.isBlank()) return null
        return runCatching {
            val key = deriveTransportKey(localNodeId, remoteNodeId)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun deriveTransportKey(localNodeId: String, remoteNodeId: String): SecretKeySpec {
        val (a, b) = if (localNodeId <= remoteNodeId) localNodeId to remoteNodeId else remoteNodeId to localNodeId
        val material = "$TRANSPORT_KEY_SALT|$a|$b".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return SecretKeySpec(digest.copyOf(16), "AES")
    }

    private fun upsertPeerFromTraffic(nodeId: String, alias: String, remoteAddress: String) {
        if (remoteAddress.isBlank()) return
        val endpoint = endpointFor(remoteAddress)
        synchronized(peerTable) {
            val existing = peerTable[endpoint]
            peerTable[endpoint] = BtPeerRecord(
                alias = alias,
                nodeId = nodeId,
                endpoint = endpoint,
                lastSeenMs = System.currentTimeMillis(),
                bonded = existing?.bonded == true
            )
            publishPeersLocked()
        }
        refreshDiagnostics()
    }

    private fun publishPeersLocked() {
        _peers.value = peerTable.values
            .sortedBy { it.alias.lowercase() }
            .map {
                DiscoveredPeer(
                    alias = it.alias,
                    nodeId = it.nodeId,
                    endpoint = it.endpoint,
                    lastSeenMs = it.lastSeenMs
                )
            }
    }

    private fun resolveTargetPeer(toPeer: String): DiscoveredPeer? {
        val snapshot = _peers.value
        if (snapshot.isEmpty()) return null
        return snapshot.firstOrNull {
            it.alias.equals(toPeer, ignoreCase = true) ||
                it.nodeId.equals(toPeer, ignoreCase = true) ||
                it.nodeId.startsWith(toPeer, ignoreCase = true)
        } ?: snapshot.firstOrNull()
    }

    private fun registerDiscoveryReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun refreshDiagnostics() {
        val adapter = bluetoothAdapterOrNull()
        val permissionGranted = hasBluetoothConnectPermission()
        val scanPermissionGranted = hasBluetoothScanPermission()
        val bluetoothEnabled = if (permissionGranted) {
            runCatching { adapter?.isEnabled == true }
                .onFailure { updateBtError(it) }
                .getOrDefault(false)
        } else {
            false
        }
        val adapterDiscoveryActive = if (scanPermissionGranted) {
            runCatching { adapter?.isDiscovering == true }
                .onFailure { updateBtError(it) }
                .getOrDefault(false)
        } else {
            false
        }
        _diagnostics.value = _diagnostics.value.copy(
            bluetoothPeerCount = _peers.value.size,
            bluetoothEnabled = bluetoothEnabled,
            bluetoothPermissionGranted = permissionGranted && scanPermissionGranted,
            bluetoothDiscoveryActive = isDiscoveryActive || adapterDiscoveryActive || isBleScanActive || isBleAdvertisingActive,
            bluetoothServerListening = isServerListening
        )
    }

    private fun updateBtError(error: Throwable) {
        if (isBenignBluetoothSocketClosure(error)) return
        _diagnostics.value = _diagnostics.value.copy(
            bluetoothLastError = error.message ?: error::class.java.simpleName
        )
    }

    private fun isBenignBluetoothSocketClosure(error: Throwable): Boolean {
        if (error is EOFException) return true
        val root = generateSequence(error) { it.cause }.lastOrNull() ?: error
        if (root is EOFException) return true
        if (root is IOException) {
            val msg = (root.message ?: "").lowercase()
            if ("read ret: -1" in msg) return true
            if ("socket might closed or timeout" in msg) return true
            if ("software caused connection abort" in msg) return true
            if ("connection reset by peer" in msg) return true
            if ("broken pipe" in msg) return true
        }
        return false
    }

    private fun endpointFor(address: String): String = BT_ENDPOINT_PREFIX + address

    private fun DiscoveredPeer.endpointIsBonded(): Boolean {
        val address = endpoint.removePrefix(BT_ENDPOINT_PREFIX)
        if (address == endpoint) return false
        return addressIsBonded(address)
    }

    private fun addressIsBonded(address: String): Boolean {
        val adapter = bluetoothAdapterOrNull() ?: return false
        if (!adapter.isEnabled || !hasBluetoothConnectPermission()) return false
        return runCatching {
            adapter.bondedDevices.orEmpty().any { it.address.equals(address, ignoreCase = true) }
        }.getOrDefault(false)
    }

    private fun fallbackNodeIdForAddress(address: String): String =
        "bt-${address.filter { it.isLetterOrDigit() }.lowercase()}"

    private fun safeDeviceName(device: BluetoothDevice): String? =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun shouldKeepBondedDevice(device: BluetoothDevice, now: Long): Boolean {
        if (!isSupportedMobileDevice(device)) return false
        if (deviceMatchesEnFaitPrefix(device)) return true
        val address = device.address ?: return false
        synchronized(bleSeenAddresses) {
            bleSeenAddresses.entries.removeIf { now - it.value > BLE_SEEN_TTL_MS }
            return bleSeenAddresses[address]?.let { now - it <= BLE_SEEN_TTL_MS } == true
        }
    }

    private fun isSupportedMobileDevice(device: BluetoothDevice): Boolean {
        val klass = runCatching { device.bluetoothClass }.getOrNull()
        return when (klass?.majorDeviceClass) {
            null -> true
            BluetoothClass.Device.Major.PHONE -> true
            BluetoothClass.Device.Major.COMPUTER -> true
            else -> false
        }
    }

    private fun deviceMatchesEnFaitPrefix(device: BluetoothDevice): Boolean {
        val name = safeDeviceName(device) ?: return false
        return name.startsWith(ENFAIT_DEVICE_PREFIX, ignoreCase = true)
    }

    private fun buildBleAdvertPayload(): ByteArray {
        val alias = localAlias.trim().ifBlank { "${ENFAIT_DEVICE_PREFIX}${localNodeId.takeLast(4)}" }
        val payload = "$BLE_SERVICE_PREFIX|${alias.take(12)}"
        val bytes = payload.toByteArray(Charsets.UTF_8)
        return if (bytes.size <= MAX_BLE_SERVICE_DATA_BYTES) bytes else bytes.copyOf(MAX_BLE_SERVICE_DATA_BYTES)
    }

    private fun isEnFaitBleAdvert(serviceData: ByteArray): Boolean {
        val text = runCatching { serviceData.toString(Charsets.UTF_8) }.getOrNull() ?: return false
        return text.startsWith(BLE_SERVICE_PREFIX)
    }

    private fun parseBleAdvertAlias(serviceData: ByteArray): String? {
        val text = runCatching { serviceData.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        if (!text.startsWith(BLE_SERVICE_PREFIX)) return null
        val raw = text.substringAfter('|', "").trim().take(MAX_ALIAS_CHARS)
        if (raw.isBlank()) return null
        return if (raw.startsWith(ENFAIT_DEVICE_PREFIX, ignoreCase = true)) raw else "${ENFAIT_DEVICE_PREFIX}${raw}"
    }

    private fun stopBleScanIfNeeded(adapter: BluetoothAdapter?) {
        if (!bleScanStarted) return
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull() ?: return
        runCatching { scanner.stopScan(bleScanCallback) }
        bleScanStarted = false
        isBleScanActive = false
    }

    private fun stopBleAdvertisingIfNeeded(adapter: BluetoothAdapter?) {
        if (!bleAdvertiseStarted) return
        val advertiser = runCatching { adapter?.bluetoothLeAdvertiser }.getOrNull() ?: return
        runCatching { advertiser.stopAdvertising(bleAdvertiseCallback) }
        bleAdvertiseStarted = false
        isBleAdvertisingActive = false
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableBluetoothDevice(key: String): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(key)
        }
    }

    private fun bluetoothAdapterOrNull(): BluetoothAdapter? {
        val manager = ContextCompat.getSystemService(appContext, BluetoothManager::class.java)
        return manager?.adapter
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothAdvertisePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class BtPeerRecord(
        val alias: String,
        val nodeId: String,
        val endpoint: String,
        val lastSeenMs: Long,
        val bonded: Boolean
    )

    private data class TransportEncryptedPayload(
        val ivBase64: String,
        val cipherBase64: String
    )

    companion object {
        private const val SERVICE_NAME = "AufaitAlphaMesh"
        private const val BT_ENDPOINT_PREFIX = "bt:"
        private const val MAX_FRAME_BYTES = 64 * 1024
        private const val MAX_JSON_CHARS = 32 * 1024
        private const val MAX_BASE64_CHARS = 48 * 1024
        private const val MAX_ALIAS_CHARS = 64
        private const val MAX_NODE_ID_CHARS = 128
        private const val MAX_MESSAGE_ID_CHARS = 128
        private const val MAX_BODY_CHARS = 4_000
        private const val HELLO_INTERVAL_MS = 10_000L
        private const val BONDED_REFRESH_MS = 5_000L
        private const val DISCOVERY_RESTART_MS = 20_000L
        private const val DISCOVERED_TTL_MS = 30_000L
        private const val INBOUND_EVENT_TTL_MS = 2 * 60_000L
        private const val MAX_TRACKED_EVENTS = 1024
        private val RETRY_DELAYS_MS = longArrayOf(0L, 300L, 900L)
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TRANSPORT_KEY_SALT = "aufait-alpha-bt-transport-v1"
        private val ALLOWED_TYPES = setOf("hello", "msg", "receipt")
        private val SERVICE_UUID: UUID = UUID.fromString("5f7609b9-c4c9-4b5d-b4b8-1f5b9f78d4a1")
        private const val ENFAIT_DEVICE_PREFIX = "EnFait-"
        private const val BLE_SERVICE_PREFIX = "ENF1"
        private const val BLE_SEEN_TTL_MS = 90_000L
        private const val MAX_TRACKED_BLE_DEVICES = 256
        private const val MAX_BLE_SERVICE_DATA_BYTES = 24
    }
}
