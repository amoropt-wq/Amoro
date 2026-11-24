package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: Long,
    val title: String,
    val username: String? = null,
    val chatType: String = "private",
    val lastMessagePreview: String = "",
    val lastMessageJson: String = "{}",
    val lastMessageTimeSec: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val telegramMessageId: Long,
    val chatId: Long,
    val updateId: Long? = null,
    val senderId: Long? = null,
    val senderName: String,
    val senderUsername: String? = null,
    val text: String,
    val isOutgoing: Boolean,
    val isJsonAutoReply: Boolean = false,
    val rawJson: String,
    val timestampSec: Long,
    val replyToMessageId: Long? = null,
    val localMediaUri: String? = null
)

@Entity(tableName = "api_logs")
data class ApiLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val method: String,
    val httpStatus: Int,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val rawJson: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "auto_reply_rules")
data class AutoReplyRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val triggerKeyword: String,
    val jsonTemplate: String,
    val isEnabled: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)
