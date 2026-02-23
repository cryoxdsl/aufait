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
        id: String = UUID.randomUUID().toString()
    ): String {
        val now = System.currentTimeMillis()
        val current = normalizeAndPrune(loadStored(), now).toMutableList()
        val safeAuthor = author.trim().ifBlank { "peer" }.take(MAX_AUTHOR_CHARS)
        val safeBody = body.take(MAX_BODY_CHARS)
        val safeTimestamp = timestampMs.coerceIn(now - MAX_CLOCK_SKEW_MS, now + MAX_CLOCK_SKEW_MS)
        val finalId = if (current.any { it.id == id }) UUID.randomUUID().toString() else id
        val (iv, data) = cipher.encrypt(safeBody)
        current += StoredMessageEnvelope(
            id = finalId,
            direction = direction,
            author = safeAuthor,
            timestampMs = safeTimestamp,
            ivBase64 = iv,
            cipherBase64 = data,
            deliveredAtMs = null,
            readAtMs = null
        )
        val pruned = normalizeAndPrune(current, now)
        saveStored(pruned)
        _messages.value = pruned.map(::decryptEnvelope)
        return finalId
    }

    fun markReceipt(messageId: String, receiptKind: ReceiptKind, atMs: Long = System.currentTimeMillis()) {
        val now = System.currentTimeMillis()
        val current = normalizeAndPrune(loadStored(), now).toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val msg = current[index]
        if (msg.direction != MessageDirection.OUTBOUND) return
        val safeAtMs = atMs.coerceIn(msg.timestampMs, now + MAX_CLOCK_SKEW_MS)

        val updated = when (receiptKind) {
            ReceiptKind.DELIVERED -> {
                if (msg.deliveredAtMs != null) return
                msg.copy(deliveredAtMs = safeAtMs)
            }
            ReceiptKind.READ -> {
                if (msg.readAtMs != null) return
                msg.copy(
                    deliveredAtMs = msg.deliveredAtMs ?: safeAtMs,
                    readAtMs = safeAtMs
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
            deliveredAtMs = env.deliveredAtMs,
            readAtMs = env.readAtMs
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
        val readAtMs = o.optNullableLong("readAtMs")
        return StoredMessageEnvelope(
            id = id.take(64),
            direction = direction,
            author = author,
            timestampMs = timestampMs,
            ivBase64 = ivBase64.take(MAX_CIPHER_B64_CHARS),
            cipherBase64 = cipherBase64.take(MAX_CIPHER_B64_CHARS),
            deliveredAtMs = deliveredAtMs,
            readAtMs = readAtMs
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
                    deliveredAtMs = delivered,
                    readAtMs = read
                )
            }
            .sortedBy { it.timestampMs }
            .takeLast(MAX_STORED_MESSAGES)
            .toList()
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
                    msg.deliveredAtMs?.let { put("deliveredAtMs", it) }
                    msg.readAtMs?.let { put("readAtMs", it) }
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
