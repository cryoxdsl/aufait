package com.aufait.alpha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aufait.alpha.data.AlphaChatContainer
import com.aufait.alpha.data.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val myIdShort: String = "",
    val fingerprint: String = "",
    val peerAlias: String = "demo-peer",
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val transportStatus: String = "Mesh alpha (simule)"
)

class ChatViewModel(
    private val container: AlphaChatContainer
) : ViewModel() {
    private val local = MutableStateFlow(ChatUiState())

    val uiState: StateFlow<ChatUiState> = combine(
        local,
        container.messageRepository.messages
    ) { state, messages ->
        state.copy(messages = messages)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ChatUiState())

    init {
        val identity = container.identityRepository.getOrCreateIdentity()
        local.update {
            it.copy(
                myIdShort = identity.id.take(12),
                fingerprint = identity.fingerprint
            )
        }

        viewModelScope.launch {
            container.chatService.start()
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

        viewModelScope.launch {
            container.chatService.sendToPeer(peer, text)
        }
    }
}
