package com.aufait.alpha.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aufait.alpha.ChatUiState
import com.aufait.alpha.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EnFaitChatTopBar(
    state: ChatUiState,
    onOpenSettings: () -> Unit
) {
    val connectionStateLabel = if (state.conversationActive) {
        stringResource(R.string.a11y_state_active)
    } else {
        stringResource(R.string.a11y_state_inactive)
    }
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
                        text = stringResource(R.string.app_name),
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
                contentDescription = stringResource(R.string.a11y_conversation_state_dot),
                tint = if (state.conversationActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(10.dp)
                    .semantics {
                        stateDescription = connectionStateLabel
                    }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.a11y_open_settings)
                )
            }
        }
    )
}

@Composable
internal fun ConversationSummaryCard(
    state: ChatUiState,
    onOpenSettings: () -> Unit,
    onShowIdentityQr: () -> Unit,
    onScanContactQr: () -> Unit
) {
    val summaryStateLabel = if (state.conversationActive) {
        stringResource(R.string.conversation_status_active)
    } else {
        stringResource(R.string.conversation_status_inactive)
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                    stateDescription = summaryStateLabel
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(name = state.peerAlias, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.peerAlias,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.transportStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { role = Role.Button },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.settings_configure)) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = summaryStateLabel,
                    accent = if (state.conversationActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                )
                StatusPill(
                    text = stringResource(R.string.conversation_lan_count, state.peers.size),
                    accent = MaterialTheme.colorScheme.secondary
                )
                StatusPill(
                    text = stringResource(R.string.conversation_contacts_count, state.contacts.size),
                    accent = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onShowIdentityQr,
                    modifier = Modifier.semantics { role = Role.Button }
                ) { Text(stringResource(R.string.action_qr_identity)) }
                TextButton(
                    onClick = onScanContactQr,
                    modifier = Modifier.semantics { role = Role.Button }
                ) { Text(stringResource(R.string.action_scan_qr)) }
            }
        }
    }
}

@Composable
internal fun ChatPanelHeader(state: ChatUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 1f
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.conversation_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.peerAlias,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusPill(
                text = if (state.conversationActive) {
                    stringResource(R.string.conversation_live)
                } else {
                    stringResource(R.string.conversation_out_of_focus)
                },
                accent = if (state.conversationActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.conversation_message_count, state.messages.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationSummaryCardPreview() {
    EnFaitTheme {
        ConversationSummaryCard(
            state = AlphaPreviewData.chatState(),
            onOpenSettings = {},
            onShowIdentityQr = {},
            onScanContactQr = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPanelHeaderPreview() {
    EnFaitTheme {
        Surface {
            ChatPanelHeader(state = AlphaPreviewData.chatState())
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopBarPreview() {
    EnFaitTheme {
        EnFaitChatTopBar(
            state = AlphaPreviewData.chatState(),
            onOpenSettings = {}
        )
    }
}
