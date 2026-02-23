package com.aufait.alpha.data

import org.json.JSONObject

data class ContactIdentityPayload(
    val version: Int,
    val alias: String,
    val userId: String,
    val publicKeyBase64: String,
    val fingerprint: String
)

object IdentityQrPayloadCodec {
    private const val PREFIX = "aufait-contact:"
    private const val MAX_PAYLOAD_LEN = 8_192

    fun encode(identity: UserIdentity): String {
        val json = JSONObject()
            .put("v", 1)
            .put("alias", identity.alias.trim().take(64))
            .put("id", identity.id.take(128))
            .put("pub", identity.publicKeyBase64.take(4096))
            .put("fp", identity.fingerprint.take(128))
        return PREFIX + json.toString()
    }

    fun decode(payload: String): ContactIdentityPayload? {
        if (payload.length > MAX_PAYLOAD_LEN) return null
        if (!payload.startsWith(PREFIX)) return null
        val json = runCatching { JSONObject(payload.removePrefix(PREFIX)) }.getOrNull() ?: return null
        return ContactIdentityPayload(
            version = json.optInt("v", 1),
            alias = json.optString("alias").trim().take(64),
            userId = json.optString("id").trim().take(128),
            publicKeyBase64 = json.optString("pub").trim().take(4096),
            fingerprint = json.optString("fp").trim().take(128)
        ).takeIf {
            it.alias.isNotBlank() &&
                it.userId.length >= 8 &&
                it.publicKeyBase64.length >= 16
        }
    }
}
