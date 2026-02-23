package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

class ContactRepository(context: Context) {
    private val prefs = context.getSharedPreferences("aufait_contacts", Context.MODE_PRIVATE)
    private val _contacts = MutableStateFlow(loadContacts())
    val contacts: StateFlow<List<ContactRecord>> = _contacts

    fun importFromQrPayload(payload: String): ContactRecord? {
        val decoded = IdentityQrPayloadCodec.decode(payload) ?: return null
        if (!isSafeContactPayload(decoded)) return null
        val contact = ContactRecord(
            userId = decoded.userId.trim().take(128),
            alias = decoded.alias.trim().ifBlank { "contact-${decoded.userId.take(6)}" }.take(32),
            publicKeyBase64 = decoded.publicKeyBase64.trim().take(4096),
            fingerprint = decoded.fingerprint.trim().take(128),
            createdAtMs = System.currentTimeMillis()
        )
        upsert(contact)
        return contact
    }

    private fun upsert(contact: ContactRecord) {
        val current = normalizeAndPruneContacts(loadContacts()).toMutableList()
        val idx = current.indexOfFirst { it.userId == contact.userId }
        if (idx >= 0) current[idx] = contact.copy(createdAtMs = current[idx].createdAtMs)
        else current += contact
        val normalized = normalizeAndPruneContacts(current)
        saveContacts(normalized)
        _contacts.value = normalized.sortedBy { it.alias.lowercase() }
    }

    private fun isSafeContactPayload(payload: ContactIdentityPayload): Boolean {
        if (payload.userId.length !in 8..128) return false
        if (payload.alias.length !in 1..64) return false
        if (payload.publicKeyBase64.length !in 16..4096) return false
        if (payload.fingerprint.length > 128) return false
        return true
    }

    private fun loadContacts(): List<ContactRecord> {
        val primary = prefs.getString(KEY_CONTACTS_JSON, null)
        val backup = prefs.getString(KEY_CONTACTS_JSON_BACKUP, null)
        parseContacts(primary)
            ?.takeIf { it.isNotEmpty() || primary != null }
            ?.let { return normalizeAndPruneContacts(it) }
        parseContacts(backup)?.let { recovered ->
            prefs.edit().putString(KEY_CONTACTS_JSON, backup).apply()
            return normalizeAndPruneContacts(recovered)
        }
        return emptyList()
    }

    private fun parseContacts(raw: String?): List<ContactRecord>? {
        raw ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    parseContact(o)?.let(::add)
                }
            }
        }.getOrNull()
    }

    private fun parseContact(o: JSONObject): ContactRecord? {
        val candidate = ContactRecord(
            userId = o.optString("userId").trim().take(128),
            alias = o.optString("alias").trim().take(64),
            publicKeyBase64 = o.optString("publicKeyBase64").trim().take(4096),
            fingerprint = o.optString("fingerprint").trim().take(128),
            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()).coerceAtLeast(0L)
        )
        val payload = ContactIdentityPayload(
            userId = candidate.userId,
            alias = candidate.alias,
            publicKeyBase64 = candidate.publicKeyBase64,
            fingerprint = candidate.fingerprint
        )
        if (!isSafeContactPayload(payload)) return null
        return candidate.copy(alias = candidate.alias.ifBlank { "contact-${candidate.userId.take(6)}" }.take(32))
    }

    private fun normalizeAndPruneContacts(input: List<ContactRecord>): List<ContactRecord> {
        val deduped = LinkedHashMap<String, ContactRecord>(input.size)
        input.forEach { raw ->
            val normalized = raw.copy(
                userId = raw.userId.trim().take(128),
                alias = raw.alias.trim().ifBlank { "contact-${raw.userId.take(6)}" }.take(32),
                publicKeyBase64 = raw.publicKeyBase64.trim().take(4096),
                fingerprint = raw.fingerprint.trim().take(128),
                createdAtMs = raw.createdAtMs.coerceAtLeast(0L)
            )
            val payload = ContactIdentityPayload(
                userId = normalized.userId,
                alias = normalized.alias,
                publicKeyBase64 = normalized.publicKeyBase64,
                fingerprint = normalized.fingerprint
            )
            if (!isSafeContactPayload(payload)) return@forEach
            deduped[normalized.userId] = normalized
        }
        return deduped.values
            .sortedWith(compareBy<ContactRecord> { it.createdAtMs }.thenBy { it.alias.lowercase() })
            .takeLast(MAX_STORED_CONTACTS)
    }

    private fun saveContacts(contacts: List<ContactRecord>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("userId", c.userId)
                    put("alias", c.alias)
                    put("publicKeyBase64", c.publicKeyBase64)
                    put("fingerprint", c.fingerprint)
                    put("createdAtMs", c.createdAtMs)
                }
            )
        }
        val serialized = arr.toString()
        val previous = prefs.getString(KEY_CONTACTS_JSON, null)
        prefs.edit().apply {
            if (!previous.isNullOrBlank() && previous != serialized) {
                putString(KEY_CONTACTS_JSON_BACKUP, previous)
            }
            putString(KEY_CONTACTS_JSON, serialized)
        }.apply()
    }

    companion object {
        private const val KEY_CONTACTS_JSON = "contacts_json"
        private const val KEY_CONTACTS_JSON_BACKUP = "contacts_json_backup"
        private const val MAX_STORED_CONTACTS = 1_000
    }
}
