package com.aufait.alpha.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
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
    private val seenInboundMessages = LinkedHashMap<String, Long>(256, 0.75f, true)
    private val seenInboundReceipts = LinkedHashMap<String, Long>(512, 0.75f, true)
    private val seenEventsLock = Any()
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
                            if (!markInboundMessageSeen(inbound)) return@collect
                            val plaintextBody = e2eCipherEngine.decryptFromPeer(inbound.fromPeer, inbound.body)
                            messageRepository.append(
                                direction = MessageDirection.INBOUND,
                                author = inbound.fromPeer,
                                body = plaintextBody,
                                timestampMs = inbound.receivedAtMs,
                                id = "in-${inbound.messageId}",
                                transportChannel = inbound.channel
                            )
                            val inForeground = readReceiptMutex.withLock { isConversationInForeground }
                            if (inForeground) {
                                incomingMessageSoundPlayer.play()
                            } else {
                                backgroundMessageNotifier.notifyIncomingMessage(
                                    fromPeer = inbound.fromPeer,
                                    body = plaintextBody
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
                            if (!markInboundReceiptSeen(receipt)) return@collect
                            messageRepository.markReceipt(
                                messageId = receipt.messageId,
                                receiptKind = receipt.kind,
                                atMs = receipt.receivedAtMs,
                                channel = receipt.channel
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
            id = outboundMessageId,
            transportChannel = null
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

    private fun markInboundMessageSeen(inbound: InboundTransportMessage): Boolean {
        val sender = inbound.fromNodeId.ifBlank { inbound.fromPeer }
        val key = "msg|$sender|${inbound.messageId}"
        return markSeen(seenInboundMessages, key, inbound.receivedAtMs)
    }

    private fun markInboundReceiptSeen(receipt: InboundReceipt): Boolean {
        val sender = receipt.fromNodeId.ifBlank { receipt.fromPeer }
        val key = "receipt|$sender|${receipt.messageId}|${receipt.kind.name}"
        return markSeen(seenInboundReceipts, key, receipt.receivedAtMs)
    }

    private fun markSeen(cache: LinkedHashMap<String, Long>, key: String, timestampMs: Long): Boolean {
        synchronized(seenEventsLock) {
            pruneSeenCache(cache, timestampMs)
            val existing = cache[key]
            if (existing != null && (timestampMs <= existing + DUPLICATE_WINDOW_MS)) {
                return false
            }
            cache[key] = timestampMs
            while (cache.size > MAX_SEEN_EVENT_KEYS) {
                val oldestKey = cache.entries.firstOrNull()?.key ?: break
                cache.remove(oldestKey)
            }
            return true
        }
    }

    private fun pruneSeenCache(cache: LinkedHashMap<String, Long>, nowMs: Long) {
        val cutoff = nowMs - SEEN_EVENT_TTL_MS
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value < cutoff) it.remove()
        }
    }

    companion object {
        private const val MAX_SEEN_EVENT_KEYS = 2_000
        private const val SEEN_EVENT_TTL_MS = 10 * 60 * 1000L
        private const val DUPLICATE_WINDOW_MS = 30_000L
    }
}
