package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChatService(
    private val identityRepository: IdentityRepository,
    private val messageRepository: EncryptedMessageRepository,
    private val transport: MeshTransport,
    private val e2eCipherEngine: E2ECipherEngine,
    private val incomingMessageSoundPlayer: IncomingMessageSoundPlayer,
    private val backgroundMessageNotifier: BackgroundMessageNotifier
) {
    private val startMutex = Mutex()
    private val readReceiptMutex = Mutex()
    private var startedJob: Job? = null
    private var isConversationInForeground: Boolean = false
    private val pendingReadReceipts = mutableListOf<PendingReadReceipt>()
    val peers: StateFlow<List<DiscoveredPeer>> = transport.peers
    val cryptoModeLabel: String get() = e2eCipherEngine.modeLabel

    suspend fun start() {
        startMutex.withLock {
            if (startedJob != null) return
            val identity = identityRepository.getOrCreateIdentity()
            transport.start(
                localAlias = identity.alias,
                localNodeId = identity.id
            )
            startedJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).let { scope ->
                scope.launch(start = CoroutineStart.DEFAULT) {
                    launch {
                        transport.inboundMessages.collect { inbound ->
                            messageRepository.append(
                                direction = MessageDirection.INBOUND,
                                author = inbound.fromPeer,
                                body = e2eCipherEngine.decryptFromPeer(inbound.fromPeer, inbound.body),
                                timestampMs = inbound.receivedAtMs,
                                id = "in-${inbound.messageId}"
                            )
                            val inForeground = readReceiptMutex.withLock { isConversationInForeground }
                            if (inForeground) {
                                incomingMessageSoundPlayer.play()
                            } else {
                                backgroundMessageNotifier.notifyIncomingMessage(
                                    fromPeer = inbound.fromPeer,
                                    body = inbound.body
                                )
                            }
                            transport.sendReceipt(
                                toPeer = inbound.fromNodeId.ifBlank { inbound.fromPeer },
                                messageId = inbound.messageId,
                                kind = ReceiptKind.DELIVERED
                            )
                            sendReadReceiptNowOrQueue(
                                toPeer = inbound.fromNodeId.ifBlank { inbound.fromPeer },
                                messageId = inbound.messageId
                            )
                        }
                    }
                    launch {
                        transport.inboundReceipts.collect { receipt ->
                            messageRepository.markReceipt(
                                messageId = receipt.messageId,
                                receiptKind = receipt.kind,
                                atMs = receipt.receivedAtMs
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun sendToPeer(peerAlias: String, body: String) {
        val me = identityRepository.getOrCreateIdentity()
        val outboundMessageId = UUID.randomUUID().toString()
        val encryptedBody = e2eCipherEngine.encryptForPeer(peerAlias, body)
        messageRepository.append(
            direction = MessageDirection.OUTBOUND,
            author = "me:${me.id.take(6)}",
            body = body,
            id = outboundMessageId
        )
        transport.sendMessage(peerAlias, outboundMessageId, encryptedBody)
    }

    suspend fun updateLocalAlias(alias: String) {
        identityRepository.setAlias(alias)
        val identity = identityRepository.getOrCreateIdentity()
        transport.updateLocalAlias(identity.alias)
    }

    suspend fun setConversationForeground(inForeground: Boolean) {
        val toFlush = readReceiptMutex.withLock {
            isConversationInForeground = inForeground
            if (!inForeground || pendingReadReceipts.isEmpty()) {
                emptyList()
            } else {
                pendingReadReceipts.toList().also { pendingReadReceipts.clear() }
            }
        }

        toFlush.forEach { pending ->
            transport.sendReceipt(
                toPeer = pending.toPeer,
                messageId = pending.messageId,
                kind = ReceiptKind.READ
            )
        }
    }

    private suspend fun sendReadReceiptNowOrQueue(toPeer: String, messageId: String) {
        val shouldSendNow = readReceiptMutex.withLock {
            if (isConversationInForeground) {
                true
            } else {
                if (pendingReadReceipts.none { it.messageId == messageId }) {
                    pendingReadReceipts += PendingReadReceipt(toPeer = toPeer, messageId = messageId)
                }
                false
            }
        }
        if (shouldSendNow) {
            transport.sendReceipt(
                toPeer = toPeer,
                messageId = messageId,
                kind = ReceiptKind.READ
            )
        }
    }

    private data class PendingReadReceipt(
        val toPeer: String,
        val messageId: String
    )
}
