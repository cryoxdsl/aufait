package com.aufait.alpha.data

import android.content.Context
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.MessageDigest

class IdentityRepository(context: Context) {
    private val prefs = context.getSharedPreferences("aufait_identity", Context.MODE_PRIVATE)

    fun getOrCreateIdentity(): UserIdentity {
        val pub = prefs.getString(KEY_PUBLIC, null)
        val priv = prefs.getString(KEY_PRIVATE, null)

        val publicKeyBytes = if (pub != null && priv != null) {
            Base64.decode(pub, Base64.NO_WRAP)
        } else {
            val generated = generateAndStore()
            generated
        }

        val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        return UserIdentity(
            id = sha256Hex(publicKeyBytes),
            publicKeyBase64 = publicKeyBase64,
            fingerprint = fingerprint(publicKeyBytes)
        )
    }

    private fun generateAndStore(): ByteArray {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()
        val publicBytes = pair.public.encoded
        val privateBytes = pair.private.encoded

        prefs.edit()
            .putString(KEY_PUBLIC, Base64.encodeToString(publicBytes, Base64.NO_WRAP))
            .putString(KEY_PRIVATE, Base64.encodeToString(privateBytes, Base64.NO_WRAP))
            .apply()
        return publicBytes
    }

    private fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(12).joinToString(":") { "%02x".format(it) }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_PUBLIC = "public_key_b64"
        private const val KEY_PRIVATE = "private_key_b64"
    }
}
