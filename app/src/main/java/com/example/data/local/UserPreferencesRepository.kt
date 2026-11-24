package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentTheme
import com.example.model.BotConnectionInfo
import com.example.model.CustomUiSettings
import com.example.model.JsonDisplayMode
import com.example.model.JsonProjectionMode
import com.example.model.JsonSyntaxTheme
import com.example.model.ThemeMode
import com.example.model.WallpaperStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telecraft_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val BOT_TOKEN = stringPreferencesKey("bot_token")
        val BOT_ID = longPreferencesKey("bot_id")
        val BOT_NAME = stringPreferencesKey("bot_name")
        val BOT_USERNAME = stringPreferencesKey("bot_username")
        val LAST_OFFSET = longPreferencesKey("last_update_offset")

        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WALLPAPER_STYLE = stringPreferencesKey("wallpaper_style")
        val BUBBLE_CORNER_DP = intPreferencesKey("bubble_corner_dp")
        val JSON_DISPLAY_MODE = stringPreferencesKey("json_display_mode")
        val JSON_PROJECTION_MODE = stringPreferencesKey("json_projection_mode")
        val CUSTOM_JSON_FIELDS = stringPreferencesKey("custom_json_fields")
        val JSON_SYNTAX_THEME = stringPreferencesKey("json_syntax_theme")
        val MONO_FONT_SIZE_SP = intPreferencesKey("mono_font_size_sp")
        val POLLING_ENABLED = booleanPreferencesKey("polling_enabled")
        val POLLING_INTERVAL_SEC = intPreferencesKey("polling_interval_sec")
        val AUTO_REPLY_JSON_ENABLED = booleanPreferencesKey("auto_reply_json_enabled")
        val INCLUDE_SEND_RECEIPT_JSON = booleanPreferencesKey("include_send_receipt_json")
    }

    val botConnectionFlow: Flow<BotConnectionInfo> = context.dataStore.data.map { prefs ->
        val token = prefs[Keys.BOT_TOKEN].orEmpty()
        BotConnectionInfo(
            token = token,
            botId = prefs[Keys.BOT_ID] ?: 0L,
            botName = prefs[Keys.BOT_NAME].orEmpty(),
            botUsername = prefs[Keys.BOT_USERNAME].orEmpty(),
            lastUpdateOffset = prefs[Keys.LAST_OFFSET] ?: 0L,
            isConnected = token.isNotBlank() && (prefs[Keys.BOT_ID] ?: 0L) != 0L
        )
    }

    val uiSettingsFlow: Flow<CustomUiSettings> = context.dataStore.data.map { prefs ->
        CustomUiSettings(
            accentTheme = enumOrDefault(prefs[Keys.ACCENT_THEME], AccentTheme.TELEGRAM_CYAN),
            themeMode = enumOrDefault(prefs[Keys.THEME_MODE], ThemeMode.DARK),
            wallpaperStyle = enumOrDefault(prefs[Keys.WALLPAPER_STYLE], WallpaperStyle.NEBULA),
            bubbleCornerDp = prefs[Keys.BUBBLE_CORNER_DP] ?: 16,
            jsonDisplayMode = enumOrDefault(prefs[Keys.JSON_DISPLAY_MODE], JsonDisplayMode.JSON_PRIMARY),
            jsonProjectionMode = enumOrDefault(prefs[Keys.JSON_PROJECTION_MODE], JsonProjectionMode.FULL_UPDATE),
            customJsonFields = prefs[Keys.CUSTOM_JSON_FIELDS]
                ?: "message_id,from.username,chat.id,chat.type,text,date",
            jsonSyntaxTheme = enumOrDefault(prefs[Keys.JSON_SYNTAX_THEME], JsonSyntaxTheme.MONOKAI),
            monospaceFontSizeSp = prefs[Keys.MONO_FONT_SIZE_SP] ?: 12,
            pollingEnabled = prefs[Keys.POLLING_ENABLED] ?: true,
            pollingIntervalSec = prefs[Keys.POLLING_INTERVAL_SEC] ?: 3,
            autoReplyJsonEnabled = prefs[Keys.AUTO_REPLY_JSON_ENABLED] ?: false,
            includeSendReceiptJson = prefs[Keys.INCLUDE_SEND_RECEIPT_JSON] ?: true
        )
    }

    suspend fun saveBotConnection(token: String, botId: Long, botName: String, botUsername: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BOT_TOKEN] = token
            prefs[Keys.BOT_ID] = botId
            prefs[Keys.BOT_NAME] = botName
            prefs[Keys.BOT_USERNAME] = botUsername
        }
    }

    suspend fun clearBotConnection() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.BOT_TOKEN)
            prefs.remove(Keys.BOT_ID)
            prefs.remove(Keys.BOT_NAME)
            prefs.remove(Keys.BOT_USERNAME)
            prefs[Keys.LAST_OFFSET] = 0L
        }
    }

    suspend fun saveLastUpdateOffset(offset: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_OFFSET] = offset
        }
    }

    suspend fun updateAccentTheme(value: AccentTheme) {
        context.dataStore.edit { it[Keys.ACCENT_THEME] = value.name }
    }

    suspend fun updateThemeMode(value: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = value.name }
    }

    suspend fun updateWallpaperStyle(value: WallpaperStyle) {
        context.dataStore.edit { it[Keys.WALLPAPER_STYLE] = value.name }
    }

    suspend fun updateBubbleCornerDp(value: Int) {
        context.dataStore.edit { it[Keys.BUBBLE_CORNER_DP] = value.coerceIn(4, 24) }
    }

    suspend fun updateJsonDisplayMode(value: JsonDisplayMode) {
        context.dataStore.edit { it[Keys.JSON_DISPLAY_MODE] = value.name }
    }

    suspend fun updateJsonProjectionMode(value: JsonProjectionMode) {
        context.dataStore.edit { it[Keys.JSON_PROJECTION_MODE] = value.name }
    }

    suspend fun updateCustomJsonFields(value: String) {
        context.dataStore.edit { it[Keys.CUSTOM_JSON_FIELDS] = value }
    }

    suspend fun updateJsonSyntaxTheme(value: JsonSyntaxTheme) {
        context.dataStore.edit { it[Keys.JSON_SYNTAX_THEME] = value.name }
    }

    suspend fun updateMonoFontSizeSp(value: Int) {
        context.dataStore.edit { it[Keys.MONO_FONT_SIZE_SP] = value.coerceIn(10, 16) }
    }

    suspend fun updatePollingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POLLING_ENABLED] = enabled }
    }

    suspend fun updatePollingIntervalSec(seconds: Int) {
        context.dataStore.edit { it[Keys.POLLING_INTERVAL_SEC] = seconds.coerceIn(2, 30) }
    }

    suspend fun updateAutoReplyJsonEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REPLY_JSON_ENABLED] = enabled }
    }

    suspend fun updateIncludeSendReceiptJson(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_SEND_RECEIPT_JSON] = enabled }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }
}
