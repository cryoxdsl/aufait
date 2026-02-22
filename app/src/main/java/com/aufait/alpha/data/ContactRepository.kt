package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class ContactRepository(context: Context) {
    private val prefs = context.getSharedPreferences("aufait_contacts", Context.MODE_PRIVATE)
    private val _contacts = MutableStateFlow(loadContacts())
    val contacts: StateFlow<List<ContactRecord>> = _contacts

    fun importFromQrPayload(payload: String): ContactRecord? {
        val decoded = IdentityQrPayloadCodec.decode(payload) ?: return null
        val contact = ContactRecord(
            userId = decoded.userId,
            alias = decoded.alias.trim().ifBlank { "contact-${decoded.userId.take(6)}" }.take(32),
            publicKeyBase64 = decoded.publicKeyBase64,
            fingerprint = decoded.fingerprint,
            createdAtMs = System.currentTimeMillis()
        )
        upsert(contact)
        return contact
    }

    private fun upsert(contact: ContactRecord) {
        val current = loadContacts().toMutableList()
        val idx = current.indexOfFirst { it.userId == contact.userId }
        if (idx >= 0) current[idx] = contact.copy(createdAtMs = current[idx].createdAtMs)
        else current += contact
        saveContacts(current)
        _contacts.value = current.sortedBy { it.alias.lowercase() }
    }

    private fun loadContacts(): List<ContactRecord> {
        val raw = prefs.getString(KEY_CONTACTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ContactRecord(
                            userId = o.getString("userId"),
                            alias = o.getString("alias"),
                            publicKeyBase64 = o.getString("publicKeyBase64"),
                            fingerprint = o.getString("fingerprint"),
                            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }.sortedBy { it.alias.lowercase() }
        }.getOrElse { emptyList() }
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
        prefs.edit().putString(KEY_CONTACTS_JSON, arr.toString()).apply()
    }

    companion object {
        private const val KEY_CONTACTS_JSON = "contacts_json"
    }
}
