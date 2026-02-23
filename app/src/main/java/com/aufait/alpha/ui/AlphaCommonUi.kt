package com.aufait.alpha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aufait.alpha.R

@Composable
internal fun StatusPill(
    text: String,
    accent: Color
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun SheetSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusPillPreview() {
    EnFaitTheme {
        StatusPill(text = stringResource(R.string.preview_status_active), accent = Color(0xFF10B981))
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactAvatarPreview() {
    EnFaitTheme {
        ContactAvatar(name = "Alice Martin", modifier = Modifier.size(40.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SheetSectionHeaderPreview() {
    EnFaitTheme {
        SheetSectionHeader(stringResource(R.string.preview_section_contacts))
    }
}

@Composable
internal fun ContactAvatar(name: String, modifier: Modifier = Modifier) {
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
