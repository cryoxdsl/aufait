package com.aufait.alpha.data

data class UserIdentity(
    val id: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

enum class MessageDirection {
    OUTBOUND,
    INBOUND
}

data class ChatMessage(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val body: String,
    val timestampMs: Long
)

data class StoredMessageEnvelope(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val timestampMs: Long,
    val ivBase64: String,
    val cipherBase64: String
)
