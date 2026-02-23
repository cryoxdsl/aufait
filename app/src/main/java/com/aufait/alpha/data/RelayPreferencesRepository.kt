package com.aufait.alpha.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.relayPrefsDataStore by preferencesDataStore(name = "relay_preferences")

data class RelayPreferences(
    val relayNetworkMode: RelayNetworkMode = RelayNetworkMode.DIRECT,
    val torFallbackPolicy: TorFallbackPolicy = TorFallbackPolicy.TOR_PREFERRED
)

class RelayPreferencesRepository(
    private val appContext: Context
) {
    val preferences: Flow<RelayPreferences> = appContext.relayPrefsDataStore.data
        .map { prefs -> prefs.toRelayPreferences() }

    suspend fun setRelayNetworkMode(mode: RelayNetworkMode) {
        appContext.relayPrefsDataStore.edit { prefs ->
            prefs[KEY_RELAY_NETWORK_MODE] = mode.name
        }
    }

    suspend fun setTorFallbackPolicy(policy: TorFallbackPolicy) {
        appContext.relayPrefsDataStore.edit { prefs ->
            prefs[KEY_TOR_FALLBACK_POLICY] = policy.name
        }
    }

    private fun Preferences.toRelayPreferences(): RelayPreferences {
        val relayMode = this[KEY_RELAY_NETWORK_MODE]
            ?.let { raw -> RelayNetworkMode.values().firstOrNull { it.name == raw } }
            ?: RelayNetworkMode.DIRECT
        val torPolicy = this[KEY_TOR_FALLBACK_POLICY]
            ?.let { raw -> TorFallbackPolicy.values().firstOrNull { it.name == raw } }
            ?: TorFallbackPolicy.TOR_PREFERRED
        return RelayPreferences(
            relayNetworkMode = relayMode,
            torFallbackPolicy = torPolicy
        )
    }

    companion object {
        private val KEY_RELAY_NETWORK_MODE = stringPreferencesKey("relay_network_mode")
        private val KEY_TOR_FALLBACK_POLICY = stringPreferencesKey("tor_fallback_policy")
    }
}
