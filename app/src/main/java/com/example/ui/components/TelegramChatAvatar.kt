package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

private val avatarPalettes = listOf(
    Color(0xFF0EA5E9) to Color(0xFF2563EB),
    Color(0xFF10B981) to Color(0xFF059669),
    Color(0xFFF59E0B) to Color(0xFFEA580C),
    Color(0xFF8B5CF6) to Color(0xFF6366F1),
    Color(0xFFEC4899) to Color(0xFFE11D48),
    Color(0xFF14B8A6) to Color(0xFF0284C7)
)

@Composable
fun TelegramChatAvatar(
    chatId: Long,
    title: String,
    chatType: String,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier
) {
    val palette = avatarPalettes[(chatId.absoluteValue % avatarPalettes.size).toInt()]
    val initials = title
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "TG" }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.first, palette.second))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }

        if (chatType != "private") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (chatType) {
                        "channel" -> Icons.Default.Campaign
                        "group", "supergroup" -> Icons.Default.Group
                        else -> Icons.Default.Person
                    },
                    contentDescription = chatType,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.24f)
                )
            }
        }
    }
}
