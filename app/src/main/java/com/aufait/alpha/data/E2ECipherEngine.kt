package com.aufait.alpha.data

import org.json.JSONObject

interface E2ECipherEngine {
    fun encryptForPeer(peerRef: String, plaintext: String): String
    fun decryptFromPeer(peerRef: String, ciphertextEnvelope: String): String
    val modeLabel: String
}

class PlaintextEnvelopeCipher : E2ECipherEngine {
    override val modeLabel: String = "alpha-plaintext-envelope"

    override fun encryptForPeer(peerRef: String, plaintext: String): String {
        val payload = JSONObject()
            .put("v", 1)
            .put("mode", modeLabel)
            .put("peer", peerRef.take(64))
            .put("body", plaintext)
        return payload.toString()
    }

    override fun decryptFromPeer(peerRef: String, ciphertextEnvelope: String): String {
        val json = runCatching { JSONObject(ciphertextEnvelope) }.getOrNull() ?: return ciphertextEnvelope
        return json.optString("body", ciphertextEnvelope)
    }
}
