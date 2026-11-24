package com.example.model

enum class AccentTheme(val label: String, val primaryHex: Long, val secondaryHex: Long) {
    TELEGRAM_CYAN("Telegram Cyan", 0xFF229ED9, 0xFF38BDF8),
    EMERALD_CODE("Emerald Matrix", 0xFF10B981, 0xFF34D399),
    SUNSET_AMBER("Sunset Amber", 0xFFF59E0B, 0xFFFBBF24),
    ROYAL_INDIGO("Royal Indigo", 0xFF6366F1, 0xFF818CF8)
}

enum class ThemeMode(val label: String) {
    DARK("Gelap"),
    AMOLED("AMOLED"),
    LIGHT("Terang"),
    SYSTEM("Sistem")
}

enum class WallpaperStyle(val label: String) {
    NEBULA("Nebula Konstelasi"),
    MIDNIGHT_GRADIENT("Gradasi Malam"),
    OCEAN_SLATE("Biru Samudra"),
    CLEAN_MINIMAL("Minimalis Polos")
}

enum class JsonDisplayMode(val label: String, val description: String) {
    JSON_PRIMARY("Balasan JSON", "Semua pesan & balasan tampil langsung sebagai JSON"),
    HYBRID("Hybrid (Teks + JSON)", "Gelembung teks dilengkapi kartu JSON di bawahnya"),
    CLASSIC("Klasik (Klik untuk JSON)", "Gelembung Telegram klasik, ketuk pesan untuk lihat JSON")
}

enum class JsonProjectionMode(val label: String, val description: String) {
    FULL_UPDATE("Update Lengkap", "Seluruh JSON mentah dari Telegram API"),
    MESSAGE_ONLY("Objek Message", "Hanya objek message / result dari Telegram"),
    COMPACT("Ringkas", "Hanya update_id, message_id, chat, from, text, date"),
    CUSTOM_FIELDS("Field Kustom", "Pilih sendiri field JSON yang ingin ditampilkan")
}

enum class JsonSyntaxTheme(val label: String) {
    MONOKAI("Monokai"),
    NORD("Nord Frost"),
    TELEGRAM_BLUE("Telegram Blue"),
    SOLARIZED("Solarized")
}

data class BotConnectionInfo(
    val token: String = "",
    val botId: Long = 0L,
    val botName: String = "",
    val botUsername: String = "",
    val lastUpdateOffset: Long = 0L,
    val isConnected: Boolean = false
)

data class CustomUiSettings(
    val accentTheme: AccentTheme = AccentTheme.TELEGRAM_CYAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val wallpaperStyle: WallpaperStyle = WallpaperStyle.NEBULA,
    val bubbleCornerDp: Int = 16,
    val jsonDisplayMode: JsonDisplayMode = JsonDisplayMode.JSON_PRIMARY,
    val jsonProjectionMode: JsonProjectionMode = JsonProjectionMode.FULL_UPDATE,
    val customJsonFields: String = "message_id,from.username,chat.id,chat.type,text,date",
    val jsonSyntaxTheme: JsonSyntaxTheme = JsonSyntaxTheme.MONOKAI,
    val monospaceFontSizeSp: Int = 12,
    val pollingEnabled: Boolean = true,
    val pollingIntervalSec: Int = 3,
    val autoReplyJsonEnabled: Boolean = false,
    val includeSendReceiptJson: Boolean = true
)
