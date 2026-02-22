package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatService(
    private val identityRepository: IdentityRepository,
    private val messageRepository: EncryptedMessageRepository,
    private val transport: MeshTransport
) {
    private val startMutex = Mutex()
    private var startedJob: Job? = null

    suspend fun start() {
        startMutex.withLock {
            if (startedJob != null) return
            startedJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).let { scope ->
                scope.launch(start = CoroutineStart.DEFAULT) {
                    transport.inboundMessages.collectLatest { inbound ->
                        messageRepository.append(
                            direction = MessageDirection.INBOUND,
                            author = inbound.fromPeer,
                            body = inbound.body,
                            timestampMs = inbound.receivedAtMs
                        )
                    }
                }
            }
        }
    }

    suspend fun sendToPeer(peerAlias: String, body: String) {
        val me = identityRepository.getOrCreateIdentity()
        messageRepository.append(
            direction = MessageDirection.OUTBOUND,
            author = "me:${me.id.take(6)}",
            body = body
        )
        transport.send(peerAlias, body)
    }
}
