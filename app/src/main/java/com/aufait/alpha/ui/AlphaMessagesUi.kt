package com.aufait.alpha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aufait.alpha.R
import com.aufait.alpha.data.ChatMessage
import com.aufait.alpha.data.MessageTransportChannel
import com.aufait.alpha.data.MessageDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MessagesList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val todayLabel = stringResource(R.string.day_today)
    val yesterdayLabel = stringResource(R.string.day_yesterday)
    val rows = remember(messages, todayLabel, yesterdayLabel) { buildConversationRows(messages, todayLabel, yesterdayLabel) }

    LaunchedEffect(messages.lastOrNull()?.id, rows.size) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusPill(
                        text = stringResource(R.string.conversation_new_thread),
                        accent = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.conversation_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.conversation_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 1.1f
            },
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
    val a11yDateSeparator = stringResource(R.string.a11y_date_separator, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = CircleShape
                )
                .semantics {
                    heading()
                    contentDescription = a11yDateSeparator
                }
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
    val a11yTime = formatTime(message.timestampMs)
    val receipt = if (outbound) receiptVisual(message) else null
    val a11yLabel = if (outbound) {
        stringResource(
            R.string.a11y_message_outbound,
            a11yTime,
            message.body,
            receipt?.label.orEmpty()
        )
    } else {
        stringResource(
            R.string.a11y_message_inbound,
            message.author,
            a11yTime,
            message.body
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = a11yLabel
            }
    ) {
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
                Spacer(Modifier.size(8.dp))
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
                Spacer(Modifier.size(36.dp))
            }
            MessageMetaLine(message = message, outbound = outbound)
        }
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
            receipt.metaLine?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            message.transportChannel?.let { channel ->
                Text(
                    text = "• ${channelShortLabel(channel)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ReceiptVisual(
    val icon: ImageVector,
    val tint: Color,
    val label: String,
    val metaLine: String? = null
)

@Composable
private fun receiptVisual(message: ChatMessage): ReceiptVisual {
    val scheme = MaterialTheme.colorScheme
    val readMeta = listOfNotNull(
        message.readAtMs?.let(::formatTime),
        message.readChannel?.let { channelShortLabel(it) }
    ).joinToString(" • ").takeIf { it.isNotBlank() }
    val deliveredMeta = listOfNotNull(
        message.deliveredAtMs?.let(::formatTime),
        message.deliveredChannel?.let { channelShortLabel(it) }
    ).joinToString(" • ").takeIf { it.isNotBlank() }
    return when {
        message.readAtMs != null -> ReceiptVisual(Icons.Default.DoneAll, Color(0xFF60A5FA), stringResource(R.string.receipt_read), readMeta)
        message.deliveredAtMs != null -> ReceiptVisual(Icons.Default.DoneAll, scheme.onSurfaceVariant, stringResource(R.string.receipt_delivered), deliveredMeta)
        else -> ReceiptVisual(
            Icons.Default.Done,
            scheme.onSurfaceVariant,
            stringResource(R.string.receipt_sent),
            message.transportChannel?.let { channelShortLabel(it) }
        )
    }
}

@Composable
private fun channelShortLabel(channel: MessageTransportChannel): String {
    return when (channel) {
        MessageTransportChannel.LOCAL -> stringResource(R.string.channel_local)
        MessageTransportChannel.WIFI -> stringResource(R.string.channel_wifi)
        MessageTransportChannel.BLUETOOTH -> stringResource(R.string.channel_bluetooth)
        MessageTransportChannel.RELAY -> stringResource(R.string.channel_relay)
        MessageTransportChannel.TOR -> stringResource(R.string.channel_tor)
    }
}

private fun formatTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

private sealed class ConversationRow(val key: String) {
    class DateSeparator(val dayStartMs: Long, val label: String) : ConversationRow("d-$dayStartMs")
    class Message(val message: ChatMessage) : ConversationRow("m-${message.id}")
}

private fun buildConversationRows(
    messages: List<ChatMessage>,
    todayLabel: String,
    yesterdayLabel: String
): List<ConversationRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = mutableListOf<ConversationRow>()
    var previousDayStart: Long? = null
    messages.forEach { msg ->
        val dayStart = startOfDayMs(msg.timestampMs)
        if (previousDayStart != dayStart) {
            rows += ConversationRow.DateSeparator(dayStart, formatDaySeparator(dayStart, todayLabel, yesterdayLabel))
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

private fun formatDaySeparator(dayStartMs: Long, todayLabel: String, yesterdayLabel: String): String {
    val today = startOfDayMs(System.currentTimeMillis())
    val oneDay = 24L * 60L * 60L * 1000L
    return when (dayStartMs) {
        today -> todayLabel
        today - oneDay -> yesterdayLabel
        else -> {
            val formatter = SimpleDateFormat("dd MMM", Locale.FRENCH)
            formatter.format(Date(dayStartMs))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessagesListPreview() {
    EnFaitTheme {
        Surface {
            MessagesList(
                messages = AlphaPreviewData.messages(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyMessagesPreview() {
    EnFaitTheme {
        Surface {
            MessagesList(
                messages = emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp)
            )
        }
    }
}
