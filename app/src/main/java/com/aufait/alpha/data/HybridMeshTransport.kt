package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HybridMeshTransport(
    private val scope: CoroutineScope,
    private val lan: MeshTransport,
    private val relay: MeshTransport
) : MeshTransport {
    private val _inboundMessages = MutableSharedFlow<InboundTransportMessage>(extraBufferCapacity = 32)
    private val _inboundReceipts = MutableSharedFlow<InboundReceipt>(extraBufferCapacity = 32)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    override val inboundMessages: SharedFlow<InboundTransportMessage> = _inboundMessages
    override val inboundReceipts: SharedFlow<InboundReceipt> = _inboundReceipts
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers

    private var started = false

    override suspend fun start(localAlias: String, localNodeId: String) {
        if (started) return
        started = true
        lan.start(localAlias, localNodeId)
        relay.start(localAlias, localNodeId)

        scope.launch { lan.inboundMessages.collect { _inboundMessages.emit(it) } }
        scope.launch { relay.inboundMessages.collect { _inboundMessages.emit(it) } }
        scope.launch { lan.inboundReceipts.collect { _inboundReceipts.emit(it) } }
        scope.launch { relay.inboundReceipts.collect { _inboundReceipts.emit(it) } }
        scope.launch {
            lan.peers.collect { lanPeers ->
                _peers.value = lanPeers
            }
        }
    }

    override suspend fun updateLocalAlias(localAlias: String) {
        lan.updateLocalAlias(localAlias)
        relay.updateLocalAlias(localAlias)
    }

    override suspend fun sendMessage(toPeer: String, messageId: String, body: String) {
        lan.sendMessage(toPeer, messageId, body)
        relay.sendMessage(toPeer, messageId, body)
    }

    override suspend fun sendReceipt(toPeer: String, messageId: String, kind: ReceiptKind) {
        lan.sendReceipt(toPeer, messageId, kind)
        relay.sendReceipt(toPeer, messageId, kind)
    }
}
