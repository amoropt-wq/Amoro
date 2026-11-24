package com.example.util

import com.example.data.tdlib.TelegramMessageUi
import org.json.JSONArray
import org.json.JSONObject

data class PhoneSearchCriteria(
    val rawQuery: String,
    val nameQuery: String,
    val phoneSuffix: String
)

data class PhoneLookupMatch(
    val messageId: Long,
    val matchedName: String,
    val email: String?,
    val phoneNumber: String,
    val normalizedPhone: String,
    val timestampSec: Long
)

object JsonPhoneFinder {

    private val phoneKeyHints = setOf(
        "phone", "phone_number", "phonenumber", "telp", "telepon", "telpon",
        "no_telp", "notelp", "no_telepon", "nomor_telepon", "nomor_telpon",
        "hp", "no_hp", "nohp", "nomor_hp", "mobile", "mobile_number",
        "wa", "whatsapp", "msisdn", "contact", "kontak", "cell", "nomor", "tlp"
    )

    private val nameKeyHints = setOf(
        "name", "nama", "full_name", "fullname", "nama_lengkap", "namalengkap",
        "customer", "customer_name", "pelanggan", "owner", "pemilik",
        "user", "username", "title", "label", "anggota", "member", "contact_name"
    )

    private val emailKeyHints = setOf(
        "email", "e-mail", "e_mail", "mail", "alamat_email", "surel"
    )

    private val nonPhoneKeyHints = setOf(
        "id", "chatid", "chat_id", "messageid", "message_id", "userid", "user_id",
        "senderid", "sender_id", "date", "timestamp", "created_at", "updated_at",
        "order", "messagethreadid", "replytomessageid", "mediaalbumid", "editdate",
        "ttl", "autodeletein", "via_bot_user_id", "viabotuserid", "offset", "length",
        "width", "height", "size", "duration", "code", "password", "sandi",
        "kata_sandi", "terenkripsi", "encrypted", "hash", "salt", "aktivitas",
        "pendaftaran", "tanggal", "lahir", "kelamin", "gender"
    )

    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun parseQuery(rawQuery: String): PhoneSearchCriteria {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return PhoneSearchCriteria("", "", "")
        }
        val tokens = trimmed.split(Regex("\\s+"))
        val effectiveTokens = if (tokens.size >= 2 && tokens.first().startsWith("/")) {
            tokens.drop(1)
        } else {
            tokens
        }
        if (effectiveTokens.size >= 2) {
            val lastToken = effectiveTokens.last()
            val hasLetters = lastToken.any { it.isLetter() }
            val digits = normalizeDigits(lastToken)
            if (!hasLetters && digits.isNotEmpty()) {
                val namePart = effectiveTokens.dropLast(1).joinToString(" ").trim()
                return PhoneSearchCriteria(
                    rawQuery = trimmed,
                    nameQuery = namePart,
                    phoneSuffix = digits
                )
            }
        }
        return PhoneSearchCriteria(
            rawQuery = trimmed,
            nameQuery = effectiveTokens.joinToString(" ").trim(),
            phoneSuffix = ""
        )
    }

    fun normalizeDigits(input: String): String =
        input.filter { it.isDigit() }

    fun normalizeText(input: String): String =
        input.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun searchInMessages(
        messages: List<TelegramMessageUi>,
        nameQuery: String,
        phoneSuffix: String,
        excludedMessageIds: Set<Long> = emptySet(),
        incomingOnly: Boolean = true
    ): List<PhoneLookupMatch> {
        val cleanName = normalizeText(nameQuery)
        val cleanSuffix = normalizeDigits(phoneSuffix)
        // Wajib ada akhiran nomor (belakang nomor); jika hanya nama jangan tampilkan
        if (cleanSuffix.isBlank()) return emptyList()

        val allMatches = mutableListOf<PhoneLookupMatch>()
        val dedup = HashSet<String>()

        val candidates = messages
            .asSequence()
            .filter { msg ->
                (!incomingOnly || !msg.isOutgoing) && msg.id !in excludedMessageIds
            }
            .sortedByDescending { it.timestampSec }

        for (msg in candidates) {
            val found = searchInSingleMessage(
                messageId = msg.id,
                text = msg.text,
                rawTdJson = msg.rawJson,
                timestampSec = msg.timestampSec,
                nameQuery = cleanName,
                phoneSuffix = cleanSuffix
            )
            for (match in found) {
                val key = "${match.messageId}|${match.normalizedPhone}|${normalizeText(match.matchedName)}"
                if (dedup.add(key)) {
                    allMatches.add(match)
                }
            }
        }
        return allMatches
    }

    fun searchInSingleMessage(
        messageId: Long,
        text: String,
        rawTdJson: String,
        timestampSec: Long,
        nameQuery: String,
        phoneSuffix: String
    ): List<PhoneLookupMatch> {
        val cleanName = normalizeText(nameQuery)
        val cleanSuffix = normalizeDigits(phoneSuffix)
        // Wajib ada akhiran nomor; jika hanya nama jangan tampilkan
        if (cleanSuffix.isBlank()) return emptyList()

        val nameTokens = if (cleanName.isBlank()) emptyList() else cleanName.split(" ").filter { it.isNotBlank() }
        val results = mutableListOf<PhoneLookupMatch>()

        // 1. Cari di JSON terstruktur di dalam teks balasan
        val textRoots = extractJsonRootsFromText(text)
        for (root in textRoots) {
            walkAnyJson(
                value = root,
                depth = 0,
                nameTokens = nameTokens,
                phoneSuffix = cleanSuffix,
                messageId = messageId,
                timestampSec = timestampSec,
                allowTdMetadata = true,
                outMatches = results
            )
        }

        // 2. Cari di kelompok key-value berbaris (mis. 📩E-mail: ..., 📞Telepon: ..., 👤Nama lengkap: ...)
        if (results.isEmpty() && text.isNotBlank()) {
            val groupedMatches = searchGroupedKeyValueText(
                text = text,
                nameTokens = nameTokens,
                phoneSuffix = cleanSuffix,
                messageId = messageId,
                timestampSec = timestampSec
            )
            results.addAll(groupedMatches)
        }

        // 3. Fallback: periksa wrapper TDLib rawJson (termasuk teks pesan yang tersimpan di dalam content.text.text)
        if (results.isEmpty() && rawTdJson.isNotBlank()) {
            val tdRoots = extractJsonRootsFromText(rawTdJson)
            for (root in tdRoots) {
                walkAnyJson(
                    value = root,
                    depth = 0,
                    nameTokens = nameTokens,
                    phoneSuffix = cleanSuffix,
                    messageId = messageId,
                    timestampSec = timestampSec,
                    allowTdMetadata = false,
                    outMatches = results
                )
            }
        }

        return results
    }

    fun hasStructuredReplyContent(text: String): Boolean {
        if (extractJsonRootsFromText(text).isNotEmpty()) return true
        return parseKeyValueGroups(text).any { it.size >= 2 }
    }

    fun extractJsonRootsFromText(input: String): List<Any> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        tryParseJson(trimmed)?.let { return listOf(it) }

        val roots = mutableListOf<Any>()
        var i = 0
        val n = trimmed.length
        while (i < n) {
            val ch = trimmed[i]
            if (ch == '{' || ch == '[') {
                val endIndex = findMatchingBracketEnd(trimmed, i)
                if (endIndex > i) {
                    val candidate = trimmed.substring(i, endIndex + 1)
                    val parsed = tryParseJson(candidate)
                    if (parsed != null) {
                        roots.add(parsed)
                        i = endIndex + 1
                        continue
                    }
                }
            }
            i++
        }
        return roots
    }

    private fun searchGroupedKeyValueText(
        text: String,
        nameTokens: List<String>,
        phoneSuffix: String,
        messageId: Long,
        timestampSec: Long
    ): List<PhoneLookupMatch> {
        val groups = parseKeyValueGroups(text)
        val matches = mutableListOf<PhoneLookupMatch>()
        for (group in groups) {
            matches.addAll(
                matchScalarGroup(
                    scalarFields = group,
                    nameTokens = nameTokens,
                    phoneSuffix = phoneSuffix,
                    messageId = messageId,
                    timestampSec = timestampSec,
                    allowTdMetadata = true
                )
            )
        }
        return matches
    }

    private fun parseKeyValueGroups(text: String): List<Map<String, String>> {
        val groups = mutableListOf<LinkedHashMap<String, String>>()
        var current = linkedMapOf<String, String>()

        val lines = text.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.all { it in "-=_•*~" }) {
                if (current.isNotEmpty()) {
                    groups.add(current)
                    current = linkedMapOf()
                }
                continue
            }

            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue

            val rawKey = cleanFieldKey(line.substring(0, colonIdx))
            val rawVal = line.substring(colonIdx + 1).trim()
            if (rawKey.isBlank() || rawVal.isBlank()) continue

            // Jika key yang sama muncul lagi dan kelompok saat ini sudah punya >= 2 field, mulai kelompok baru
            if (current.containsKey(rawKey) && current.size >= 2) {
                groups.add(current)
                current = linkedMapOf()
            }
            current[rawKey] = rawVal
        }
        if (current.isNotEmpty()) {
            groups.add(current)
        }
        return groups
    }

    private fun cleanFieldKey(rawKey: String): String {
        val firstAlnum = rawKey.indexOfFirst { it.isLetterOrDigit() }
        return if (firstAlnum >= 0) rawKey.substring(firstAlnum).trim() else rawKey.trim()
    }

    private fun findMatchingBracketEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) return i
                        if (depth < 0) return -1
                    }
                }
            }
        }
        return -1
    }

    private fun tryParseJson(candidate: String): Any? {
        val t = candidate.trim()
        return try {
            when {
                t.startsWith("{") -> JSONObject(t)
                t.startsWith("[") -> JSONArray(t)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun walkAnyJson(
        value: Any?,
        depth: Int,
        nameTokens: List<String>,
        phoneSuffix: String,
        messageId: Long,
        timestampSec: Long,
        allowTdMetadata: Boolean,
        outMatches: MutableList<PhoneLookupMatch>
    ): Boolean {
        if (value == null || depth > 12) return false

        return when (value) {
            is JSONObject -> {
                var childMatched = false
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val childVal = value.opt(key)
                    if (childVal is JSONObject || childVal is JSONArray) {
                        if (walkAnyJson(
                                value = childVal,
                                depth = depth + 1,
                                nameTokens = nameTokens,
                                phoneSuffix = phoneSuffix,
                                messageId = messageId,
                                timestampSec = timestampSec,
                                allowTdMetadata = allowTdMetadata,
                                outMatches = outMatches
                            )
                        ) {
                            childMatched = true
                        }
                    } else if (childVal is String) {
                        val embedded = extractJsonRootsFromText(childVal)
                        if (embedded.isNotEmpty()) {
                            for (subRoot in embedded) {
                                if (walkAnyJson(
                                        value = subRoot,
                                        depth = depth + 1,
                                        nameTokens = nameTokens,
                                        phoneSuffix = phoneSuffix,
                                        messageId = messageId,
                                        timestampSec = timestampSec,
                                        allowTdMetadata = true,
                                        outMatches = outMatches
                                    )
                                ) {
                                    childMatched = true
                                }
                            }
                        } else if (childVal.contains(":") && childVal.contains("\n")) {
                            val grouped = searchGroupedKeyValueText(
                                text = childVal,
                                nameTokens = nameTokens,
                                phoneSuffix = phoneSuffix,
                                messageId = messageId,
                                timestampSec = timestampSec
                            )
                            if (grouped.isNotEmpty()) {
                                outMatches.addAll(grouped)
                                childMatched = true
                            }
                        }
                    }
                }

                if (!childMatched) {
                    val scalarFields = linkedMapOf<String, String>()
                    collectShallowScalars(value, prefix = "", depth = 0, out = scalarFields)
                    val nodeMatches = matchScalarGroup(
                        scalarFields = scalarFields,
                        nameTokens = nameTokens,
                        phoneSuffix = phoneSuffix,
                        messageId = messageId,
                        timestampSec = timestampSec,
                        allowTdMetadata = allowTdMetadata
                    )
                    if (nodeMatches.isNotEmpty()) {
                        outMatches.addAll(nodeMatches)
                        return true
                    }
                }
                childMatched
            }
            is JSONArray -> {
                var anyMatched = false
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (walkAnyJson(
                            value = item,
                            depth = depth + 1,
                            nameTokens = nameTokens,
                            phoneSuffix = phoneSuffix,
                            messageId = messageId,
                            timestampSec = timestampSec,
                            allowTdMetadata = allowTdMetadata,
                            outMatches = outMatches
                        )
                    ) {
                        anyMatched = true
                    }
                }
                anyMatched
            }
            is String -> {
                val embedded = extractJsonRootsFromText(value)
                if (embedded.isNotEmpty()) {
                    var anyEmbedded = false
                    for (sub in embedded) {
                        if (walkAnyJson(
                                value = sub,
                                depth = depth + 1,
                                nameTokens = nameTokens,
                                phoneSuffix = phoneSuffix,
                                messageId = messageId,
                                timestampSec = timestampSec,
                                allowTdMetadata = true,
                                outMatches = outMatches
                            )
                        ) {
                            anyEmbedded = true
                        }
                    }
                    anyEmbedded
                } else {
                    val grouped = searchGroupedKeyValueText(
                        text = value,
                        nameTokens = nameTokens,
                        phoneSuffix = phoneSuffix,
                        messageId = messageId,
                        timestampSec = timestampSec
                    )
                    if (grouped.isNotEmpty()) {
                        outMatches.addAll(grouped)
                        true
                    } else {
                        val inlineMatch = matchInlineStringRecord(
                            text = value,
                            nameTokens = nameTokens,
                            phoneSuffix = phoneSuffix,
                            messageId = messageId,
                            timestampSec = timestampSec
                        )
                        if (inlineMatch != null) {
                            outMatches.add(inlineMatch)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            else -> false
        }
    }

    private fun matchScalarGroup(
        scalarFields: Map<String, String>,
        nameTokens: List<String>,
        phoneSuffix: String,
        messageId: Long,
        timestampSec: Long,
        allowTdMetadata: Boolean
    ): List<PhoneLookupMatch> {
        if (scalarFields.isEmpty() || phoneSuffix.isBlank()) return emptyList()

        // 1. Cari nama yang cocok di kelompok ini
        var matchedNameValue: String? = null
        var bestNameScore = -1

        if (nameTokens.isEmpty()) {
            matchedNameValue = scalarFields.entries
                .firstOrNull { isNameKey(it.key) }
                ?.value
                ?: scalarFields.values.firstOrNull()
                ?: ""
        } else {
            for ((k, v) in scalarFields) {
                val normalizedVal = normalizeText(v)
                val valWords = normalizedVal.split(" ").filter { it.isNotEmpty() }
                if (nameTokens.all { token ->
                        valWords.any { w -> w == token || (token.length >= 3 && w.contains(token)) }
                    }
                ) {
                    val score = if (isNameKey(k)) 10 else 5
                    if (score > bestNameScore) {
                        bestNameScore = score
                        matchedNameValue = v
                    }
                }
                val normalizedKey = normalizeText(k)
                val keyWords = normalizedKey.split(" ").filter { it.isNotEmpty() }
                if (nameTokens.all { token ->
                        keyWords.any { w -> w == token || (token.length >= 3 && w.contains(token)) }
                    }
                ) {
                    val score = 3
                    if (score > bestNameScore) {
                        bestNameScore = score
                        matchedNameValue = k
                    }
                }
            }
        }

        if (matchedNameValue == null) return emptyList()

        // 2. Cari alamat email di dalam kelompok yang sama
        val groupEmail = extractEmailFromGroup(scalarFields)

        // 3. Cari nomor telepon yang akhiran digitnya sesuai phoneSuffix
        val matches = mutableListOf<PhoneLookupMatch>()
        for ((key, rawVal) in scalarFields) {
            if (!allowTdMetadata && isNonPhoneKey(key)) continue
            if (isNonPhoneKey(key) && !isPhoneKey(key)) continue

            val candidates = extractPhoneCandidates(key, rawVal)
            for (phoneRaw in candidates) {
                val digits = normalizeDigits(phoneRaw)
                if (digits.length < 6) continue
                if (!digits.endsWith(phoneSuffix)) continue

                matches.add(
                    PhoneLookupMatch(
                        messageId = messageId,
                        matchedName = matchedNameValue,
                        email = groupEmail,
                        phoneNumber = phoneRaw,
                        normalizedPhone = digits,
                        timestampSec = timestampSec
                    )
                )
            }
        }
        return matches
    }

    private fun extractEmailFromGroup(scalarFields: Map<String, String>): String? {
        // Prioritaskan key berlabel email
        for ((k, v) in scalarFields) {
            if (isEmailKey(k)) {
                val found = emailRegex.find(v)?.value
                if (!found.isNullOrBlank()) return found
                if (v.contains("@") && v.contains(".")) return v.trim()
            }
        }
        // Fallback: cari pola email di field mana pun dalam kelompok yang sama
        for ((_, v) in scalarFields) {
            val found = emailRegex.find(v)?.value
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    private fun matchInlineStringRecord(
        text: String,
        nameTokens: List<String>,
        phoneSuffix: String,
        messageId: Long,
        timestampSec: Long
    ): PhoneLookupMatch? {
        if (phoneSuffix.isBlank()) return null
        val norm = normalizeText(text)
        if (nameTokens.isNotEmpty() && !nameTokens.all { norm.contains(it) }) {
            return null
        }
        val inlineEmail = emailRegex.find(text)?.value
        val phoneMatches = Regex("(?:\\+?\\d[\\d\\s\\-().]{5,18}\\d)").findAll(text)
        for (match in phoneMatches) {
            val rawPhone = match.value.trim()
            val digits = normalizeDigits(rawPhone)
            if (digits.length in 7..16 && digits.endsWith(phoneSuffix)) {
                return PhoneLookupMatch(
                    messageId = messageId,
                    matchedName = if (nameTokens.isEmpty()) text else nameTokens.joinToString(" ").uppercase(),
                    email = inlineEmail,
                    phoneNumber = rawPhone,
                    normalizedPhone = digits,
                    timestampSec = timestampSec
                )
            }
        }
        return null
    }

    private fun collectShallowScalars(
        obj: JSONObject,
        prefix: String,
        depth: Int,
        out: MutableMap<String, String>
    ) {
        if (depth > 1) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (val v = obj.opt(key)) {
                null, JSONObject.NULL -> Unit
                is JSONObject -> collectShallowScalars(v, fullKey, depth + 1, out)
                is JSONArray -> {
                    for (i in 0 until v.length()) {
                        val item = v.opt(i)
                        if (item is String || item is Number) {
                            out["$fullKey[$i]"] = item.toString().trim()
                        }
                    }
                }
                else -> {
                    val str = v.toString().trim()
                    if (str.isNotEmpty() && !str.startsWith("{") && !str.startsWith("[")) {
                        out[fullKey] = str
                    }
                }
            }
        }
    }

    private fun extractPhoneCandidates(key: String, rawValue: String): List<String> {
        val phoneKey = isPhoneKey(key)
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (phoneKey) {
            val parts = trimmed.split(Regex("[,;/|]+")).map { it.trim() }.filter { it.isNotEmpty() }
            val valid = parts.filter { part ->
                val d = normalizeDigits(part)
                d.length in 6..16 && part.count { it.isLetter() } <= 2
            }
            if (valid.isNotEmpty()) return valid
        }

        val regexMatches = Regex("(?:\\+?\\d[\\d\\s\\-().]{5,18}\\d)").findAll(trimmed)
            .map { it.value.trim() }
            .filter { candidate ->
                val d = normalizeDigits(candidate)
                d.length in 7..16 && (
                    phoneKey ||
                        candidate.startsWith("+") ||
                        d.startsWith("08") ||
                        d.startsWith("628") ||
                        d.startsWith("0") ||
                        candidate.contains("-") ||
                        candidate.contains(" ")
                    )
            }
            .toList()
        return regexMatches
    }

    private fun isPhoneKey(key: String): Boolean {
        val leaf = normalizeText(key.substringAfterLast(".").substringBefore("["))
        val compact = leaf.replace(" ", "")
        if (compact in phoneKeyHints || leaf in phoneKeyHints) return true
        return phoneKeyHints.any { hint ->
            hint.length >= 3 && (compact.contains(hint) || leaf.contains(hint))
        }
    }

    private fun isNameKey(key: String): Boolean {
        val leaf = normalizeText(key.substringAfterLast(".").substringBefore("["))
        val compact = leaf.replace(" ", "")
        if (compact in nameKeyHints || leaf in nameKeyHints) return true
        return nameKeyHints.any { hint -> compact.contains(hint) || leaf.contains(hint) }
    }

    private fun isEmailKey(key: String): Boolean {
        val leaf = normalizeText(key.substringAfterLast(".").substringBefore("["))
        val compact = leaf.replace(" ", "")
        if (compact in emailKeyHints || leaf in emailKeyHints) return true
        return emailKeyHints.any { hint -> compact.contains(hint) || leaf.contains(hint) }
    }

    private fun isNonPhoneKey(key: String): Boolean {
        val leaf = normalizeText(key.substringAfterLast(".").substringBefore("["))
        val compact = leaf.replace(" ", "")
        if (compact in nonPhoneKeyHints || leaf in nonPhoneKeyHints) return true
        return nonPhoneKeyHints.any { hint ->
            hint.length >= 4 && (compact.contains(hint) || leaf.contains(hint))
        }
    }
}
