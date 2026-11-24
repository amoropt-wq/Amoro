package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, lastMessageTimeSec DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    fun observeChat(chatId: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    suspend fun getChat(chatId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun clearUnread(chatId: Long)

    @Query("UPDATE chats SET isPinned = :pinned WHERE chatId = :chatId")
    suspend fun setPinned(chatId: Long, pinned: Boolean)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: Long)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestampSec ASC, id ASC")
    fun observeMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE chatId = :chatId AND telegramMessageId = :telegramMessageId)")
    suspend fun messageExists(chatId: Long, telegramMessageId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface ApiLogDao {
    @Query("SELECT * FROM api_logs ORDER BY timestampMs DESC, id DESC LIMIT 150")
    fun observeLogs(): Flow<List<ApiLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ApiLogEntity)

    @Query("DELETE FROM api_logs")
    suspend fun clearLogs()
}

@Dao
interface AutoReplyRuleDao {
    @Query("SELECT * FROM auto_reply_rules ORDER BY id ASC")
    fun observeRules(): Flow<List<AutoReplyRuleEntity>>

    @Query("SELECT * FROM auto_reply_rules WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun getEnabledRules(): List<AutoReplyRuleEntity>

    @Query("SELECT COUNT(*) FROM auto_reply_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutoReplyRuleEntity): Long

    @Update
    suspend fun updateRule(rule: AutoReplyRuleEntity)

    @Query("DELETE FROM auto_reply_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: Long)
}
