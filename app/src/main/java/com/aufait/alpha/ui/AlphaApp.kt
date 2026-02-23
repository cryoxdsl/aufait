package com.aufait.alpha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.ChatViewModel
import com.aufait.alpha.AttachmentDraft
import com.aufait.alpha.R
import com.aufait.alpha.data.TransportRoutingMode
import com.aufait.alpha.data.RelayNetworkMode
import com.aufait.alpha.data.TorFallbackPolicy

@Composable
fun AlphaApp(
    viewModel: ChatViewModel,
    onScanContactQrRequest: () -> Unit = {},
    onPickAttachmentRequest: () -> Unit = {},
    onBluetoothPermissionRequest: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    EnFaitTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AlphaScreen(
                state = uiState,
                onInputChanged = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onSelectPeer = viewModel::selectPeer,
                onSelectContact = viewModel::selectContact,
                onAliasDraftChanged = viewModel::onAliasDraftChanged,
                onSaveAlias = viewModel::saveAlias,
                onToggleIdentityQr = viewModel::setIdentityQrVisible,
                onScanContactQrRequest = onScanContactQrRequest,
                onDismissContactImportStatus = viewModel::clearContactImportStatus,
                onPickAttachmentRequest = onPickAttachmentRequest,
                onClearAttachmentDraft = viewModel::clearAttachmentDraft,
                onSetTransportRoutingMode = { mode ->
                    if (mode != TransportRoutingMode.LAN_ONLY) {
                        onBluetoothPermissionRequest()
                    }
                    viewModel.setTransportRoutingMode(mode)
                },
                onSetRelayNetworkMode = viewModel::setRelayNetworkMode,
                onSetTorFallbackPolicy = viewModel::setTorFallbackPolicy,
                onRelaySharedSecretDraftChanged = viewModel::onRelaySharedSecretDraftChanged,
                onSaveRelaySharedSecret = viewModel::saveRelaySharedSecret
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlphaScreen(
    state: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSelectPeer: (String) -> Unit,
    onSelectContact: (String) -> Unit,
    onAliasDraftChanged: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onToggleIdentityQr: (Boolean) -> Unit,
    onScanContactQrRequest: () -> Unit,
    onDismissContactImportStatus: () -> Unit,
    onPickAttachmentRequest: () -> Unit,
    onClearAttachmentDraft: () -> Unit,
    onSetTransportRoutingMode: (TransportRoutingMode) -> Unit,
    onSetRelayNetworkMode: (RelayNetworkMode) -> Unit,
    onSetTorFallbackPolicy: (TorFallbackPolicy) -> Unit,
    onRelaySharedSecretDraftChanged: (String) -> Unit,
    onSaveRelaySharedSecret: () -> Unit
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    if (state.showIdentityQr) {
        IdentityQrDialog(
            payload = state.identityQrPayload,
            onDismiss = { onToggleIdentityQr(false) }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            state = state,
            onDismiss = { showSettingsSheet = false },
            onAliasDraftChanged = onAliasDraftChanged,
            onSaveAlias = onSaveAlias,
            onShowIdentityQr = { onToggleIdentityQr(true) },
            onScanContactQr = onScanContactQrRequest,
            onDismissContactImportStatus = onDismissContactImportStatus,
            onSelectContact = onSelectContact,
            onSelectPeer = onSelectPeer,
            onSetTransportRoutingMode = onSetTransportRoutingMode,
            onSetRelayNetworkMode = onSetRelayNetworkMode,
            onSetTorFallbackPolicy = onSetTorFallbackPolicy,
            onRelaySharedSecretDraftChanged = onRelaySharedSecretDraftChanged,
            onSaveRelaySharedSecret = onSaveRelaySharedSecret
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EnFaitChatTopBar(
                state = state,
                onOpenSettings = { showSettingsSheet = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .imePadding()
            ) {
                ConversationSummaryCard(
                    state = state,
                    onOpenSettings = { showSettingsSheet = true },
                    onShowIdentityQr = { onToggleIdentityQr(true) },
                    onScanContactQr = onScanContactQrRequest
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ChatPanelHeader(state = state)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                        MessagesList(
                            messages = state.messages,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Composer(
                    input = state.input,
                    attachmentDraft = state.attachmentDraft,
                    onInputChanged = onInputChanged,
                    onSend = onSend,
                    onAttach = onPickAttachmentRequest,
                    onClearAttachment = onClearAttachmentDraft
                )
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    attachmentDraft: AttachmentDraft?,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.semantics {
            isTraversalGroup = true
            traversalIndex = 2f
        }
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttach,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { role = Role.Button }
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = stringResource(R.string.composer_attach_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                attachmentDraft?.let { draft ->
                    AttachmentPreviewChip(
                        draft = draft,
                        onRemove = onClearAttachment
                    )
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.composer_message_label)) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = input.isNotBlank() || attachmentDraft != null,
                modifier = Modifier
                    .height(56.dp)
                    .semantics { role = Role.Button },
                shape = CircleShape
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.composer_send_cd))
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    draft: AttachmentDraft,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = listOfNotNull(
                        draft.mimeType?.takeIf { it.isNotBlank() },
                        draft.sizeBytes?.let { formatBytesUi(it) }
                    ).joinToString(stringResource(R.string.composer_file_meta_separator)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onRemove,
                modifier = Modifier.semantics { role = Role.Button }
            ) {
                Text(stringResource(R.string.composer_remove_file))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun AlphaScreenPreview() {
    EnFaitTheme {
        AlphaScreen(
            state = AlphaPreviewData.chatState(),
            onInputChanged = {},
            onSend = {},
            onSelectPeer = {},
            onSelectContact = {},
            onAliasDraftChanged = {},
            onSaveAlias = {},
            onToggleIdentityQr = {},
            onScanContactQrRequest = {},
            onDismissContactImportStatus = {},
            onPickAttachmentRequest = {},
            onClearAttachmentDraft = {},
            onSetTransportRoutingMode = {},
            onSetRelayNetworkMode = {},
            onSetTorFallbackPolicy = {},
            onRelaySharedSecretDraftChanged = {},
            onSaveRelaySharedSecret = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposerPreview() {
    EnFaitTheme {
        Composer(
            input = AlphaPreviewData.chatState().input,
            attachmentDraft = AlphaPreviewData.attachmentDraft(),
            onInputChanged = {},
            onSend = {},
            onAttach = {},
            onClearAttachment = {}
        )
    }
}


@Composable
private fun formatBytesUi(size: Long): String {
    if (size < 1024) return stringResource(R.string.size_bytes, size)
    val kb = size / 1024.0
    if (kb < 1024) return stringResource(R.string.size_kb, kb)
    val mb = kb / 1024.0
    return stringResource(R.string.size_mb, mb)
}

