package com.example.data.tdlib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import com.example.BuildConfig
import com.example.data.local.AutoReplyRuleDao
import com.example.data.local.AutoReplyRuleEntity
import com.example.data.local.UserPreferencesRepository
import com.example.util.JsonUtils
import com.example.util.TdJsonSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TdLibManager(
    private val context: Context,
    private val prefs: UserPreferencesRepository,
    private val ruleDao: AutoReplyRuleDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<TdAuthUiState>(TdAuthUiState.Initializing)
    val authState: StateFlow<TdAuthUiState> = _authState.asStateFlow()

    private val _me = MutableStateFlow(TelegramUserProfile())
    val me: StateFlow<TelegramUserProfile> = _me.asStateFlow()

    private val chatsMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val usersMap = ConcurrentHashMap<Long, TdApi.User>()
    private val messagesByChat = ConcurrentHashMap<Long, MutableMap<Long, TelegramMessageUi>>()

    private val _chatsFlow = MutableStateFlow<List<TelegramChatUi>>(emptyList())
    val chatsFlow: StateFlow<List<TelegramChatUi>> = _chatsFlow.asStateFlow()

    private val _messagesTick = MutableStateFlow<Map<Long, List<TelegramMessageUi>>>(emptyMap())

    private val _jsonLogs = MutableStateFlow<List<TdJsonLogEntry>>(emptyList())
    val jsonLogs: StateFlow<List<TdJsonLogEntry>> = _jsonLogs.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val logSeq = AtomicLong(1L)
    private var client: Client? = null
    private var lastPhoneNumber: String = ""

    init {
        scope.launch { ensureDefaultRules() }
        startClientIfConfigured()
    }

    private suspend fun ensureDefaultRules() {
        if (ruleDao.getRuleCount() == 0) {
            ruleDao.insertRule(
                AutoReplyRuleEntity(
                    triggerKeyword = "/info",
                    jsonTemplate = """
                        {
                          "status": "ok",
                          "client": "TeleCraft TDLib",
                          "from": "@{{username}}",
                          "chat_id": {{chat_id}},
                          "message_id": {{message_id}},
                          "text": "{{text}}",
                          "timestamp": {{date}}
                        }
                    """.trimIndent(),
                    isEnabled = true
                )
            )
            ruleDao.insertRule(
                AutoReplyRuleEntity(
                    triggerKeyword = "*",
                    jsonTemplate = """
                        {
                          "reply_type": "telegram_json",
                          "echo": "{{text}}",
                          "sender": "{{first_name}}",
                          "chat_id": {{chat_id}},
                          "timestamp": {{date}}
                        }
                    """.trimIndent(),
                    isEnabled = true
                )
            )
        }
    }

    fun startClientIfConfigured() {
        val apiId = BuildConfig.TELEGRAM_API_ID.trim().toIntOrNull() ?: 0
        val apiHash = BuildConfig.TELEGRAM_API_HASH.trim()

        if (apiId <= 0 || apiHash.isBlank() || apiHash == "MY_TELEGRAM_API_HASH") {
            _authState.value = TdAuthUiState.MissingCredentials(
                "Tambahkan TELEGRAM_API_ID dan TELEGRAM_API_HASH Anda pada panel Secrets AI Studio lalu bangun ulang aplikasi."
            )
            return
        }

        if (client != null) return

        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            val updateHandler = Client.ResultHandler { obj ->
                if (obj != null) handleUpdate(obj)
            }
            val exceptionHandler = Client.ExceptionHandler { e ->
                appendJsonLog("TdLibException", false, """{"error": "${e.message ?: "Unknown"}"}""")
            }
            client = Client.create(updateHandler, exceptionHandler, exceptionHandler)
        } catch (e: Throwable) {
            _authState.value = TdAuthUiState.MissingCredentials(
                "Gagal memuat native TDLib: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun observeChat(chatId: Long): Flow<TelegramChatUi?> =
        _chatsFlow.map { list -> list.firstOrNull { it.chatId == chatId } }

    fun observeMessages(chatId: Long): Flow<List<TelegramMessageUi>> =
        _messagesTick.map { map -> map[chatId].orEmpty() }

    fun getCurrentMessages(chatId: Long): List<TelegramMessageUi> =
        _messagesTick.value[chatId].orEmpty()

    fun submitPhoneNumber(phone: String) {
        val trimmed = phone.trim()
        if (trimmed.isBlank()) {
            _events.tryEmit("Masukkan nomor telepon Telegram dengan kode negara (mis. +62812...)")
            return
        }
        lastPhoneNumber = trimmed
        _isBusy.value = true
        val req = TdApi.SetAuthenticationPhoneNumber().apply {
            phoneNumber = trimmed
        }
        send(req, "SetAuthenticationPhoneNumber") { res ->
            _isBusy.value = false
            if (res is TdApi.Error) {
                _events.tryEmit("Gagal mengirim OTP: ${res.message}")
            }
        }
    }

    fun submitAuthCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            _events.tryEmit("Masukkan kode OTP dari Telegram.")
            return
        }
        _isBusy.value = true
        val req = TdApi.CheckAuthenticationCode().apply {
            this.code = trimmed
        }
        send(req, "CheckAuthenticationCode") { res ->
            _isBusy.value = false
            if (res is TdApi.Error) {
                _events.tryEmit("Kode OTP salah atau kadaluarsa: ${res.message}")
            }
        }
    }

    fun resendAuthCode() {
        _isBusy.value = true
        send(TdApi.ResendAuthenticationCode(), "ResendAuthenticationCode") { res ->
            _isBusy.value = false
            if (res is TdApi.Error) {
                _events.tryEmit("Gagal kirim ulang kode: ${res.message}")
            } else {
                _events.tryEmit("Permintaan kirim ulang kode dikirim ke Telegram")
            }
        }
    }

    fun submitPassword(password: String) {
        if (password.isBlank()) {
            _events.tryEmit("Masukkan kata sandi verifikasi dua langkah (2FA).")
            return
        }
        _isBusy.value = true
        val req = TdApi.CheckAuthenticationPassword().apply {
            this.password = password
        }
        send(req, "CheckAuthenticationPassword") { res ->
            _isBusy.value = false
            if (res is TdApi.Error) {
                _events.tryEmit("Kata sandi 2FA salah: ${res.message}")
            }
        }
    }

    fun loadChats(limit: Int = 50) {
        _isBusy.value = true
        val req = TdApi.LoadChats().apply {
            chatList = TdApi.ChatListMain()
            this.limit = limit
        }
        send(req, "LoadChats") { res ->
            _isBusy.value = false
            if (res is TdApi.Error && res.code != 404) {
                _events.tryEmit("Gagal memuat daftar chat: ${res.message}")
            }
        }
    }

    fun loadChatHistory(chatId: Long) {
        val req = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            fromMessageId = 0L
            offset = 0
            limit = 50
            onlyLocal = false
        }
        send(req, "GetChatHistory") { res ->
            if (res is TdApi.Messages) {
                val map = messagesByChat.getOrPut(chatId) { ConcurrentHashMap() }
                res.messages.forEach { msg ->
                    val ui = mapTdMessage(msg)
                    map[ui.id] = ui
                }
                publishMessages(chatId)
            } else if (res is TdApi.Error) {
                _events.tryEmit("Gagal memuat riwayat: ${res.message}")
            }
        }
    }

    fun sendMessage(chatId: Long, text: String, sendAsJsonBlock: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val finalText = if (sendAsJsonBlock) JsonUtils.formatPrettyJson(trimmed) else trimmed
        val entities = if (sendAsJsonBlock) {
            arrayOf(
                TdApi.TextEntity().apply {
                    offset = 0
                    length = finalText.length
                    type = TdApi.TextEntityTypePreCode("json")
                }
            )
        } else {
            emptyArray()
        }

        val content = TdApi.InputMessageText().apply {
            this.text = TdApi.FormattedText().apply {
                this.text = finalText
                this.entities = entities
            }
        }

        val req = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }

        send(req, "SendMessage") { res ->
            if (res is TdApi.Message) {
                val ui = mapTdMessage(res)
                messagesByChat.getOrPut(chatId) { ConcurrentHashMap() }[ui.id] = ui
                publishMessages(chatId)
            } else if (res is TdApi.Error) {
                _events.tryEmit("Gagal mengirim pesan: ${res.message}")
            }
        }
    }

    fun sendPhoto(chatId: Long, photoUri: Uri, caption: String?) {
        scope.launch {
            try {
                val mimeType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                val tempFile = File(context.cacheDir, "tg_upload_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(photoUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    _events.tryEmit("Tidak dapat membaca gambar terpilih.")
                    return@launch
                }

                val content = TdApi.InputMessagePhoto().apply {
                    photo = TdApi.InputFileLocal().apply { path = tempFile.absolutePath }
                    if (!caption.isNullOrBlank()) {
                        this.caption = TdApi.FormattedText().apply {
                            text = caption
                            entities = emptyArray()
                        }
                    }
                }
                val req = TdApi.SendMessage().apply {
                    this.chatId = chatId
                    this.inputMessageContent = content
                }
                send(req, "SendMessage(Photo)") { res ->
                    if (res is TdApi.Message) {
                        val ui = mapTdMessage(res)
                        messagesByChat.getOrPut(chatId) { ConcurrentHashMap() }[ui.id] = ui
                        publishMessages(chatId)
                    } else if (res is TdApi.Error) {
                        _events.tryEmit("Gagal mengirim foto: ${res.message}")
                    }
                }
            } catch (e: Exception) {
                _events.tryEmit("Gagal memproses foto: ${e.localizedMessage}")
            }
        }
    }

    fun openPublicChatOrId(target: String, onOpened: (Long) -> Unit) {
        val cleaned = target.trim().removePrefix("@")
        val numericId = cleaned.toLongOrNull()
        if (numericId != null) {
            val req = TdApi.GetChat().apply { chatId = numericId }
            send(req, "GetChat") { res ->
                if (res is TdApi.Chat) {
                    chatsMap[res.id] = res
                    publishChats()
                    onOpened(res.id)
                } else if (res is TdApi.Error) {
                    _events.tryEmit("Chat tidak ditemukan: ${res.message}")
                }
            }
        } else if (cleaned.isNotBlank()) {
            val req = TdApi.SearchPublicChat().apply { username = cleaned }
            send(req, "SearchPublicChat") { res ->
                if (res is TdApi.Chat) {
                    chatsMap[res.id] = res
                    publishChats()
                    onOpened(res.id)
                } else if (res is TdApi.Error) {
                    _events.tryEmit("@$cleaned tidak ditemukan: ${res.message}")
                }
            }
        }
    }

    fun markChatOpened(chatId: Long) {
        val chat = chatsMap[chatId]
        val lastId = chat?.lastMessage?.id ?: return
        val req = TdApi.ViewMessages().apply {
            this.chatId = chatId
            messageIds = longArrayOf(lastId)
            forceRead = true
        }
        send(req, "ViewMessages")
    }

    fun logOut() {
        _authState.value = TdAuthUiState.LoggingOut
        send(TdApi.LogOut(), "LogOut")
    }

    fun clearLogs() {
        _jsonLogs.value = emptyList()
    }

    private fun send(
        function: TdApi.Function<*>,
        methodName: String,
        onResult: ((TdApi.Object) -> Unit)? = null
    ) {
        val activeClient = client ?: return
        activeClient.send(function) { result ->
            if (result != null) {
                val isOk = result !is TdApi.Error
                if (methodName != "ViewMessages") {
                    appendJsonLog(
                        eventType = "$methodName -> ${result.javaClass.simpleName}",
                        isSuccess = isOk,
                        rawJson = TdJsonSerializer.toPrettyJson(result)
                    )
                }
                onResult?.invoke(result)
            }
        }
    }

    private fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthState(obj.authorizationState)
            }
            is TdApi.UpdateUser -> {
                usersMap[obj.user.id] = obj.user
                val currentMe = _me.value
                if (currentMe.id == obj.user.id) {
                    updateMeFromUser(obj.user)
                }
            }
            is TdApi.UpdateNewChat -> {
                chatsMap[obj.chat.id] = obj.chat
                publishChats()
            }
            is TdApi.UpdateChatTitle -> {
                chatsMap[obj.chatId]?.let { chat ->
                    chat.title = obj.title
                    publishChats()
                }
            }
            is TdApi.UpdateChatLastMessage -> {
                chatsMap[obj.chatId]?.let { chat ->
                    chat.lastMessage = obj.lastMessage
                    obj.positions.forEach { pos ->
                        if (pos.list is TdApi.ChatListMain) {
                            chat.positions = obj.positions
                        }
                    }
                    publishChats()
                }
            }
            is TdApi.UpdateChatPosition -> {
                chatsMap[obj.chatId]?.let { chat ->
                    if (obj.position.list is TdApi.ChatListMain) {
                        val updated = chat.positions.filterNot { it.list is TdApi.ChatListMain }.toMutableList()
                        updated.add(obj.position)
                        chat.positions = updated.toTypedArray()
                        publishChats()
                    }
                }
            }
            is TdApi.UpdateChatReadInbox -> {
                chatsMap[obj.chatId]?.let { chat ->
                    chat.unreadCount = obj.unreadCount
                    publishChats()
                }
            }
            is TdApi.UpdateNewMessage -> {
                val msg = obj.message
                val uiMsg = mapTdMessage(msg, updateWrapper = obj)
                messagesByChat.getOrPut(msg.chatId) { ConcurrentHashMap() }[uiMsg.id] = uiMsg
                publishMessages(msg.chatId)

                appendJsonLog(
                    eventType = "UpdateNewMessage (chat ${msg.chatId})",
                    isSuccess = true,
                    rawJson = uiMsg.rawJson
                )

                if (!msg.isOutgoing) {
                    scope.launch { maybeSendAutoReplyJson(msg, uiMsg) }
                }
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                val msg = obj.message
                val map = messagesByChat.getOrPut(msg.chatId) { ConcurrentHashMap() }
                map.remove(obj.oldMessageId)
                val uiMsg = mapTdMessage(msg)
                map[uiMsg.id] = uiMsg
                publishMessages(msg.chatId)
            }
            is TdApi.UpdateMessageContent -> {
                val map = messagesByChat[obj.chatId]
                val existing = map?.get(obj.messageId)
                if (existing != null) {
                    loadChatHistory(obj.chatId)
                }
            }
            is TdApi.UpdateDeleteMessages -> {
                if (obj.isPermanent || obj.fromCache.not()) {
                    val map = messagesByChat[obj.chatId]
                    if (map != null) {
                        obj.messageIds.forEach { map.remove(it) }
                        publishMessages(obj.chatId)
                    }
                }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val apiIdVal = BuildConfig.TELEGRAM_API_ID.trim().toIntOrNull() ?: 0
                val apiHashVal = BuildConfig.TELEGRAM_API_HASH.trim()
                val params = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = File(context.filesDir, "tdlib_db").absolutePath
                    filesDirectory = File(context.filesDir, "tdlib_files").absolutePath
                    databaseEncryptionKey = byteArrayOf()
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = apiIdVal
                    apiHash = apiHashVal
                    systemLanguageCode = "id"
                    deviceModel = Build.MODEL
                    systemVersion = Build.VERSION.RELEASE
                    applicationVersion = "1.0"
                }
                send(params, "SetTdlibParameters") { res ->
                    if (res is TdApi.Error) {
                        _authState.value = TdAuthUiState.MissingCredentials("TDLib error: ${res.message}")
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _isBusy.value = false
                _authState.value = TdAuthUiState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _isBusy.value = false
                val len = when (val t = state.codeInfo.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> t.length
                    is TdApi.AuthenticationCodeTypeSms -> t.length
                    is TdApi.AuthenticationCodeTypeFragment -> t.length
                    else -> 5
                }
                val delivery = when (state.codeInfo.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> "Aplikasi Telegram"
                    is TdApi.AuthenticationCodeTypeSms -> "SMS"
                    is TdApi.AuthenticationCodeTypeCall -> "Panggilan Telepon"
                    else -> "Telegram"
                }
                _authState.value = TdAuthUiState.WaitCode(
                    phoneNumber = state.codeInfo.phoneNumber.ifBlank { lastPhoneNumber },
                    codeLength = len,
                    deliveryType = delivery
                )
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _isBusy.value = false
                _authState.value = TdAuthUiState.WaitPassword(state.passwordHint.orEmpty())
            }
            is TdApi.AuthorizationStateReady -> {
                _isBusy.value = false
                _authState.value = TdAuthUiState.Ready
                send(TdApi.GetMe(), "GetMe") { res ->
                    if (res is TdApi.User) {
                        usersMap[res.id] = res
                        updateMeFromUser(res)
                    }
                }
                loadChats(50)
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = TdAuthUiState.LoggingOut
            }
            is TdApi.AuthorizationStateClosed -> {
                chatsMap.clear()
                messagesByChat.clear()
                _chatsFlow.value = emptyList()
                _messagesTick.value = emptyMap()
                client = null
                _authState.value = TdAuthUiState.Initializing
                startClientIfConfigured()
            }
        }
    }

    private fun updateMeFromUser(user: TdApi.User) {
        val uname = user.usernames?.activeUsernames?.firstOrNull().orEmpty()
        _me.value = TelegramUserProfile(
            id = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = uname,
            phoneNumber = user.phoneNumber
        )
    }

    private suspend fun maybeSendAutoReplyJson(msg: TdApi.Message, uiMsg: TelegramMessageUi) {
        val settings = prefs.uiSettingsFlow.first()
        if (!settings.autoReplyJsonEnabled) return
        // Only auto-reply in 1-on-1 private chats (never spam groups/channels!)
        val chat = chatsMap[msg.chatId] ?: return
        if (chat.type !is TdApi.ChatTypePrivate) return
        if (uiMsg.text.isBlank()) return

        val rules = ruleDao.getEnabledRules()
        if (rules.isEmpty()) return

        val matchedRule = rules.firstOrNull { rule ->
            rule.triggerKeyword != "*" && uiMsg.text.contains(rule.triggerKeyword, ignoreCase = true)
        } ?: rules.firstOrNull { it.triggerKeyword == "*" } ?: return

        val renderedJson = JsonUtils.renderAutoReplyJson(
            template = matchedRule.jsonTemplate,
            text = uiMsg.text,
            chatId = msg.chatId,
            username = uiMsg.senderUsername,
            firstName = uiMsg.senderName,
            messageId = msg.id,
            dateSec = msg.date.toLong(),
            botUsername = _me.value.username.ifBlank { _me.value.displayName }
        )

        val content = TdApi.InputMessageText().apply {
            text = TdApi.FormattedText().apply {
                text = renderedJson
                entities = arrayOf(
                    TdApi.TextEntity().apply {
                        offset = 0
                        length = renderedJson.length
                        type = TdApi.TextEntityTypePreCode("json")
                    }
                )
            }
        }
        val req = TdApi.SendMessage().apply {
            chatId = msg.chatId
            inputMessageContent = content
        }
        send(req, "SendMessage(AutoJSON)")
    }

    private fun publishChats() {
        val list = chatsMap.values.map { chat ->
            val mainPos = chat.positions.firstOrNull { it.list is TdApi.ChatListMain }
            val order = mainPos?.order ?: 0L
            val isPinned = mainPos?.isPinned ?: false
            val typeStr = when (chat.type) {
                is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> "private"
                is TdApi.ChatTypeBasicGroup -> "group"
                is TdApi.ChatTypeSupergroup -> if ((chat.type as TdApi.ChatTypeSupergroup).isChannel) "channel" else "supergroup"
                else -> "private"
            }
            val lastMsg = chat.lastMessage
            val preview = if (lastMsg != null) extractMessageText(lastMsg.content) else ""
            val rawJson = if (lastMsg != null) TdJsonSerializer.toPrettyJson(lastMsg) else TdJsonSerializer.toPrettyJson(chat)

            TelegramChatUi(
                chatId = chat.id,
                title = chat.title.ifBlank { "Chat ${chat.id}" },
                username = resolveChatUsername(chat),
                chatType = typeStr,
                lastMessagePreview = preview,
                lastMessageTimeSec = lastMsg?.date?.toLong() ?: 0L,
                unreadCount = chat.unreadCount,
                order = order,
                isPinned = isPinned,
                rawJson = rawJson
            )
        }.sortedWith(compareByDescending<TelegramChatUi> { it.isPinned }.thenByDescending { it.order }.thenByDescending { it.lastMessageTimeSec })

        _chatsFlow.value = list
    }

    private fun publishMessages(chatId: Long) {
        val list = messagesByChat[chatId]?.values
            ?.sortedWith(compareBy<TelegramMessageUi> { it.timestampSec }.thenBy { it.id })
            ?: emptyList()
        val next = _messagesTick.value.toMutableMap()
        next[chatId] = list
        _messagesTick.value = next
    }

    private fun mapTdMessage(
        msg: TdApi.Message,
        updateWrapper: TdApi.Object? = null
    ): TelegramMessageUi {
        var senderName = "Telegram"
        var senderUsername: String? = null
        when (val sender = msg.senderId) {
            is TdApi.MessageSenderUser -> {
                val user = usersMap[sender.userId]
                if (user != null) {
                    senderName = listOf(user.firstName, user.lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { user.usernames?.activeUsernames?.firstOrNull() ?: "User ${sender.userId}" }
                    senderUsername = user.usernames?.activeUsernames?.firstOrNull()
                } else {
                    senderName = "User ${sender.userId}"
                }
            }
            is TdApi.MessageSenderChat -> {
                senderName = chatsMap[sender.chatId]?.title ?: "Chat ${sender.chatId}"
            }
        }

        val text = extractMessageText(msg.content)
        val isJsonBlock = (msg.content as? TdApi.MessageText)?.text?.entities
            ?.any { it.type is TdApi.TextEntityTypePre || it.type is TdApi.TextEntityTypeCode } == true
        val localPhoto = extractLocalPhotoPath(msg.content)
        val json = TdJsonSerializer.toPrettyJson(updateWrapper ?: msg)

        return TelegramMessageUi(
            id = msg.id,
            chatId = msg.chatId,
            senderName = senderName,
            senderUsername = senderUsername,
            text = text,
            isOutgoing = msg.isOutgoing,
            isJsonAutoReply = isJsonBlock,
            rawJson = json,
            timestampSec = msg.date.toLong(),
            localPhotoPath = localPhoto
        )
    }

    private fun extractMessageText(content: TdApi.MessageContent?): String {
        return when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> {
                val cap = content.caption.text
                if (cap.isNotBlank()) "[Foto] $cap" else "[Foto]"
            }
            is TdApi.MessageVideo -> {
                val cap = content.caption.text
                if (cap.isNotBlank()) "[Video] $cap" else "[Video]"
            }
            is TdApi.MessageDocument -> "[Dokumen: ${content.document.fileName}]"
            is TdApi.MessageSticker -> "[Stiker ${content.sticker.emoji}]"
            is TdApi.MessageVoiceNote -> "[Pesan Suara]"
            is TdApi.MessageAudio -> "[Audio: ${content.audio.title}]"
            is TdApi.MessageAnimatedEmoji -> content.emoji
            null -> ""
            else -> "[${content.javaClass.simpleName}]"
        }
    }

    private fun extractLocalPhotoPath(content: TdApi.MessageContent?): String? {
        val photo = (content as? TdApi.MessagePhoto)?.photo ?: return null
        val downloaded = photo.sizes.lastOrNull()?.photo?.local
        return if (downloaded != null && downloaded.isDownloadingCompleted && downloaded.path.isNotBlank()) {
            downloaded.path
        } else {
            null
        }
    }

    private fun resolveChatUsername(chat: TdApi.Chat): String? {
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> usersMap[type.userId]?.usernames?.activeUsernames?.firstOrNull()
            else -> null
        }
    }

    private fun appendJsonLog(eventType: String, isSuccess: Boolean, rawJson: String) {
        val entry = TdJsonLogEntry(
            id = logSeq.getAndIncrement(),
            eventType = eventType,
            isSuccess = isSuccess,
            rawJson = rawJson
        )
        val current = _jsonLogs.value
        _jsonLogs.value = (listOf(entry) + current).take(120)
    }
}
