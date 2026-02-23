package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashSet
import java.util.UUID

class EncryptedMessageRepository(
    context: Context,
    private val cipher: LocalCipher
) {
    private val prefs = context.getSharedPreferences("aufait_messages", Context.MODE_PRIVATE)
    private val _messages = MutableStateFlow(loadAndDecrypt())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun append(
        direction: MessageDirection,
        author: String,
        body: String,
        timestampMs: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
        transportChannel: MessageTransportChannel? = null
    ): String {
        val now = System.currentTimeMillis()
        val current = normalizeAndPrune(loadStored(), now).toMutableList()
        val safeAuthor = author.trim().ifBlank { "peer" }.take(MAX_AUTHOR_CHARS)
        val safeBody = body.take(MAX_BODY_CHARS)
        val safeTimestamp = timestampMs.coerceIn(now - MAX_CLOCK_SKEW_MS, now + MAX_CLOCK_SKEW_MS)
        val existingIndex = current.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            val merged = existing.copy(
                transportChannel = mergeChannel(existing.transportChannel, transportChannel),
                deliveredChannel = existing.deliveredChannel,
                readChannel = existing.readChannel
            )
            if (merged != existing) {
                current[existingIndex] = merged
                val pruned = normalizeAndPrune(current, now)
                saveStored(pruned)
                _messages.value = pruned.map(::decryptEnvelope)
            }
            return existing.id
        }
        val finalId = id
        val (iv, data) = cipher.encrypt(safeBody)
        current += StoredMessageEnvelope(
            id = finalId,
            direction = direction,
            author = safeAuthor,
            timestampMs = safeTimestamp,
            ivBase64 = iv,
            cipherBase64 = data,
            transportChannel = transportChannel,
            deliveredAtMs = null,
            deliveredChannel = null,
            readAtMs = null
        )
        val pruned = normalizeAndPrune(current, now)
        saveStored(pruned)
        _messages.value = pruned.map(::decryptEnvelope)
        return finalId
    }

    fun markReceipt(
        messageId: String,
        receiptKind: ReceiptKind,
        atMs: Long = System.currentTimeMillis(),
        channel: MessageTransportChannel? = null
    ) {
        val now = System.currentTimeMillis()
        val current = normalizeAndPrune(loadStored(), now).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val msg = current[index]
        if (msg.direction != MessageDirection.OUTBOUND) return
        val safeAtMs = atMs.coerceIn(msg.timestampMs, now + MAX_CLOCK_SKEW_MS)

        val updated = when (receiptKind) {
            ReceiptKind.DELIVERED -> {
                val deliveredAtMs = msg.deliveredAtMs ?: safeAtMs
                val deliveredChannel = mergeChannel(msg.deliveredChannel, channel)
                if (deliveredAtMs == msg.deliveredAtMs && deliveredChannel == msg.deliveredChannel) return
                msg.copy(
                    deliveredAtMs = deliveredAtMs,
                    deliveredChannel = deliveredChannel
                )
            }
            ReceiptKind.READ -> {
                val readAtMs = msg.readAtMs ?: safeAtMs
                val deliveredAtMs = msg.deliveredAtMs ?: safeAtMs
                val deliveredChannel = mergeChannel(msg.deliveredChannel, channel)
                val readChannel = mergeChannel(msg.readChannel, channel)
                if (
                    readAtMs == msg.readAtMs &&
                    deliveredAtMs == msg.deliveredAtMs &&
                    deliveredChannel == msg.deliveredChannel &&
                    readChannel == msg.readChannel
                ) return
                msg.copy(
                    deliveredAtMs = deliveredAtMs,
                    deliveredChannel = deliveredChannel,
                    readAtMs = readAtMs,
                    readChannel = readChannel
                )
            }
        }
        current[index] = updated
        val pruned = normalizeAndPrune(current, now)
        saveStored(pruned)
        _messages.value = pruned.map(::decryptEnvelope)
    }

    private fun loadAndDecrypt(): List<ChatMessage> {
        val pruned = normalizeAndPrune(loadStored(), System.currentTimeMillis())
        return pruned.map(::decryptEnvelope)
    }

    private fun decryptEnvelope(env: StoredMessageEnvelope): ChatMessage {
        val body = runCatching { cipher.decrypt(env.ivBase64, env.cipherBase64) }
            .getOrElse { "[message illisible]" }
        return ChatMessage(
            id = env.id,
            direction = env.direction,
            author = env.author,
            body = body,
            timestampMs = env.timestampMs,
            transportChannel = env.transportChannel,
            deliveredAtMs = env.deliveredAtMs,
            deliveredChannel = env.deliveredChannel,
            readAtMs = env.readAtMs,
            readChannel = env.readChannel
        )
    }

    private fun loadStored(): List<StoredMessageEnvelope> {
        val primary = prefs.getString(KEY_MESSAGES_JSON, null)
        val backup = prefs.getString(KEY_MESSAGES_JSON_BACKUP, null)
        parseStored(primary)
            ?.takeIf { it.isNotEmpty() || primary != null }
            ?.let { return it }
        parseStored(backup)?.let { recovered ->
            prefs.edit().putString(KEY_MESSAGES_JSON, backup).apply()
            return recovered
        }
        return emptyList()
    }

    private fun parseStored(raw: String?): List<StoredMessageEnvelope>? {
        raw ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    parseEnvelope(o)?.let(::add)
                }
            }
        }.getOrNull()
    }

    private fun parseEnvelope(o: JSONObject): StoredMessageEnvelope? {
        val id = o.optString("id").trim()
        val direction = o.optString("direction")
            .takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { MessageDirection.valueOf(raw) }.getOrNull() }
            ?: return null
        val author = o.optString("author").trim().ifBlank { "peer" }.take(MAX_AUTHOR_CHARS)
        val timestampMs = o.optLong("timestampMs", 0L)
        val ivBase64 = o.optString("ivBase64").trim()
        val cipherBase64 = o.optString("cipherBase64").trim()
        if (id.isBlank() || ivBase64.isBlank() || cipherBase64.isBlank()) return null
        if (timestampMs <= 0L) return null
        val deliveredAtMs = o.optNullableLong("deliveredAtMs")
        val transportChannel = o.optChannel("transportChannel")
        val deliveredChannel = o.optChannel("deliveredChannel")
        val readAtMs = o.optNullableLong("readAtMs")
        val readChannel = o.optChannel("readChannel")
        return StoredMessageEnvelope(
            id = id.take(64),
            direction = direction,
            author = author,
            timestampMs = timestampMs,
            ivBase64 = ivBase64.take(MAX_CIPHER_B64_CHARS),
            cipherBase64 = cipherBase64.take(MAX_CIPHER_B64_CHARS),
            transportChannel = transportChannel,
            deliveredAtMs = deliveredAtMs,
            deliveredChannel = deliveredChannel,
            readAtMs = readAtMs,
            readChannel = readChannel
        )
    }

    private fun normalizeAndPrune(
        messages: List<StoredMessageEnvelope>,
        nowMs: Long
    ): List<StoredMessageEnvelope> {
        val minTimestamp = nowMs - MESSAGE_RETENTION_MS
        val seenIds = LinkedHashSet<String>(messages.size)
        return messages
            .asSequence()
            .filter { it.id.isNotBlank() && it.id.length <= 64 }
            .filter { it.timestampMs in minTimestamp..(nowMs + MAX_CLOCK_SKEW_MS) }
            .filter { seenIds.add(it.id) }
            .map { env ->
                val delivered = env.deliveredAtMs?.takeIf { it in env.timestampMs..(nowMs + MAX_CLOCK_SKEW_MS) }
                val read = env.readAtMs?.takeIf { delivered == null || it >= delivered }
                    ?.takeIf { it in env.timestampMs..(nowMs + MAX_CLOCK_SKEW_MS) }
                env.copy(
                    author = env.author.trim().ifBlank { "peer" }.take(MAX_AUTHOR_CHARS),
                    transportChannel = env.transportChannel,
                    deliveredAtMs = delivered,
                    deliveredChannel = env.deliveredChannel,
                    readAtMs = read,
                    readChannel = env.readChannel
                )
            }
            .sortedBy { it.timestampMs }
            .toList()
            .takeLast(MAX_STORED_MESSAGES)
    }

    private fun saveStored(messages: List<StoredMessageEnvelope>) {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("direction", msg.direction.name)
                    put("author", msg.author)
                    put("timestampMs", msg.timestampMs)
                    put("ivBase64", msg.ivBase64)
                    put("cipherBase64", msg.cipherBase64)
                    msg.transportChannel?.let { put("transportChannel", it.name) }
                    msg.deliveredAtMs?.let { put("deliveredAtMs", it) }
                    msg.deliveredChannel?.let { put("deliveredChannel", it.name) }
                    msg.readAtMs?.let { put("readAtMs", it) }
                    msg.readChannel?.let { put("readChannel", it.name) }
                }
            )
        }
        val serialized = arr.toString()
        val previous = prefs.getString(KEY_MESSAGES_JSON, null)
        prefs.edit().apply {
            if (!previous.isNullOrBlank() && previous != serialized) {
                putString(KEY_MESSAGES_JSON_BACKUP, previous)
            }
            putString(KEY_MESSAGES_JSON, serialized)
        }.apply()
    }

    private fun mergeChannel(
        existing: MessageTransportChannel?,
        incoming: MessageTransportChannel?
    ): MessageTransportChannel? {
        if (incoming == null) return existing
        if (existing == null) return incoming
        if (existing == incoming) return existing
        // Prioritise the strongest/most specific path when multiple channels are observed.
        val rank = mapOf(
            MessageTransportChannel.LOCAL to 0,
            MessageTransportChannel.RELAY to 1,
            MessageTransportChannel.WIFI to 2,
            MessageTransportChannel.BLUETOOTH to 3,
            MessageTransportChannel.TOR to 4
        )
        return if ((rank[incoming] ?: 0) >= (rank[existing] ?: 0)) incoming else existing
    }

    companion object {
        private const val KEY_MESSAGES_JSON = "messages_json"
        private const val KEY_MESSAGES_JSON_BACKUP = "messages_json_backup"
        private const val MAX_STORED_MESSAGES = 2_000
        private const val MESSAGE_RETENTION_MS = 90L * 24L * 60L * 60L * 1000L
        private const val MAX_CLOCK_SKEW_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MAX_AUTHOR_CHARS = 64
        private const val MAX_BODY_CHARS = 16_000
        private const val MAX_CIPHER_B64_CHARS = 64_000
    }
}

private fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.optChannel(key: String): MessageTransportChannel? =
    optString(key).takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { MessageTransportChannel.valueOf(raw) }.getOrNull()
    }
