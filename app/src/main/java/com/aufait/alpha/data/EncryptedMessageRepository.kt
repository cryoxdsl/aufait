package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
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
        val current = loadStored().toMutableList()
        val (iv, data) = cipher.encrypt(body)
        current += StoredMessageEnvelope(
            id = id,
            direction = direction,
            author = author,
            timestampMs = timestampMs,
            ivBase64 = iv,
            cipherBase64 = data,
            deliveredAtMs = null,
            readAtMs = null
        )
        saveStored(current)
        _messages.value = current.map(::decryptEnvelope)
        return id
    }

    fun markReceipt(messageId: String, receiptKind: ReceiptKind, atMs: Long = System.currentTimeMillis()) {
        val current = loadStored().toMutableList()
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val msg = current[index]
        if (msg.direction != MessageDirection.OUTBOUND) return

        val updated = when (receiptKind) {
            ReceiptKind.DELIVERED -> {
                if (msg.deliveredAtMs != null) return
                msg.copy(deliveredAtMs = atMs)
            }
            ReceiptKind.READ -> {
                if (msg.readAtMs != null) return
                msg.copy(
                    deliveredAtMs = msg.deliveredAtMs ?: atMs,
                    readAtMs = atMs
                )
            }
        }
        current[index] = updated
        saveStored(current)
        _messages.value = current.map(::decryptEnvelope)
    }

    private fun loadAndDecrypt(): List<ChatMessage> = loadStored().map(::decryptEnvelope)

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
        val raw = prefs.getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        StoredMessageEnvelope(
                            id = o.getString("id"),
                            direction = MessageDirection.valueOf(o.getString("direction")),
                            author = o.getString("author"),
                            timestampMs = o.getLong("timestampMs"),
                            ivBase64 = o.getString("ivBase64"),
                            cipherBase64 = o.getString("cipherBase64"),
                            deliveredAtMs = o.optNullableLong("deliveredAtMs"),
                            readAtMs = o.optNullableLong("readAtMs")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
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
        prefs.edit().putString(KEY_MESSAGES_JSON, arr.toString()).apply()
    }

    companion object {
        private const val KEY_MESSAGES_JSON = "messages_json"
    }
}

private fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
