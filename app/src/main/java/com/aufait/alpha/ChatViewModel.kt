package com.aufait.alpha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aufait.alpha.data.AlphaChatContainer
import com.aufait.alpha.data.ChatMessage
import com.aufait.alpha.data.DiscoveredPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val myIdShort: String = "",
    val fingerprint: String = "",
    val peerAlias: String = "demo-peer",
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val transportStatus: String = "LAN mesh alpha",
    val startupError: String? = null,
    val peers: List<DiscoveredPeer> = emptyList()
)

class ChatViewModel(
    private val container: AlphaChatContainer
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    val uiState: StateFlow<ChatUiState> = combine(
        local,
        container.messageRepository.messages,
        container.chatService.peers
    ) { state, messages, peers ->
        val selectedPeer = peers.firstOrNull()?.alias ?: state.peerAlias
        state.copy(
            messages = messages,
            peers = peers,
            peerAlias = selectedPeer,
            transportStatus = if (peers.isEmpty()) {
                "LAN mesh alpha (aucun pair, fallback local)"
            } else {
                "LAN mesh alpha (${peers.size} pair(s))"
            }
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                container.chatService.start()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.identityRepository.getOrCreateIdentity() }
                .onSuccess { identity ->
                    local.update {
                        it.copy(
                            myIdShort = identity.id.take(12),
                            fingerprint = identity.fingerprint,
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

    fun sendMessage() {
        val text = uiState.value.input.trim()
        if (text.isEmpty()) return

        val peer = uiState.value.peerAlias
        local.update { it.copy(input = "") }

        viewModelScope.launch(Dispatchers.IO) {
            container.chatService.sendToPeer(peer, text)
        }
    }
}
