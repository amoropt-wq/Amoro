package com.example

import com.example.data.tdlib.TelegramMessageUi
import com.example.util.JsonPhoneFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPhoneFinderTest {

    @Test
    fun parseQuery_extractsNameAndTrailingPhoneSuffix() {
        val parsed = JsonPhoneFinder.parseQuery("DANNY SUGIHARTO 72")
        assertEquals("DANNY SUGIHARTO", parsed.nameQuery)
        assertEquals("72", parsed.phoneSuffix)
    }

    @Test
    fun parseQuery_handlesNameOnly() {
        val parsed = JsonPhoneFinder.parseQuery("DANNY SUGIHARTO")
        assertEquals("DANNY SUGIHARTO", parsed.nameQuery)
        assertEquals("", parsed.phoneSuffix)
    }

    @Test
    fun parseQuery_stripsLeadingSlashCommand() {
        val parsed = JsonPhoneFinder.parseQuery("/cari DANNY SUGIHARTO 72")
        assertEquals("DANNY SUGIHARTO", parsed.nameQuery)
        assertEquals("72", parsed.phoneSuffix)
    }

    @Test
    fun searchInMessages_findsGroupedRecordWithEmailAndSuffixMatch() {
        val telegramReply = """
            📩E-mail:  other_person@example.com
            📞Telepon:  62811111199
            👤Nama lengkap:  Danny Sugiharto

            📩E-mail:  ignatius_danny@yahoo.co.id
            🔐Kata sandi terenkripsi:  fd5a14a4653dacfd7803f7ff2cb612edbb9643f4178408d00417e1a6521e55364eec1e74c00b3b0886c5ae15b8ac874d
            📞Telepon:  62816612872
            📆Aktivitas terakhir:  2019-05-16 09:47:03.436999
            📆Tanggal pendaftaran:  2019-05-16 09:45:10
            🎂Tanggal lahir:  0001-01-01
            🍚Salt:  31fa
            👤Nama lengkap:  Danny Sugiharto
            🚻Jenis Kelamin:  0
        """.trimIndent()

        val msg = TelegramMessageUi(
            id = 500L,
            chatId = 123L,
            senderName = "LookupBot",
            text = telegramReply,
            isOutgoing = false,
            rawJson = "{}",
            timestampSec = 1700000000L
        )

        val matches = JsonPhoneFinder.searchInMessages(
            messages = listOf(msg),
            nameQuery = "DANNY SUGIHARTO",
            phoneSuffix = "72"
        )

        assertEquals(1, matches.size)
        val match = matches.first()
        assertEquals("Danny Sugiharto", match.matchedName)
        assertEquals("ignatius_danny@yahoo.co.id", match.email)
        assertEquals("62816612872", match.phoneNumber)
    }

    @Test
    fun searchInMessages_returnsEmptyWhenOnlyNameProvidedWithoutPhoneSuffix() {
        val telegramReply = """
            📩E-mail:  ignatius_danny@yahoo.co.id
            📞Telepon:  62816612872
            👤Nama lengkap:  Danny Sugiharto
        """.trimIndent()

        val msg = TelegramMessageUi(
            id = 501L,
            chatId = 123L,
            senderName = "LookupBot",
            text = telegramReply,
            isOutgoing = false,
            rawJson = "{}",
            timestampSec = 1700000000L
        )

        val matches = JsonPhoneFinder.searchInMessages(
            messages = listOf(msg),
            nameQuery = "DANNY SUGIHARTO",
            phoneSuffix = ""
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun normalizeHelpers_handleFormattedPhonesAndText() {
        assertEquals("6281388770072", JsonPhoneFinder.normalizeDigits("+62 813-8877-0072"))
        assertEquals("danny sugiharto", JsonPhoneFinder.normalizeText("  DANNY,  Sugiharto! "))
    }
}
