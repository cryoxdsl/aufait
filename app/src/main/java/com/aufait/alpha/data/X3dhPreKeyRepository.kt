package com.aufait.alpha.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class SignedPreKeyRecord(
    val keyId: Int,
    val publicKeyBase64: String,
    val signatureBase64: String
)

data class OneTimePreKeyRecord(
    val keyId: Int,
    val publicKeyBase64: String,
    val consumed: Boolean
)

data class LocalX3dhBundleSummary(
    val signedPreKey: SignedPreKeyRecord,
    val oneTimePreKeyCount: Int
)

class X3dhPreKeyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("aufait_x3dh_prekeys", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun getOrCreateBundleSummary(): LocalX3dhBundleSummary {
        if (!prefs.contains(KEY_SIGNED_PREKEY_JSON)) {
            bootstrap()
        }
        val signed = loadSignedPreKey() ?: run {
            bootstrap()
            loadSignedPreKey()!!
        }
        val oneTime = loadOneTimePreKeys()
        return LocalX3dhBundleSummary(
            signedPreKey = signed,
            oneTimePreKeyCount = oneTime.count { !it.consumed }
        )
    }

    private fun bootstrap() {
        val signed = SignedPreKeyRecord(
            keyId = 1,
            publicKeyBase64 = randomKeyMaterial(),
            signatureBase64 = randomSignatureMaterial()
        )
        val oneTime = (1..20).map { id ->
            OneTimePreKeyRecord(
                keyId = id,
                publicKeyBase64 = randomKeyMaterial(),
                consumed = false
            )
        }
        prefs.edit()
            .putString(KEY_SIGNED_PREKEY_JSON, JSONObject().apply {
                put("keyId", signed.keyId)
                put("publicKeyBase64", signed.publicKeyBase64)
                put("signatureBase64", signed.signatureBase64)
            }.toString())
            .putString(KEY_ONE_TIME_PREKEYS_JSON, JSONArray().apply {
                oneTime.forEach { key ->
                    put(JSONObject().apply {
                        put("keyId", key.keyId)
                        put("publicKeyBase64", key.publicKeyBase64)
                        put("consumed", key.consumed)
                    })
                }
            }.toString())
            .apply()
    }

    private fun loadSignedPreKey(): SignedPreKeyRecord? {
        val raw = prefs.getString(KEY_SIGNED_PREKEY_JSON, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            SignedPreKeyRecord(
                keyId = o.getInt("keyId"),
                publicKeyBase64 = o.getString("publicKeyBase64"),
                signatureBase64 = o.getString("signatureBase64")
            )
        }.getOrNull()
    }

    private fun loadOneTimePreKeys(): List<OneTimePreKeyRecord> {
        val raw = prefs.getString(KEY_ONE_TIME_PREKEYS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        OneTimePreKeyRecord(
                            keyId = o.getInt("keyId"),
                            publicKeyBase64 = o.getString("publicKeyBase64"),
                            consumed = o.optBoolean("consumed", false)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun randomKeyMaterial(): String = ByteArray(32).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP) }

    private fun randomSignatureMaterial(): String = ByteArray(64).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP) }

    companion object {
        private const val KEY_SIGNED_PREKEY_JSON = "signed_prekey_json"
        private const val KEY_ONE_TIME_PREKEYS_JSON = "one_time_prekeys_json"
    }
}
