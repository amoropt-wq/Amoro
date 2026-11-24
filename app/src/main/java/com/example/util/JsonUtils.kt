package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.model.JsonProjectionMode
import com.example.model.JsonSyntaxTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonUtils {

    fun formatPrettyJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "{}"
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    fun projectTelegramJson(
        rawJson: String,
        mode: JsonProjectionMode,
        customFieldsCsv: String
    ): String {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return "{}"
        return try {
            val root = JSONObject(trimmed)
            val messageObj: JSONObject = when {
                root.has("message") && root.optJSONObject("message") != null -> root.getJSONObject("message")
                else -> root
            }

            when (mode) {
                JsonProjectionMode.FULL_UPDATE -> root.toString(2)
                JsonProjectionMode.MESSAGE_ONLY -> {
                    val contentObj = messageObj.optJSONObject("content")
                    contentObj?.toString(2) ?: messageObj.toString(2)
                }
                JsonProjectionMode.COMPACT -> {
                    val compact = JSONObject()
                    if (messageObj.has("@type")) compact.put("@type", messageObj.get("@type"))
                    if (messageObj.has("id")) compact.put("id", messageObj.get("id"))
                    if (messageObj.has("chatId")) compact.put("chatId", messageObj.get("chatId"))
                    if (messageObj.has("isOutgoing")) compact.put("isOutgoing", messageObj.get("isOutgoing"))
                    messageObj.optJSONObject("senderId")?.let { compact.put("senderId", it) }
                    messageObj.optJSONObject("content")?.let { content ->
                        val text = content.optJSONObject("text")?.optString("text")
                            ?: content.optJSONObject("caption")?.optString("text")
                        if (!text.isNullOrBlank()) {
                            compact.put("text", text)
                        } else {
                            compact.put("contentType", content.optString("@type"))
                        }
                    }
                    if (messageObj.has("date")) compact.put("date", messageObj.get("date"))
                    compact.toString(2)
                }
                JsonProjectionMode.CUSTOM_FIELDS -> {
                    val fields = customFieldsCsv.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (fields.isEmpty()) return messageObj.toString(2)
                    val custom = JSONObject()
                    for (path in fields) {
                        val value = resolvePath(root, path) ?: resolvePath(messageObj, path)
                        if (value != null) {
                            custom.put(path, value)
                        }
                    }
                    if (custom.length() == 0) messageObj.toString(2) else custom.toString(2)
                }
            }
        } catch (_: Exception) {
            formatPrettyJson(rawJson)
        }
    }

    private fun resolvePath(obj: JSONObject, dotPath: String): Any? {
        val parts = dotPath.split(".")
        var current: Any? = obj
        for (part in parts) {
            if (current is JSONObject && current.has(part)) {
                current = current.get(part)
            } else {
                return null
            }
        }
        return current
    }

    fun renderAutoReplyJson(
        template: String,
        text: String,
        chatId: Long,
        username: String?,
        firstName: String,
        messageId: Long,
        dateSec: Long,
        botUsername: String
    ): String {
        val escapedText = JSONObject.quote(text).removePrefix("\"").removeSuffix("\"")
        val escapedUser = JSONObject.quote(username ?: "").removePrefix("\"").removeSuffix("\"")
        val escapedFirst = JSONObject.quote(firstName).removePrefix("\"").removeSuffix("\"")
        val escapedBot = JSONObject.quote(botUsername).removePrefix("\"").removeSuffix("\"")

        val filled = template
            .replace("{{text}}", escapedText)
            .replace("{{chat_id}}", chatId.toString())
            .replace("{{username}}", escapedUser)
            .replace("{{first_name}}", escapedFirst)
            .replace("{{message_id}}", messageId.toString())
            .replace("{{date}}", dateSec.toString())
            .replace("{{bot_username}}", escapedBot)

        return formatPrettyJson(filled)
    }

    fun highlightJson(json: String, theme: JsonSyntaxTheme, isDark: Boolean): AnnotatedString {
        val palette = when (theme) {
            JsonSyntaxTheme.MONOKAI -> SyntaxPalette(
                key = if (isDark) Color(0xFFF92672) else Color(0xFFBE185D),
                string = if (isDark) Color(0xFFE6DB74) else Color(0xFF047857),
                number = if (isDark) Color(0xFFAE81FF) else Color(0xFF6D28D9),
                booleanNull = if (isDark) Color(0xFF66D9EF) else Color(0xFF0369A1),
                punctuation = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            )
            JsonSyntaxTheme.NORD -> SyntaxPalette(
                key = if (isDark) Color(0xFF88C0D0) else Color(0xFF0284C7),
                string = if (isDark) Color(0xFFA3BE8C) else Color(0xFF15803D),
                number = if (isDark) Color(0xFFB48EAD) else Color(0xFF7E22CE),
                booleanNull = if (isDark) Color(0xFF81A1C1) else Color(0xFF1D4ED8),
                punctuation = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
            JsonSyntaxTheme.TELEGRAM_BLUE -> SyntaxPalette(
                key = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
                string = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                number = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                booleanNull = if (isDark) Color(0xFFF472B6) else Color(0xFFBE185D),
                punctuation = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            )
            JsonSyntaxTheme.SOLARIZED -> SyntaxPalette(
                key = if (isDark) Color(0xFF268BD2) else Color(0xFF1D4ED8),
                string = if (isDark) Color(0xFF2AA198) else Color(0xFF0F766E),
                number = if (isDark) Color(0xFFD33682) else Color(0xFFBE185D),
                booleanNull = if (isDark) Color(0xFFB58900) else Color(0xFFA16207),
                punctuation = if (isDark) Color(0xFF839496) else Color(0xFF64748B)
            )
        }

        return buildAnnotatedString {
            var i = 0
            val len = json.length
            while (i < len) {
                val ch = json[i]
                when {
                    ch == '"' -> {
                        val start = i
                        i++
                        while (i < len) {
                            if (json[i] == '\\' && i + 1 < len) {
                                i += 2
                            } else if (json[i] == '"') {
                                i++
                                break
                            } else {
                                i++
                            }
                        }
                        var j = i
                        while (j < len && json[j].isWhitespace()) j++
                        val isKey = j < len && json[j] == ':'
                        val token = json.substring(start, i)
                        pushStyle(
                            SpanStyle(
                                color = if (isKey) palette.key else palette.string,
                                fontWeight = if (isKey) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                        append(token)
                        pop()
                    }
                    ch == '-' || ch.isDigit() -> {
                        val start = i
                        i++
                        while (i < len && (json[i].isDigit() || json[i] in ".eE+-")) {
                            i++
                        }
                        pushStyle(SpanStyle(color = palette.number))
                        append(json.substring(start, i))
                        pop()
                    }
                    json.startsWith("true", i) -> {
                        pushStyle(SpanStyle(color = palette.booleanNull, fontWeight = FontWeight.Medium))
                        append("true")
                        pop()
                        i += 4
                    }
                    json.startsWith("false", i) -> {
                        pushStyle(SpanStyle(color = palette.booleanNull, fontWeight = FontWeight.Medium))
                        append("false")
                        pop()
                        i += 5
                    }
                    json.startsWith("null", i) -> {
                        pushStyle(SpanStyle(color = palette.booleanNull, fontWeight = FontWeight.Medium))
                        append("null")
                        pop()
                        i += 4
                    }
                    ch in "{}[]:," -> {
                        pushStyle(SpanStyle(color = palette.punctuation))
                        append(ch)
                        pop()
                        i++
                    }
                    else -> {
                        append(ch)
                        i++
                    }
                }
            }
        }
    }

    fun formatTimestamp(epochSec: Long): String {
        if (epochSec <= 0L) return ""
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(epochSec * 1000L))
    }

    fun formatFullDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    private data class SyntaxPalette(
        val key: Color,
        val string: Color,
        val number: Color,
        val booleanNull: Color,
        val punctuation: Color
    )
}
