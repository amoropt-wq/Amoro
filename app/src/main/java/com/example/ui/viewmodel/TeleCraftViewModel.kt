package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AutoReplyRuleDao
import com.example.data.local.AutoReplyRuleEntity
import com.example.data.local.TeleCraftDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.tdlib.PhoneLookupState
import com.example.data.tdlib.TdAuthUiState
import com.example.data.tdlib.TdJsonLogEntry
import com.example.data.tdlib.TdLibManager
import com.example.data.tdlib.TelegramChatUi
import com.example.data.tdlib.TelegramMessageUi
import com.example.data.tdlib.TelegramUserProfile
import com.example.model.AccentTheme
import com.example.model.CustomUiSettings
import com.example.model.JsonDisplayMode
import com.example.model.JsonProjectionMode
import com.example.model.JsonSyntaxTheme
import com.example.model.ThemeMode
import com.example.model.WallpaperStyle
import com.example.util.JsonPhoneFinder
import com.example.util.JsonUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class TeleCraftViewModel(
    private val tdLib: TdLibManager,
    private val prefs: UserPreferencesRepository,
    private val ruleDao: AutoReplyRuleDao
) : ViewModel() {

    val authState: StateFlow<TdAuthUiState> = tdLib.authState
    val me: StateFlow<TelegramUserProfile> = tdLib.me
    val chats: StateFlow<List<TelegramChatUi>> = tdLib.chatsFlow
    val jsonLogs: StateFlow<List<TdJsonLogEntry>> = tdLib.jsonLogs
    val isBusy: StateFlow<Boolean> = tdLib.isBusy

    val uiSettings: StateFlow<CustomUiSettings> = prefs.uiSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomUiSettings())

    val autoReplyRules: StateFlow<List<AutoReplyRuleEntity>> = ruleDao.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localToast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: Flow<String> = merge(tdLib.events, _localToast.asSharedFlow())

    private val _phoneLookupStates = MutableStateFlow<Map<Long, PhoneLookupState>>(emptyMap())
    private val lookupWatchJobs = mutableMapOf<Long, Job>()

    fun retryInit() = tdLib.startClientIfConfigured()
    fun submitPhoneNumber(phone: String) = tdLib.submitPhoneNumber(phone)
    fun submitAuthCode(code: String) = tdLib.submitAuthCode(code)
    fun resendAuthCode() = tdLib.resendAuthCode()
    fun submitPassword(password: String) = tdLib.submitPassword(password)
    fun refreshChats() = tdLib.loadChats(50)
    fun loadChatHistory(chatId: Long) = tdLib.loadChatHistory(chatId)
    fun markChatOpened(chatId: Long) = tdLib.markChatOpened(chatId)

    fun observeChat(chatId: Long): Flow<TelegramChatUi?> = tdLib.observeChat(chatId)
    fun observeMessages(chatId: Long): Flow<List<TelegramMessageUi>> = tdLib.observeMessages(chatId)

    fun observePhoneLookup(chatId: Long): Flow<PhoneLookupState> =
        _phoneLookupStates.map { map -> map[chatId] ?: PhoneLookupState(chatId = chatId) }

    fun togglePhoneLookupPanel(chatId: Long, expanded: Boolean? = null) {
        updateLookupState(chatId) { current ->
            current.copy(isPanelExpanded = expanded ?: !current.isPanelExpanded)
        }
    }

    fun updatePhoneLookupQuery(chatId: Long, rawQuery: String) {
        val parsed = JsonPhoneFinder.parseQuery(rawQuery)
        updateLookupState(chatId) { current ->
            current.copy(
                queryInput = rawQuery,
                targetName = parsed.nameQuery,
                phoneSuffix = parsed.phoneSuffix
            )
        }
    }

    fun updatePhoneLookupFilter(chatId: Long, targetName: String, phoneSuffix: String) {
        val digitsOnly = JsonPhoneFinder.normalizeDigits(phoneSuffix)
        updateLookupState(chatId) { current ->
            val composed = listOf(targetName.trim(), digitsOnly)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            current.copy(
                queryInput = if (current.queryInput.isBlank() || composed.isNotBlank()) composed else current.queryInput,
                targetName = targetName,
                phoneSuffix = digitsOnly
            )
        }
    }

    fun clearPhoneLookupResults(chatId: Long) {
        lookupWatchJobs.remove(chatId)?.cancel()
        updateLookupState(chatId) { current ->
            current.copy(
                isWaitingForReply = false,
                baselineIncomingIds = emptySet(),
                matches = emptyList(),
                statusMessage = null
            )
        }
    }

    fun sendAndFindPhone(
        chatId: Long,
        currentMessages: List<TelegramMessageUi>,
        sendAsJsonBlock: Boolean
    ) {
        val current = _phoneLookupStates.value[chatId] ?: PhoneLookupState(chatId = chatId)
        val messageToSend = current.queryInput.trim().ifBlank {
            listOf(current.targetName.trim(), current.phoneSuffix.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
        if (messageToSend.isBlank()) {
            _localToast.tryEmit("Masukkan nama & akhiran nomor (mis. DANNY SUGIHARTO 72).")
            return
        }

        val parsed = JsonPhoneFinder.parseQuery(messageToSend)
        val effectiveName = current.targetName.trim().ifBlank { parsed.nameQuery }
        val effectiveSuffix = JsonPhoneFinder.normalizeDigits(
            current.phoneSuffix.trim().ifBlank { parsed.phoneSuffix }
        )

        if (effectiveSuffix.isBlank()) {
            _localToast.tryEmit("Akhiran nomor wajib diisi (contoh: DANNY SUGIHARTO 72). Jika hanya nama tidak ditampilkan.")
            return
        }

        val knownIncomingIds = currentMessages.asSequence()
            .filter { !it.isOutgoing }
            .map { it.id }
            .toSet()

        updateLookupState(chatId) { state ->
            state.copy(
                isPanelExpanded = true,
                queryInput = messageToSend,
                targetName = effectiveName,
                phoneSuffix = effectiveSuffix,
                isWaitingForReply = true,
                baselineIncomingIds = knownIncomingIds,
                matches = emptyList(),
                statusMessage = "Pesan \"$messageToSend\" dikirim. Menunggu balasan Telegram..."
            )
        }

        tdLib.sendMessage(chatId, messageToSend, sendAsJsonBlock)
        startWatchingIncomingReplies(chatId)
    }

    fun scanExistingMessagesForPhone(
        chatId: Long,
        messages: List<TelegramMessageUi>
    ) {
        val current = _phoneLookupStates.value[chatId] ?: PhoneLookupState(chatId = chatId)
        val parsed = JsonPhoneFinder.parseQuery(current.queryInput)
        val effectiveName = current.targetName.trim().ifBlank { parsed.nameQuery }
        val effectiveSuffix = JsonPhoneFinder.normalizeDigits(
            current.phoneSuffix.trim().ifBlank { parsed.phoneSuffix }
        )

        if (effectiveSuffix.isBlank()) {
            updateLookupState(chatId) { state ->
                state.copy(
                    isPanelExpanded = true,
                    isWaitingForReply = false,
                    matches = emptyList(),
                    statusMessage = "Masukkan akhiran nomor (contoh: DANNY SUGIHARTO 72). Jika hanya nama tidak ditampilkan."
                )
            }
            return
        }

        val found = JsonPhoneFinder.searchInMessages(
            messages = messages,
            nameQuery = effectiveName,
            phoneSuffix = effectiveSuffix,
            excludedMessageIds = emptySet(),
            incomingOnly = false
        )

        val status = if (found.isNotEmpty()) {
            "Ditemukan ${found.size} data cocok di riwayat chat."
        } else {
            "Tidak ditemukan \"$effectiveName\" dengan akhiran \"$effectiveSuffix\"."
        }

        updateLookupState(chatId) { state ->
            state.copy(
                isPanelExpanded = true,
                targetName = effectiveName,
                phoneSuffix = effectiveSuffix,
                isWaitingForReply = false,
                matches = found,
                statusMessage = status
            )
        }
    }

    private fun startWatchingIncomingReplies(chatId: Long) {
        lookupWatchJobs.remove(chatId)?.cancel()
        lookupWatchJobs[chatId] = viewModelScope.launch {
            tdLib.observeMessages(chatId).collect { messages ->
                val state = _phoneLookupStates.value[chatId] ?: return@collect
                if (!state.isWaitingForReply) return@collect
                if (state.phoneSuffix.isBlank()) return@collect

                val newIncoming = messages.filter { !it.isOutgoing && it.id !in state.baselineIncomingIds }
                if (newIncoming.isEmpty()) return@collect

                val matches = JsonPhoneFinder.searchInMessages(
                    messages = newIncoming,
                    nameQuery = state.targetName,
                    phoneSuffix = state.phoneSuffix,
                    excludedMessageIds = emptySet(),
                    incomingOnly = true
                )

                if (matches.isNotEmpty()) {
                    updateLookupState(chatId) { cur ->
                        cur.copy(
                            isPanelExpanded = true,
                            isWaitingForReply = false,
                            matches = matches,
                            statusMessage = "Ketemu ${matches.size} hasil cocok (...${cur.phoneSuffix})!"
                        )
                    }
                    _localToast.tryEmit("Nomor ditemukan: ${matches.first().phoneNumber}")
                } else {
                    val hasStructuredReply = newIncoming.any {
                        JsonPhoneFinder.hasStructuredReplyContent(it.text)
                    }
                    if (hasStructuredReply) {
                        updateLookupState(chatId) { cur ->
                            cur.copy(
                                statusMessage = "Balasan diterima, belum ada yang berakhiran \"${state.phoneSuffix}\" untuk \"${state.targetName}\"."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateLookupState(chatId: Long, transform: (PhoneLookupState) -> PhoneLookupState) {
        _phoneLookupStates.update { map ->
            val current = map[chatId] ?: PhoneLookupState(chatId = chatId)
            map + (chatId to transform(current))
        }
    }

    fun sendMessage(chatId: Long, text: String, sendAsJsonBlock: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val shouldAutoSearch = !sendAsJsonBlock &&
            !trimmed.startsWith("{") &&
            !trimmed.startsWith("[")

        if (shouldAutoSearch) {
            val parsed = JsonPhoneFinder.parseQuery(trimmed)
            // Hanya aktifkan pencarian otomatis jika ada nama DAN akhiran nomor (mis. DANNY SUGIHARTO 72)
            if (parsed.phoneSuffix.isNotBlank()) {
                val knownIncomingIds = tdLib.getCurrentMessages(chatId)
                    .asSequence()
                    .filter { !it.isOutgoing }
                    .map { it.id }
                    .toSet()

                updateLookupState(chatId) { cur ->
                    cur.copy(
                        queryInput = trimmed,
                        targetName = parsed.nameQuery,
                        phoneSuffix = parsed.phoneSuffix,
                        isWaitingForReply = true,
                        baselineIncomingIds = knownIncomingIds,
                        matches = emptyList(),
                        statusMessage = "Menunggu balasan untuk \"$trimmed\"..."
                    )
                }
                startWatchingIncomingReplies(chatId)
            } else {
                // Jika hanya nama tanpa akhiran nomor, jangan tampilkan hasil pencarian
                clearPhoneLookupResults(chatId)
            }
        }

        tdLib.sendMessage(chatId, text, sendAsJsonBlock)
    }

    fun sendPhoto(chatId: Long, uri: Uri, caption: String?) =
        tdLib.sendPhoto(chatId, uri, caption)

    fun openChatByTarget(target: String, onOpened: (Long) -> Unit) =
        tdLib.openPublicChatOrId(target, onOpened)

    fun logOut() = tdLib.logOut()
    fun clearJsonLogs() = tdLib.clearLogs()

    // Customization updates
    fun setAccentTheme(theme: AccentTheme) = viewModelScope.launch { prefs.updateAccentTheme(theme) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.updateThemeMode(mode) }
    fun setWallpaperStyle(style: WallpaperStyle) = viewModelScope.launch { prefs.updateWallpaperStyle(style) }
    fun setBubbleCornerDp(dp: Int) = viewModelScope.launch { prefs.updateBubbleCornerDp(dp) }
    fun setJsonDisplayMode(mode: JsonDisplayMode) = viewModelScope.launch { prefs.updateJsonDisplayMode(mode) }
    fun setJsonProjectionMode(mode: JsonProjectionMode) = viewModelScope.launch { prefs.updateJsonProjectionMode(mode) }
    fun setCustomJsonFields(csv: String) = viewModelScope.launch { prefs.updateCustomJsonFields(csv) }
    fun setJsonSyntaxTheme(theme: JsonSyntaxTheme) = viewModelScope.launch { prefs.updateJsonSyntaxTheme(theme) }
    fun setMonoFontSizeSp(sp: Int) = viewModelScope.launch { prefs.updateMonoFontSizeSp(sp) }
    fun setAutoReplyJsonEnabled(enabled: Boolean) = viewModelScope.launch { prefs.updateAutoReplyJsonEnabled(enabled) }
    fun setIncludeSendReceiptJson(enabled: Boolean) = viewModelScope.launch { prefs.updateIncludeSendReceiptJson(enabled) }

    fun addAutoReplyRule(keyword: String, template: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val cleanKeyword = keyword.trim().ifBlank { "*" }
            val cleanTemplate = template.trim()
            try {
                JSONObject(cleanTemplate)
                ruleDao.insertRule(
                    AutoReplyRuleEntity(
                        triggerKeyword = cleanKeyword,
                        jsonTemplate = JsonUtils.formatPrettyJson(cleanTemplate),
                        isEnabled = true
                    )
                )
                _localToast.tryEmit("Aturan Auto-Reply JSON ditambahkan")
                onDone()
            } catch (_: Exception) {
                _localToast.tryEmit("Template harus berupa format JSON yang valid.")
            }
        }
    }

    fun toggleRule(rule: AutoReplyRuleEntity) {
        viewModelScope.launch { ruleDao.updateRule(rule.copy(isEnabled = !rule.isEnabled)) }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch { ruleDao.deleteRule(ruleId) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val db = TeleCraftDatabase.getInstance(appContext)
                val prefs = UserPreferencesRepository(appContext)
                val ruleDao = db.autoReplyRuleDao()
                val tdLib = TdLibManager(appContext, prefs, ruleDao)
                return TeleCraftViewModel(tdLib, prefs, ruleDao) as T
            }
        }
    }
}
