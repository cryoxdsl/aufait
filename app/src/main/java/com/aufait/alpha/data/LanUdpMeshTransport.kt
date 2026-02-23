package com.aufait.alpha.data

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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

class LanUdpMeshTransport(
    private val externalScope: CoroutineScope,
    private val fallback: MeshTransport = LoopbackMeshTransport(externalScope)
) : MeshTransport, TransportControl {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _inbound = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 32)
    private val _receipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 32)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _fallbackPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _diagnostics = MutableStateFlow(TransportDiagnostics())

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inbound
    override val inboundReceipts: SharedFlow<InboundReceipt> = _receipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()
    override val diagnostics: StateFlow<TransportDiagnostics> = _diagnostics.asStateFlow()

    private val peerTable = ConcurrentHashMap<String, PeerRecord>()
    @Volatile private var receiveSocket: DatagramSocket? = null
    @Volatile private var started = false
    @Volatile private var routingMode: TransportRoutingMode = TransportRoutingMode.AUTO
    @Volatile private var localAlias: String = "android-alpha"
    @Volatile private var localNodeId: String = ""

    override suspend fun start(localAlias: String, localNodeId: String) {
        if (started) return
        this.localAlias = localAlias
        this.localNodeId = localNodeId
        started = true

        fallback.start(localAlias, localNodeId)
        externalScope.launch {
            fallback.inboundMessages.collect { msg ->
                _inbound.emit(msg)
            }
        }
        externalScope.launch {
            fallback.inboundReceipts.collect { receipt ->
                _receipts.emit(receipt)
            }
        }
        externalScope.launch {
            fallback.peers.collect { peers ->
                _fallbackPeers.value = peers
                publishPeers()
                refreshDiagnostics()
            }
        }
        if (fallback is BluetoothMeshTransport) {
            externalScope.launch {
                fallback.diagnostics.collect {
                    refreshDiagnostics()
                }
            }
        }

        scope.launch { receiveLoop() }
        scope.launch { beaconLoop() }
        scope.launch { cleanupLoop() }
        refreshDiagnostics()
    }

    override suspend fun updateLocalAlias(localAlias: String) {
        this.localAlias = localAlias.trim().ifBlank { this.localAlias }
    }

    override suspend fun setRoutingMode(mode: TransportRoutingMode) {
        routingMode = mode
        publishPeers()
        refreshDiagnostics()
    }

    override suspend fun sendMessage(toPeer: String, messageId: String, body: String) {
        if (routingMode == TransportRoutingMode.BLUETOOTH_ONLY) {
            fallback.sendMessage(toPeer, messageId, body)
            return
        }
        val peersSnapshot = currentLanPeers()
        if (peersSnapshot.isEmpty()) {
            if (routingMode == TransportRoutingMode.LAN_ONLY) return
            fallback.sendMessage(toPeer, messageId, body)
            return
        }

        val target = peersSnapshot.firstOrNull {
            it.alias.equals(toPeer, ignoreCase = true) || it.nodeId.startsWith(toPeer, ignoreCase = true)
        } ?: peersSnapshot.first()

        val payload = JSONObject()
            .put("type", "msg")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("messageId", messageId)
            .put("body", body.take(4000))
            .toString()

        sendDatagram(
            data = payload.toByteArray(Charsets.UTF_8),
            address = InetAddress.getByName(target.endpoint.substringBefore(':')),
            port = target.endpoint.substringAfter(':').toIntOrNull() ?: PORT
        )
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) {
        if (routingMode == TransportRoutingMode.BLUETOOTH_ONLY) {
            fallback.sendReceipt(toPeer, messageId, kind)
            return
        }
        val peersSnapshot = currentLanPeers()
        if (peersSnapshot.isEmpty()) {
            if (routingMode == TransportRoutingMode.LAN_ONLY) return
            fallback.sendReceipt(toPeer, messageId, kind)
            return
        }

        val target = peersSnapshot.firstOrNull {
            it.alias.equals(toPeer, ignoreCase = true) || it.nodeId.startsWith(toPeer, ignoreCase = true)
        } ?: return

        val payload = JSONObject()
            .put("type", "receipt")
            .put("nodeId", localNodeId)
            .put("alias", localAlias)
            .put("messageId", messageId)
            .put("receiptKind", kind.name.lowercase())
            .toString()

        sendDatagram(
            data = payload.toByteArray(Charsets.UTF_8),
            address = InetAddress.getByName(target.endpoint.substringBefore(':')),
            port = target.endpoint.substringAfter(':').toIntOrNull() ?: PORT
        )
    }

    private suspend fun beaconLoop() {
        while (true) {
            runCatching {
                val payload = JSONObject()
                    .put("type", "hello")
                    .put("nodeId", localNodeId)
                    .put("alias", localAlias)
                    .put("port", PORT)
                    .toString()
                sendDatagram(payload.toByteArray(Charsets.UTF_8), InetAddress.getByName(BROADCAST_ADDR), PORT)
            }
            delay(BEACON_INTERVAL_MS)
        }
    }

    private suspend fun cleanupLoop() {
        while (true) {
            delay(PEER_TTL_MS / 2)
            val cutoff = System.currentTimeMillis() - PEER_TTL_MS
            val changed = peerTable.entries.removeIf { it.value.lastSeenMs < cutoff }
            if (changed) publishPeers()
        }
    }

    private fun receiveLoop() {
        val socket = try {
            DatagramSocket(PORT).apply {
                broadcast = true
                soTimeout = 0
                reuseAddress = true
            }
        } catch (e: SocketException) {
            // Si le port est occupe, on reste fonctionnel via le fallback.
            return
        }
        receiveSocket = socket

        val buffer = ByteArray(8192)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                handlePacket(packet.address, packet.port, text)
            } catch (_: Exception) {
                // Ignore malformed/temporary network errors in alpha.
            }
        }
    }

    private fun handlePacket(address: InetAddress, port: Int, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val type = json.optString("type")
        val nodeId = json.optString("nodeId")
        if (nodeId.isBlank() || nodeId == localNodeId) return

        val alias = json.optString("alias").ifBlank { "peer-${nodeId.take(6)}" }
        val endpointPort = json.optInt("port", port).takeIf { it > 0 } ?: PORT
        upsertPeer(
            nodeId = nodeId,
            alias = alias,
            host = address.hostAddress ?: return,
            port = endpointPort
        )

        if (type == "msg") {
            val body = json.optString("body")
            val messageId = json.optString("messageId").ifBlank { "remote-${System.currentTimeMillis()}" }
            if (body.isNotBlank()) {
                externalScope.launch {
                    _inbound.emit(
                        InboundTransportMessage(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            body = body,
                            channel = MessageTransportChannel.WIFI
                        )
                    )
                }
            }
        } else if (type == "receipt") {
            val messageId = json.optString("messageId")
            val kind = when (json.optString("receiptKind").lowercase()) {
                "delivered" -> ReceiptKind.DELIVERED
                "read" -> ReceiptKind.READ
                else -> null
            }
            if (messageId.isNotBlank() && kind != null) {
                externalScope.launch {
                    _receipts.emit(
                        InboundReceipt(
                            messageId = messageId,
                            fromPeer = alias,
                            fromNodeId = nodeId,
                            kind = kind,
                            channel = MessageTransportChannel.WIFI
                        )
                    )
                }
            }
        }
    }

    private fun upsertPeer(nodeId: String, alias: String, host: String, port: Int) {
        peerTable[nodeId] = PeerRecord(
            alias = alias,
            host = host,
            port = port,
            lastSeenMs = System.currentTimeMillis()
        )
        publishPeers()
        refreshDiagnostics()
    }

    private fun publishPeers() {
        val lanPeers = currentLanPeers()
        _peers.value = when (routingMode) {
            TransportRoutingMode.LAN_ONLY -> lanPeers
            TransportRoutingMode.BLUETOOTH_ONLY -> _fallbackPeers.value
            TransportRoutingMode.AUTO -> if (lanPeers.isNotEmpty()) lanPeers else _fallbackPeers.value
        }
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        val lanCount = peerTable.size
        val fallbackDiag = (fallback as? BluetoothMeshTransport)?.diagnostics?.value
        _diagnostics.value = TransportDiagnostics(
            routingMode = routingMode,
            lanPeerCount = lanCount,
            bluetoothPeerCount = _fallbackPeers.value.size,
            bluetoothEnabled = fallbackDiag?.bluetoothEnabled ?: false,
            bluetoothPermissionGranted = fallbackDiag?.bluetoothPermissionGranted ?: false,
            bluetoothDiscoveryActive = fallbackDiag?.bluetoothDiscoveryActive ?: false,
            bluetoothServerListening = fallbackDiag?.bluetoothServerListening ?: false,
            bluetoothLastError = fallbackDiag?.bluetoothLastError
        )
    }

    private fun currentLanPeers(): List<DiscoveredPeer> {
        return peerTable.entries
            .sortedBy { it.value.alias.lowercase() }
            .map { (nodeId, record) ->
                DiscoveredPeer(
                    alias = record.alias,
                    nodeId = nodeId,
                    endpoint = "${record.host}:${record.port}",
                    lastSeenMs = record.lastSeenMs
                )
            }
    }

    private fun sendDatagram(data: ByteArray, address: InetAddress, port: Int) {
        val packet = DatagramPacket(data, data.size, address, port)
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.send(packet)
        }
    }

    private data class PeerRecord(
        val alias: String,
        val host: String,
        val port: Int,
        val lastSeenMs: Long
    )

    companion object {
        private const val PORT = 40444
        private const val BROADCAST_ADDR = "255.255.255.255"
        private const val BEACON_INTERVAL_MS = 2_000L
        private const val PEER_TTL_MS = 8_000L
    }
}
