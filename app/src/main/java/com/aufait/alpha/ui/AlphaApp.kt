package com.aufait.alpha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.ChatViewModel
import com.aufait.alpha.data.ChatMessage
import com.aufait.alpha.data.DiscoveredPeer
import com.aufait.alpha.data.ContactRecord
import com.aufait.alpha.data.MessageDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlphaApp(
    viewModel: ChatViewModel,
    onScanContactQrRequest: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    MaterialTheme {
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
                onDismissContactImportStatus = viewModel::clearContactImportStatus
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
    onDismissContactImportStatus: () -> Unit
) {
    if (state.showIdentityQr) {
        IdentityQrDialog(
            payload = state.identityQrPayload,
            onDismiss = { onToggleIdentityQr(false) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aufait Alpha")
                        Text(
                            text = state.transportStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            IdentityCard(
                state = state,
                onAliasDraftChanged = onAliasDraftChanged,
                onSaveAlias = onSaveAlias,
                onShowIdentityQr = { onToggleIdentityQr(true) },
                onScanContactQr = onScanContactQrRequest
            )
            Spacer(Modifier.height(10.dp))
            ContactsCard(
                contacts = state.contacts,
                selectedContactUserId = state.selectedContactUserId,
                contactImportStatus = state.contactImportStatus,
                onDismissStatus = onDismissContactImportStatus,
                onSelectContact = onSelectContact
            )
            Spacer(Modifier.height(10.dp))
            PeersCard(
                peers = state.peers,
                selectedPeerNodeId = state.selectedPeerNodeId,
                onSelectPeer = onSelectPeer
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            MessagesList(
                messages = state.messages,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(8.dp))
            Composer(
                input = state.input,
                onInputChanged = onInputChanged,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun PeersCard(
    peers: List<DiscoveredPeer>,
    selectedPeerNodeId: String?,
    onSelectPeer: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Pairs LAN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (peers.isEmpty()) {
                Text(
                    "Aucun pair detecte pour l'instant (meme Wi-Fi requis).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            peers.forEach { peer ->
                val isSelected = peer.nodeId == selectedPeerNodeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .clickable { onSelectPeer(peer.nodeId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        Icon(Icons.Default.Check, contentDescription = "Selectionne")
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Identite locale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            state.startupError?.let {
                Text(
                    text = "Erreur init: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
            }
            Text("ID: ${state.myIdShort}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Alias local: ${state.myAlias}", style = MaterialTheme.typography.bodySmall)
            Text("Fingerprint: ${state.fingerprint}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.aliasDraft,
                    onValueChange = onAliasDraftChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Alias local") },
                    singleLine = true,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSaveAlias,
                    enabled = state.aliasDraft.trim().isNotEmpty()
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Sauver alias")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = onShowIdentityQr) {
                    Text("QR identite")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onScanContactQr) {
                    Text("Scanner QR contact")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Pair cible (alpha): ${state.peerAlias}", style = MaterialTheme.typography.labelMedium)
            Text(
                "Pairs LAN detectes: ${state.peers.size}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = state.cryptoStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (state.conversationActive) "Conversation active (lu en temps reel)" else "Conversation inactive (lu differe)",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.conversationActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contacts importes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("${contacts.size}", style = MaterialTheme.typography.labelMedium)
            }
            contactImportStatus?.let { status ->
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = onDismissStatus,
                    label = { Text(status) }
                )
            }
            if (contacts.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Aucun contact importe. Utilise \"Scanner QR contact\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Spacer(Modifier.height(6.dp))
            contacts.take(5).forEach { contact ->
                val isSelected = selectedContactUserId == contact.userId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                        .clickable { onSelectContact(contact.userId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(contact.alias, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${contact.userId.take(12)} • ${contact.fingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun IdentityQrDialog(
    payload: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(payload) {
        runCatching { QrCodeBitmapFactory.create(payload) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fermer") }
        },
        title = { Text("QR identite") },
        text = {
            Column {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR identite",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } ?: Text("Impossible de generer le QR")
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
private fun MessagesList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Aucun message. Envoie un premier message pour tester le flux alpha.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages.reversed(), key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val outbound = message.direction == MessageDirection.OUTBOUND
    val bg = if (outbound) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val alignment = if (outbound) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(text = message.body)
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatTime(message.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (outbound) {
                Text(
                    text = formatReceiptStatus(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChanged,
            modifier = Modifier.weight(1f),
            label = { Text("Message") },
            minLines = 1,
            maxLines = 4
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = input.isNotBlank(),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = "Envoyer")
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

private fun formatReceiptStatus(message: ChatMessage): String {
    val readAt = message.readAtMs
    if (readAt != null) return "lu ${formatTime(readAt)}"
    val deliveredAt = message.deliveredAtMs
    if (deliveredAt != null) return "recu ${formatTime(deliveredAt)}"
    return "envoye"
}
