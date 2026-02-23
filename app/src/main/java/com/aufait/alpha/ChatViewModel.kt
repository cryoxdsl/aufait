package com.aufait.alpha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aufait.alpha.data.AlphaChatContainer
import com.aufait.alpha.data.ChatMessage
import com.aufait.alpha.data.ContactRecord
import com.aufait.alpha.data.DiscoveredPeer
import com.aufait.alpha.data.IdentityQrPayloadCodec
import com.aufait.alpha.data.TransportDiagnostics
import com.aufait.alpha.data.TransportRoutingMode
import com.aufait.alpha.data.RelayNetworkMode
import com.aufait.alpha.data.TorFallbackPolicy
import com.aufait.alpha.data.TorRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ChatUiState(
    val myIdShort: String = "",
    val myAlias: String = "",
    val aliasDraft: String = "",
    val fingerprint: String = "",
    val peerAlias: String = "demo-peer",
    val selectedPeerNodeId: String? = null,
    val selectedContactUserId: String? = null,
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val transportStatus: String = "LAN mesh alpha",
    val cryptoStatus: String = "crypto: alpha",
    val startupError: String? = null,
    val peers: List<DiscoveredPeer> = emptyList(),
    val conversationActive: Boolean = false,
    val publicKeyBase64: String = "",
    val identityQrPayload: String = "",
    val showIdentityQr: Boolean = false,
    val contacts: List<ContactRecord> = emptyList(),
    val contactImportStatus: String? = null,
    val attachmentDraft: AttachmentDraft? = null,
    val transportRoutingMode: TransportRoutingMode = TransportRoutingMode.AUTO,
    val transportLanPeerCount: Int = 0,
    val transportBluetoothPeerCount: Int = 0,
    val bluetoothEnabled: Boolean = false,
    val bluetoothPermissionGranted: Boolean = false,
    val bluetoothDiscoveryActive: Boolean = false,
    val bluetoothServerListening: Boolean = false,
    val transportLastError: String? = null,
    val relayEnabled: Boolean = false,
    val relayConfigured: Boolean = false,
    val relayNetworkMode: RelayNetworkMode = RelayNetworkMode.DIRECT,
    val torFallbackPolicy: TorFallbackPolicy = TorFallbackPolicy.TOR_PREFERRED,
    val torRuntimeState: TorRuntimeState = TorRuntimeState.DISABLED,
    val torBootstrapPercent: Int = 0,
    val torReady: Boolean = false,
    val torUsingProxy: Boolean = false,
    val relayLastError: String? = null
)

data class AttachmentDraft(
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

class ChatViewModel(
    private val container: AlphaChatContainer
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    val uiState: StateFlow<ChatUiState> = combine(
        local,
        container.messageRepository.messages,
        container.chatService.peers,
        container.contactRepository.contacts,
        combine(container.transportControl.diagnostics, container.relayControl.diagnostics) { t, r -> t to r }
    ) { state, messages, peers, contacts, diagPair ->
        val (diagnostics, relayDiagnostics) = diagPair
        val selectedPeer = peers.firstOrNull { it.nodeId == state.selectedPeerNodeId } ?: peers.firstOrNull()
        val selectedContact = contacts.firstOrNull { it.userId == state.selectedContactUserId } ?: contacts.firstOrNull()
        val activeTargetLabel = selectedPeer?.alias ?: selectedContact?.alias ?: state.peerAlias
        state.copy(
            messages = messages,
            peers = peers,
            contacts = contacts,
            selectedPeerNodeId = selectedPeer?.nodeId,
            selectedContactUserId = selectedContact?.userId,
            peerAlias = activeTargetLabel,
            transportStatus = buildTransportStatus(peers, diagnostics),
            transportRoutingMode = diagnostics.routingMode,
            transportLanPeerCount = diagnostics.lanPeerCount,
            transportBluetoothPeerCount = diagnostics.bluetoothPeerCount,
            bluetoothEnabled = diagnostics.bluetoothEnabled,
            bluetoothPermissionGranted = diagnostics.bluetoothPermissionGranted,
            bluetoothDiscoveryActive = diagnostics.bluetoothDiscoveryActive,
            bluetoothServerListening = diagnostics.bluetoothServerListening,
            transportLastError = diagnostics.bluetoothLastError,
            relayEnabled = relayDiagnostics.enabled,
            relayConfigured = relayDiagnostics.relayConfigured,
            relayNetworkMode = relayDiagnostics.relayNetworkMode,
            torFallbackPolicy = relayDiagnostics.torFallbackPolicy,
            torRuntimeState = relayDiagnostics.torRuntimeState,
            torBootstrapPercent = relayDiagnostics.torBootstrapPercent,
            torReady = relayDiagnostics.torReady,
            torUsingProxy = relayDiagnostics.torUsingProxy,
            relayLastError = relayDiagnostics.lastError
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                container.chatService.start()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = container.relayPreferencesRepository.preferences.first()
                container.relayControl.setTorFallbackPolicy(prefs.torFallbackPolicy)
                container.relayControl.setRelayNetworkMode(prefs.relayNetworkMode)
            }
            runCatching { container.identityRepository.getOrCreateIdentity() }
                .onSuccess { identity ->
                    local.update {
                        it.copy(
                            myIdShort = identity.id.take(12),
                            myAlias = identity.alias,
                            aliasDraft = identity.alias,
                            fingerprint = identity.fingerprint,
                            publicKeyBase64 = identity.publicKeyBase64,
                            identityQrPayload = IdentityQrPayloadCodec.encode(identity),
                            cryptoStatus = buildCryptoStatus(),
                            startupError = null
                        )
                    }
                }
                .onFailure { error ->
                    local.update {
                        it.copy(startupError = error.message ?: error::class.java.simpleName)
                    }
                }
        }
    }

    fun onInputChanged(value: String) {
        local.update { it.copy(input = value) }
    }

    fun onAliasDraftChanged(value: String) {
        local.update { it.copy(aliasDraft = value.take(32)) }
    }

    fun saveAlias() {
        val alias = uiState.value.aliasDraft.trim()
        if (alias.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.chatService.updateLocalAlias(alias)
                container.identityRepository.getOrCreateIdentity()
            }.onSuccess { identity ->
                local.update {
                        it.copy(
                            myAlias = identity.alias,
                            aliasDraft = identity.alias,
                            publicKeyBase64 = identity.publicKeyBase64,
                            identityQrPayload = IdentityQrPayloadCodec.encode(identity),
                            cryptoStatus = buildCryptoStatus(),
                            startupError = null
                        )
                }
            }.onFailure { error ->
                local.update { it.copy(startupError = error.message ?: error::class.java.simpleName) }
            }
        }
    }

    fun selectPeer(nodeId: String) {
        local.update { it.copy(selectedPeerNodeId = nodeId, selectedContactUserId = null) }
    }

    fun selectContact(userId: String) {
        local.update { it.copy(selectedContactUserId = userId, selectedPeerNodeId = null) }
    }

    fun sendMessage() {
        val state = uiState.value
        val text = state.input.trim()
        val attachment = state.attachmentDraft
        if (text.isEmpty() && attachment == null) return

        val peerRef = state.peers.firstOrNull { it.nodeId == state.selectedPeerNodeId }?.nodeId
            ?: state.contacts.firstOrNull { it.userId == state.selectedContactUserId }?.userId
            ?: state.peerAlias
        val outboundBody = buildOutgoingBody(text, attachment)
        local.update { it.copy(input = "", attachmentDraft = null) }

        viewModelScope.launch(Dispatchers.IO) {
            container.chatService.sendToPeer(peerRef, outboundBody)
        }
    }

    fun onConversationForegroundChanged(inForeground: Boolean) {
        local.update { it.copy(conversationActive = inForeground) }
        viewModelScope.launch(Dispatchers.IO) {
            container.chatService.setConversationForeground(inForeground)
        }
    }

    fun setTransportRoutingMode(mode: TransportRoutingMode) {
        viewModelScope.launch(Dispatchers.IO) {
            container.transportControl.setRoutingMode(mode)
        }
    }

    fun setRelayNetworkMode(mode: RelayNetworkMode) {
        viewModelScope.launch(Dispatchers.IO) {
            container.relayPreferencesRepository.setRelayNetworkMode(mode)
            container.relayControl.setRelayNetworkMode(mode)
        }
    }

    fun setTorFallbackPolicy(policy: TorFallbackPolicy) {
        viewModelScope.launch(Dispatchers.IO) {
            container.relayPreferencesRepository.setTorFallbackPolicy(policy)
            container.relayControl.setTorFallbackPolicy(policy)
        }
    }

    fun setIdentityQrVisible(visible: Boolean) {
        local.update { it.copy(showIdentityQr = visible) }
    }

    fun importContactFromQr(payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val imported = container.contactRepository.importFromQrPayload(payload)
            local.update {
                if (imported != null) {
                    it.copy(contactImportStatus = "Contact importe: ${imported.alias}")
                } else {
                    it.copy(contactImportStatus = "QR invalide / non supporte")
                }
            }
        }
    }

    fun clearContactImportStatus() {
        local.update { it.copy(contactImportStatus = null) }
    }

    fun onAttachmentPicked(
        uriString: String,
        displayName: String,
        mimeType: String?,
        sizeBytes: Long?
    ) {
        val safeName = displayName
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .ifBlank { "fichier" }
            .take(120)
        val safeMime = mimeType
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            ?.take(80)
        val safeSize = sizeBytes?.takeIf { it in 0..MAX_ATTACHMENT_SIZE_BYTES }
        local.update {
            it.copy(
                attachmentDraft = AttachmentDraft(
                    uriString = uriString,
                    displayName = safeName,
                    mimeType = safeMime,
                    sizeBytes = safeSize
                )
            )
        }
    }

    fun clearAttachmentDraft() {
        local.update { it.copy(attachmentDraft = null) }
    }

    private fun buildCryptoStatus(): String {
        val bundle = container.x3dhPreKeyRepository.getOrCreateBundleSummary()
        return "${container.chatService.cryptoModeLabel} • x3dh-prekeys:${bundle.oneTimePreKeyCount}"
    }

    private fun buildTransportStatus(
        peers: List<DiscoveredPeer>,
        diagnostics: TransportDiagnostics
    ): String {
        val modeLabel = when (diagnostics.routingMode) {
            TransportRoutingMode.AUTO -> "Auto"
            TransportRoutingMode.LAN_ONLY -> "LAN"
            TransportRoutingMode.BLUETOOTH_ONLY -> "Bluetooth"
        }
        if (peers.isEmpty()) {
            return "Transport $modeLabel (aucun pair)"
        }
        val lan = diagnostics.lanPeerCount
        val bt = diagnostics.bluetoothPeerCount
        return "Transport $modeLabel (LAN:$lan • BT:$bt • visibles:${peers.size})"
    }

    private fun buildOutgoingBody(text: String, attachment: AttachmentDraft?): String {
        if (attachment == null) return text
        val attachmentLine = buildString {
            append("📎 ")
            append(attachment.displayName)
            attachment.sizeBytes?.let { append(" (").append(formatBytes(it)).append(")") }
            attachment.mimeType?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
        }
        val combined = if (text.isBlank()) attachmentLine else "$attachmentLine\n$text"
        return combined.take(MAX_OUTBOUND_BODY_CHARS)
    }

    private fun formatBytes(size: Long): String {
        if (size < 1024) return "${size} B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    companion object {
        private const val MAX_ATTACHMENT_SIZE_BYTES = 64L * 1024L * 1024L
        private const val MAX_OUTBOUND_BODY_CHARS = 8_000
    }
}
