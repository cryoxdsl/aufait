package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class InboundTransportMessage(
    val messageId: String,
    val fromPeer: String,
    val fromNodeId: String = "",
    val body: String,
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class InboundReceipt(
    val messageId: String,
    val fromPeer: String,
    val fromNodeId: String = "",
    val kind: ReceiptKind,
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class DiscoveredPeer(
    val alias: String,
    val nodeId: String,
    val endpoint: String,
    val lastSeenMs: Long
)

interface MeshTransport {
    val inboundMessages: SharedFlow<InboundTransportMessage>
    val inboundReceipts: SharedFlow<InboundReceipt>
    val peers: StateFlow<List<DiscoveredPeer>>
    suspend fun start(localAlias: String, localNodeId: String)
    suspend fun updateLocalAlias(localAlias: String)
    suspend fun sendMessage(toPeer: String, messageId: String, body: String)
    suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind)
}

interface TransportControl {
    val diagnostics: StateFlow<TransportDiagnostics>
    suspend fun setRoutingMode(mode: TransportRoutingMode)
}

interface RelayTransportControl {
    val diagnostics: StateFlow<RelayDiagnostics>
    suspend fun setRelayNetworkMode(mode: RelayNetworkMode)
    suspend fun setTorFallbackPolicy(policy: TorFallbackPolicy)
    suspend fun setSharedSecret(secret: String?)
}

class LoopbackMeshTransport(
    private val scope: CoroutineScope
) : MeshTransport {
    private val _inbound = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 16)
    private val _receipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 16)
    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inbound
    override val inboundReceipts: SharedFlow<InboundReceipt> = _receipts
    override val peers: StateFlow<List<DiscoveredPeer>> = MutableStateFlow(emptyList())

    override suspend fun start(localAlias: String, localNodeId: String) = Unit
    override suspend fun updateLocalAlias(localAlias: String) = Unit

    override suspend fun sendMessage(toPeer: String, messageId: String, body: String) {
        scope.launch {
            delay(150)
            _receipts.emit(
                InboundReceipt(
                    messageId = messageId,
                    fromPeer = toPeer,
                    kind = ReceiptKind.DELIVERED
                )
            )
            delay(250)
            _receipts.emit(
                InboundReceipt(
                    messageId = messageId,
                    fromPeer = toPeer,
                    kind = ReceiptKind.READ
                )
            )
        }
        scope.launch {
            delay(650)
            _inbound.emit(
                InboundTransportMessage(
                    messageId = "loopback-${System.currentTimeMillis()}",
                    fromPeer = toPeer,
                    body = buildAutoReply(body)
                )
            )
        }
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) = Unit

    private fun buildAutoReply(body: String): String {
        val compact = body.trim().take(120)
        return "alpha-reply: $compact"
    }
}
