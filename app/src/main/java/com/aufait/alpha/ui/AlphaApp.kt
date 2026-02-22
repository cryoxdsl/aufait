package com.aufait.alpha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.ChatViewModel
import com.aufait.alpha.R
import com.aufait.alpha.AttachmentDraft
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
    onScanContactQrRequest: () -> Unit = {},
    onPickAttachmentRequest: () -> Unit = {}
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
                onClearAttachmentDraft = viewModel::clearAttachmentDraft
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
    onClearAttachmentDraft: () -> Unit
) {
    var panelsExpanded by rememberSaveable { mutableStateOf(false) }

    if (state.showIdentityQr) {
        IdentityQrDialog(
            payload = state.identityQrPayload,
            onDismiss = { onToggleIdentityQr(false) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EnFaitChatTopBar(
                state = state,
                panelsExpanded = panelsExpanded,
                onTogglePanels = { panelsExpanded = !panelsExpanded }
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
                if (panelsExpanded) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                    ) {
                        ConversationHeaderCompact(
                            state = state,
                            panelsExpanded = panelsExpanded,
                            onTogglePanels = { panelsExpanded = !panelsExpanded }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            IdentityCard(
                                state = state,
                                onAliasDraftChanged = onAliasDraftChanged,
                                onSaveAlias = onSaveAlias,
                                onShowIdentityQr = { onToggleIdentityQr(true) },
                                onScanContactQr = onScanContactQrRequest
                            )
                            Spacer(Modifier.height(8.dp))
                            ContactsCard(
                                contacts = state.contacts,
                                selectedContactUserId = state.selectedContactUserId,
                                contactImportStatus = state.contactImportStatus,
                                onDismissStatus = onDismissContactImportStatus,
                                onSelectContact = onSelectContact
                            )
                            Spacer(Modifier.height(8.dp))
                            PeersCard(
                                peers = state.peers,
                                selectedPeerNodeId = state.selectedPeerNodeId,
                                onSelectPeer = onSelectPeer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (panelsExpanded) 8.dp else 2.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MessagesList(
                            messages = state.messages,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
@OptIn(ExperimentalMaterial3Api::class)
private fun EnFaitChatTopBar(
    state: ChatUiState,
    panelsExpanded: Boolean,
    onTogglePanels: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_enfait_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "EnFait",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.peerAlias,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = if (state.conversationActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onTogglePanels) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = if (panelsExpanded) "Masquer informations" else "Afficher informations"
                )
            }
        }
    )
}

@Composable
private fun ChatTopBarTitle(state: ChatUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.peerAlias.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = state.peerAlias,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.transportStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationHeaderCompact(
    state: ChatUiState,
    panelsExpanded: Boolean,
    onTogglePanels: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.conversationActive) "Conversation active" else "Conversation inactive",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Cible: ${state.peerAlias} • LAN ${state.peers.size} • Contacts ${state.contacts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = onTogglePanels,
                leadingIcon = {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(if (panelsExpanded) "Infos" else "Infos") }
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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
                TextButton(onClick = onShowIdentityQr) {
                    Text("QR identite")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onScanContactQr) {
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
    val rows = remember(messages) { buildConversationRows(messages) }

    LaunchedEffect(messages.lastOrNull()?.id, rows.size) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Text(
                    "Aucun message pour le moment.\nCommence la conversation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { it.key }) { row ->
            AnimatedConversationRow(rowKey = row.key) {
                when (row) {
                    is ConversationRow.DateSeparator -> DateSeparatorChip(row.label)
                    is ConversationRow.Message -> MessageBubble(row.message)
                }
            }
        }
    }
}

@Composable
private fun AnimatedConversationRow(
    rowKey: String,
    content: @Composable () -> Unit
) {
    var visible by remember(rowKey) { mutableStateOf(false) }
    LaunchedEffect(rowKey) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        content()
    }
}

@Composable
private fun DateSeparatorChip(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val outbound = message.direction == MessageDirection.OUTBOUND
    val bg = if (outbound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outbound) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!outbound) {
            ContactAvatar(
                name = message.author,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (outbound) 0.84f else 0.78f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (outbound) 18.dp else 6.dp,
                        bottomEnd = if (outbound) 6.dp else 18.dp
                    )
                )
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (!outbound) {
                Text(
                    text = message.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.body,
                color = if (outbound) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outbound) Arrangement.End else Arrangement.Start
    ) {
        if (!outbound) {
            Spacer(Modifier.width(36.dp))
        }
        MessageMetaLine(message = message, outbound = outbound)
    }
}

@Composable
private fun MessageMetaLine(message: ChatMessage, outbound: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = formatTime(message.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (outbound) {
            val receipt = receiptVisual(message)
            Icon(
                imageVector = receipt.icon,
                contentDescription = receipt.label,
                modifier = Modifier.size(14.dp),
                tint = receipt.tint
            )
        }
    }
}

private data class ReceiptVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String
)

@Composable
private fun receiptVisual(message: ChatMessage): ReceiptVisual {
    val scheme = MaterialTheme.colorScheme
    return when {
        message.readAtMs != null -> ReceiptVisual(Icons.Default.DoneAll, Color(0xFF60A5FA), "Lu")
        message.deliveredAtMs != null -> ReceiptVisual(Icons.Default.DoneAll, scheme.onSurfaceVariant, "Recu")
        else -> ReceiptVisual(Icons.Default.Done, scheme.onSurfaceVariant, "Envoye")
    }
}

@Composable
private fun ContactAvatar(name: String, modifier: Modifier = Modifier) {
    val bg = avatarColorFor(name)
    val initials = initialsFor(name)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF0F766E),
        Color(0xFF2563EB),
        Color(0xFF0891B2),
        Color(0xFF7C3AED),
        Color(0xFFDC2626),
        Color(0xFFEA580C),
        Color(0xFF16A34A)
    )
    val idx = (name.lowercase().hashCode() and Int.MAX_VALUE) % palette.size
    return palette[idx]
}

private fun initialsFor(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "?"
    val parts = cleaned.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        else -> cleaned.take(2).uppercase()
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
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Piece jointe",
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
                    label = { Text("Message") },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = input.isNotBlank() || attachmentDraft != null,
                modifier = Modifier.height(56.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Send, contentDescription = "Envoyer")
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
                    text = listOfNotNull(draft.mimeType?.takeIf { it.isNotBlank() }, draft.sizeBytes?.let(::formatBytesUi)).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(onClick = onRemove) {
                Text("Retirer")
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

private fun formatBytesUi(size: Long): String {
    if (size < 1024) return "${size} B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

private sealed class ConversationRow(val key: String) {
    class DateSeparator(val dayStartMs: Long, val label: String) : ConversationRow("d-$dayStartMs")
    class Message(val message: ChatMessage) : ConversationRow("m-${message.id}")
}

private fun buildConversationRows(messages: List<ChatMessage>): List<ConversationRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = mutableListOf<ConversationRow>()
    var previousDayStart: Long? = null
    messages.forEach { msg ->
        val dayStart = startOfDayMs(msg.timestampMs)
        if (previousDayStart != dayStart) {
            rows += ConversationRow.DateSeparator(dayStart, formatDaySeparator(dayStart))
            previousDayStart = dayStart
        }
        rows += ConversationRow.Message(msg)
    }
    return rows
}

private fun startOfDayMs(timestampMs: Long): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = timestampMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun formatDaySeparator(dayStartMs: Long): String {
    val today = startOfDayMs(System.currentTimeMillis())
    val oneDay = 24L * 60L * 60L * 1000L
    return when (dayStartMs) {
        today -> "Aujourd'hui"
        today - oneDay -> "Hier"
        else -> {
            val formatter = SimpleDateFormat("dd MMM", Locale.FRENCH)
            formatter.format(Date(dayStartMs))
        }
    }
}
