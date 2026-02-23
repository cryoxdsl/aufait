package com.aufait.alpha.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.R
import com.aufait.alpha.data.ContactRecord
import com.aufait.alpha.data.DiscoveredPeer

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SettingsBottomSheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onAliasDraftChanged: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onShowIdentityQr: () -> Unit,
    onScanContactQr: () -> Unit,
    onDismissContactImportStatus: () -> Unit,
    onSelectContact: (String) -> Unit,
    onSelectPeer: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                }
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SheetSectionHeader(stringResource(R.string.settings_section_identity))
            IdentityCard(
                state = state,
                onAliasDraftChanged = onAliasDraftChanged,
                onSaveAlias = onSaveAlias,
                onShowIdentityQr = onShowIdentityQr,
                onScanContactQr = onScanContactQr
            )
            Spacer(Modifier.height(10.dp))
            SheetSectionHeader(stringResource(R.string.settings_section_contacts))
            ContactsCard(
                contacts = state.contacts,
                selectedContactUserId = state.selectedContactUserId,
                contactImportStatus = state.contactImportStatus,
                onDismissStatus = onDismissContactImportStatus,
                onSelectContact = onSelectContact
            )
            Spacer(Modifier.height(10.dp))
            SheetSectionHeader(stringResource(R.string.settings_section_peers))
            PeersCard(
                peers = state.peers,
                selectedPeerNodeId = state.selectedPeerNodeId,
                onSelectPeer = onSelectPeer
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PeersCard(
    peers: List<DiscoveredPeer>,
    selectedPeerNodeId: String?,
    onSelectPeer: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.peers_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (peers.isEmpty()) {
                EmptyInfoCard(
                    title = stringResource(R.string.peers_empty_title),
                    subtitle = stringResource(R.string.peers_empty_body)
                )
                return@Column
            }
            peers.forEach { peer ->
                val isSelected = peer.nodeId == selectedPeerNodeId
                val peerSelectionLabel = if (isSelected) {
                    stringResource(R.string.a11y_peer_selected)
                } else {
                    stringResource(R.string.a11y_peer_not_selected)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectPeer(peer.nodeId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            stateDescription = peerSelectionLabel
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peer.alias, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            peer.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.a11y_selected))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun IdentityCard(
    state: ChatUiState,
    onAliasDraftChanged: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onShowIdentityQr: () -> Unit,
    onScanContactQr: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.identity_local_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            state.startupError?.let {
                Text(
                    text = stringResource(R.string.identity_init_error, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(stringResource(R.string.identity_id, state.myIdShort), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.identity_alias, state.myAlias), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.identity_fingerprint, state.fingerprint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.aliasDraft,
                    onValueChange = onAliasDraftChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.identity_alias_label)) },
                    singleLine = true,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSaveAlias,
                    enabled = state.aliasDraft.trim().isNotEmpty(),
                    modifier = Modifier.semantics { role = Role.Button }
                ) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.identity_save_alias))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShowIdentityQr, modifier = Modifier.semantics { role = Role.Button }) {
                    Text(stringResource(R.string.action_qr_identity))
                }
                TextButton(onClick = onScanContactQr, modifier = Modifier.semantics { role = Role.Button }) {
                    Text(stringResource(R.string.action_scan_qr))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.identity_target_peer, state.peerAlias), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.identity_peers_detected, state.peers.size), style = MaterialTheme.typography.labelMedium)
            Text(
                text = state.cryptoStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (state.conversationActive) {
                    stringResource(R.string.identity_conversation_active)
                } else {
                    stringResource(R.string.identity_conversation_inactive)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.conversationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactsCard(
    contacts: List<ContactRecord>,
    selectedContactUserId: String?,
    contactImportStatus: String?,
    onDismissStatus: () -> Unit,
    onSelectContact: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.contacts_imported_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.contacts_count_only, contacts.size), style = MaterialTheme.typography.labelMedium)
            }
            contactImportStatus?.let { status ->
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = onDismissStatus,
                    modifier = Modifier.semantics { role = Role.Button },
                    label = { Text(status) }
                )
            }
            if (contacts.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                EmptyInfoCard(
                    title = stringResource(R.string.contacts_empty_title),
                    subtitle = stringResource(R.string.contacts_empty_body)
                )
                return@Column
            }
            Spacer(Modifier.height(6.dp))
            contacts.take(5).forEach { contact ->
                val isSelected = selectedContactUserId == contact.userId
                val contactSelectionLabel = if (isSelected) {
                    stringResource(R.string.a11y_contact_selected)
                } else {
                    stringResource(R.string.a11y_contact_not_selected)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectContact(contact.userId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            stateDescription = contactSelectionLabel
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = contact.alias, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact.alias, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${contact.userId.take(12)} • ${contact.fingerprint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
internal fun IdentityQrDialog(
    payload: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(payload) {
        runCatching { QrCodeBitmapFactory.create(payload) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.semantics { role = Role.Button }) { Text(stringResource(R.string.qr_dialog_close)) }
        },
        title = { Text(stringResource(R.string.qr_dialog_title)) },
        text = {
            Column {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_dialog_image_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } ?: Text(stringResource(R.string.qr_dialog_error))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun EmptyInfoCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IdentityCardPreview() {
    EnFaitTheme {
        IdentityCard(
            state = AlphaPreviewData.chatState(),
            onAliasDraftChanged = {},
            onSaveAlias = {},
            onShowIdentityQr = {},
            onScanContactQr = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactsCardPreview() {
    EnFaitTheme {
        ContactsCard(
            contacts = AlphaPreviewData.contacts(),
            selectedContactUserId = AlphaPreviewData.contacts().first().userId,
            contactImportStatus = stringResource(R.string.preview_contact_imported_alice),
            onDismissStatus = {},
            onSelectContact = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PeersCardPreview() {
    EnFaitTheme {
        PeersCard(
            peers = AlphaPreviewData.peers(),
            selectedPeerNodeId = AlphaPreviewData.peers().first().nodeId,
            onSelectPeer = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyInfoCardPreview() {
    EnFaitTheme {
        EmptyInfoCard(
            title = stringResource(R.string.preview_empty_contact_title),
            subtitle = stringResource(R.string.preview_empty_contact_subtitle)
        )
    }
}
