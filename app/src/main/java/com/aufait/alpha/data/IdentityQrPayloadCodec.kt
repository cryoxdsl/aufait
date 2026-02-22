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

    fun encode(identity: UserIdentity): String {
        val json = JSONObject()
            .put("v", 1)
            .put("alias", identity.alias)
            .put("id", identity.id)
            .put("pub", identity.publicKeyBase64)
            .put("fp", identity.fingerprint)
        return PREFIX + json.toString()
    }

    fun decode(payload: String): ContactIdentityPayload? {
        if (!payload.startsWith(PREFIX)) return null
        val json = runCatching { JSONObject(payload.removePrefix(PREFIX)) }.getOrNull() ?: return null
        return ContactIdentityPayload(
            version = json.optInt("v", 1),
            alias = json.optString("alias"),
            userId = json.optString("id"),
            publicKeyBase64 = json.optString("pub"),
            fingerprint = json.optString("fp")
        ).takeIf { it.alias.isNotBlank() && it.userId.isNotBlank() && it.publicKeyBase64.isNotBlank() }
    }
}
