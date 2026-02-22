package com.aufait.alpha.data

data class UserIdentity(
    val id: String,
    val alias: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

enum class MessageDirection {
    OUTBOUND,
    INBOUND
}

enum class ReceiptKind {
    DELIVERED,
    READ
}

data class ChatMessage(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val body: String,
    val timestampMs: Long,
    val deliveredAtMs: Long? = null,
    val readAtMs: Long? = null
)

data class ContactRecord(
    val userId: String,
    val alias: String,
    val publicKeyBase64: String,
    val fingerprint: String,
    val createdAtMs: Long
)

data class StoredMessageEnvelope(
    val id: String,
    val direction: MessageDirection,
    val author: String,
    val timestampMs: Long,
    val ivBase64: String,
    val cipherBase64: String,
    val deliveredAtMs: Long? = null,
    val readAtMs: Long? = null
)
