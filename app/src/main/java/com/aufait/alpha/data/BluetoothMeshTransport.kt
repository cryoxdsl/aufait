package com.aufait.alpha.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothMeshTransport(
    context: Context,
    private val externalScope: CoroutineScope,
    private val fallback: MeshTransport = LoopbackMeshTransport(externalScope)
) : MeshTransport {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _inbound = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 32)
    private val _receipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 32)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val peerTable = linkedMapOf<String, BtPeerRecord>()

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inbound
    override val inboundReceipts: SharedFlow<InboundReceipt> = _receipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    @Volatile private var started = false
    @Volatile private var localAlias: String = "android-alpha"
    @Volatile private var localNodeId: String = ""

    override suspend fun start(localAlias: String, localNodeId: String) {
        if (started) return
        started = true
        this.localAlias = localAlias
        this.localNodeId = localNodeId

        fallback.start(localAlias, localNodeId)
        externalScope.launch {
            fallback.inboundMessages.collect { message ->
                _inbound.emit(message)
            }
        }
        externalScope.launch {
            fallback.inboundReceipts.collect { receipt ->
                _receipts.emit(receipt)
            }
        }

        scope.launch { refreshBondedPeersLoop() }
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
        val payload = JSONObject()
            .put("type", "msg")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("messageId", messageId)
            .put("body", body.take(4000))
            .toString()
        if (!sendFrame(target.endpoint, payload)) {
            fallback.sendMessage(toPeer, messageId, body)
        }
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) {
        val target = resolveTargetPeer(toPeer)
        if (target == null) {
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
        if (!sendFrame(target.endpoint, payload)) {
            fallback.sendReceipt(toPeer, messageId, kind)
        }
    }

    private suspend fun refreshBondedPeersLoop() {
        while (true) {
            refreshBondedPeers()
            delay(5_000L)
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
                    sendFrame(peer.endpoint, hello)
                }
            }
            delay(10_000L)
        }
    }

    private fun refreshBondedPeers() {
        val adapter = bluetoothAdapterOrNull() ?: run {
            clearPeers()
            return
        }
        if (!hasBluetoothConnectPermission() || !adapter.isEnabled) {
            clearPeers()
            return
        }

        val now = System.currentTimeMillis()
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrElse { emptySet() }
        synchronized(peerTable) {
            val seenEndpoints = mutableSetOf<String>()
            bonded.forEach { device ->
                val address = device.address ?: return@forEach
                val endpoint = BT_ENDPOINT_PREFIX + address
                seenEndpoints += endpoint
                val existing = peerTable[endpoint]
                peerTable[endpoint] = BtPeerRecord(
                    alias = existing?.alias?.takeIf { it.isNotBlank() }
                        ?: (device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth ${address.takeLast(5)}"),
                    nodeId = existing?.nodeId ?: "bt-${address.filter { it.isLetterOrDigit() }.lowercase()}",
                    endpoint = endpoint,
                    lastSeenMs = maxOf(existing?.lastSeenMs ?: 0L, now)
                )
            }
            peerTable.entries.removeIf { (_, record) ->
                record.endpoint.startsWith(BT_ENDPOINT_PREFIX) && record.endpoint !in seenEndpoints
            }
            publishPeersLocked()
        }
    }

    private fun clearPeers() {
        synchronized(peerTable) {
            if (peerTable.isEmpty()) return
            peerTable.clear()
            publishPeersLocked()
        }
    }

    private suspend fun serverAcceptLoop() {
        while (true) {
            val adapter = bluetoothAdapterOrNull()
            if (adapter == null || !adapter.isEnabled || !hasBluetoothConnectPermission()) {
                delay(3_000L)
                continue
            }

            runCatching {
                adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID).use { server ->
                    while (true) {
                        val socket = server.accept() ?: continue
                        scope.launch { handleIncomingSocket(socket) }
                    }
                }
            }
            delay(2_000L)
        }
    }

    private fun handleIncomingSocket(socket: android.bluetooth.BluetoothSocket) {
        runCatching {
            val remoteAddress = socket.remoteDevice?.address.orEmpty()
            BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    handleFrame(remoteAddress, line)
                }
            }
        }
        runCatching { socket.close() }
    }

    private fun handleFrame(remoteAddress: String, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = json.optString("type")
        val nodeId = json.optString("nodeId").ifBlank {
            "bt-${remoteAddress.filter { it.isLetterOrDigit() }.lowercase()}"
        }
        if (nodeId == localNodeId) return
        val alias = json.optString("alias").ifBlank {
            "Bluetooth ${remoteAddress.takeLast(5)}"
        }
        upsertPeerFromTraffic(nodeId = nodeId, alias = alias, remoteAddress = remoteAddress)

        when (type) {
            "hello" -> Unit
            "msg" -> {
                val body = json.optString("body")
                val messageId = json.optString("messageId").ifBlank { "bt-${System.currentTimeMillis()}" }
                if (body.isBlank()) return
                externalScope.launch {
                    _inbound.emit(
                        InboundTransportMessage(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            body = body
                        )
                    )
                }
            }
            "receipt" -> {
                val messageId = json.optString("messageId")
                val kind = when (json.optString("receiptKind").lowercase()) {
                    "delivered" -> ReceiptKind.DELIVERED
                    "read" -> ReceiptKind.READ
                    else -> null
                }
                if (messageId.isBlank() || kind == null) return
                externalScope.launch {
                    _receipts.emit(
                        InboundReceipt(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            kind = kind
                        )
                    )
                }
            }
        }
    }

    private fun upsertPeerFromTraffic(nodeId: String, alias: String, remoteAddress: String) {
        if (remoteAddress.isBlank()) return
        val endpoint = BT_ENDPOINT_PREFIX + remoteAddress
        synchronized(peerTable) {
            val existing = peerTable[endpoint]
            peerTable[endpoint] = BtPeerRecord(
                alias = alias,
                nodeId = nodeId,
                endpoint = endpoint,
                lastSeenMs = System.currentTimeMillis().coerceAtLeast(existing?.lastSeenMs ?: 0L)
            )
            publishPeersLocked()
        }
    }

    private fun publishPeersLocked() {
        _peers.value = peerTable.values
            .sortedBy { it.alias.lowercase() }
            .map { record ->
                DiscoveredPeer(
                    alias = record.alias,
                    nodeId = record.nodeId,
                    endpoint = record.endpoint,
                    lastSeenMs = record.lastSeenMs
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

    private fun sendFrame(endpoint: String, payload: String): Boolean {
        val address = endpoint.removePrefix(BT_ENDPOINT_PREFIX)
        if (address == endpoint) return false
        val adapter = bluetoothAdapterOrNull() ?: return false
        if (!hasBluetoothConnectPermission() || !adapter.isEnabled) return false
        return runCatching {
            runCatching { adapter.cancelDiscovery() }
            val device = adapter.getRemoteDevice(address)
            val socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()
            socket.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.append(payload)
                writer.append('\n')
                writer.flush()
            }
            socket.close()
            true
        }.getOrDefault(false)
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

    private data class BtPeerRecord(
        val alias: String,
        val nodeId: String,
        val endpoint: String,
        val lastSeenMs: Long
    )

    companion object {
        private const val SERVICE_NAME = "AufaitAlphaMesh"
        private const val BT_ENDPOINT_PREFIX = "bt:"
        private val SERVICE_UUID: UUID = UUID.fromString("5f7609b9-c4c9-4b5d-b4b8-1f5b9f78d4a1")
    }
}
