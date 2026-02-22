package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class InboundTransportMessage(
    val fromPeer: String,
    val body: String,
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
    val peers: StateFlow<List<DiscoveredPeer>>
    suspend fun start(localAlias: String, localNodeId: String)
    suspend fun send(toPeer: String, body: String)
}

class LoopbackMeshTransport(
    private val scope: CoroutineScope
) : MeshTransport {
    private val _inbound = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 16)
    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inbound
    override val peers: StateFlow<List<DiscoveredPeer>> = MutableStateFlow(emptyList())

    override suspend fun start(localAlias: String, localNodeId: String) = Unit

    override suspend fun send(toPeer: String, body: String) {
        scope.launch {
            delay(650)
            _inbound.emit(
                InboundTransportMessage(
                    fromPeer = toPeer,
                    body = buildAutoReply(body)
                )
            )
        }
    }

    private fun buildAutoReply(body: String): String {
        val compact = body.trim().take(120)
        return "alpha-reply: $compact"
    }
}
