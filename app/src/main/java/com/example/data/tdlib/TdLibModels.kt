package com.example.data.tdlib

sealed interface TdAuthUiState {
    data object Initializing : TdAuthUiState
    data class MissingCredentials(val message: String) : TdAuthUiState
    data object WaitPhoneNumber : TdAuthUiState
    data class WaitCode(
        val phoneNumber: String,
        val codeLength: Int = 5,
        val deliveryType: String = "Telegram"
    ) : TdAuthUiState
    data class WaitPassword(val passwordHint: String) : TdAuthUiState
    data object Ready : TdAuthUiState
    data object LoggingOut : TdAuthUiState
}

data class TelegramUserProfile(
    val id: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val phoneNumber: String = ""
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
            if (username.isNotBlank()) "@$username" else "Pengguna Telegram"
        }
}

data class TelegramChatUi(
    val chatId: Long,
    val title: String,
    val username: String? = null,
    val chatType: String = "private",
    val lastMessagePreview: String = "",
    val lastMessageTimeSec: Long = 0L,
    val unreadCount: Int = 0,
    val order: Long = 0L,
    val isPinned: Boolean = false,
    val rawJson: String = "{}"
)

data class TelegramMessageUi(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val senderUsername: String? = null,
    val text: String,
    val isOutgoing: Boolean,
    val isJsonAutoReply: Boolean = false,
    val rawJson: String,
    val timestampSec: Long,
    val localPhotoPath: String? = null
)

data class TdJsonLogEntry(
    val id: Long,
    val eventType: String,
    val isSuccess: Boolean,
    val rawJson: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PhoneLookupState(
    val chatId: Long = 0L,
    val isPanelExpanded: Boolean = false,
    val queryInput: String = "",
    val targetName: String = "",
    val phoneSuffix: String = "",
    val isWaitingForReply: Boolean = false,
    val baselineIncomingIds: Set<Long> = emptySet(),
    val matches: List<com.example.util.PhoneLookupMatch> = emptyList(),
    val statusMessage: String? = null
)
