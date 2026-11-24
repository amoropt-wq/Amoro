package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AutoReplyRuleEntity
import com.example.data.tdlib.TelegramUserProfile
import com.example.model.AccentTheme
import com.example.model.CustomUiSettings
import com.example.model.JsonDisplayMode
import com.example.model.JsonProjectionMode
import com.example.model.JsonSyntaxTheme
import com.example.model.ThemeMode
import com.example.model.WallpaperStyle
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

@Composable
fun CustomizeStudioScreen(
    me: TelegramUserProfile,
    uiSettings: CustomUiSettings,
    autoReplyRules: List<AutoReplyRuleEntity>,
    onAccentThemeChange: (AccentTheme) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWallpaperChange: (WallpaperStyle) -> Unit,
    onBubbleCornerChange: (Int) -> Unit,
    onJsonDisplayModeChange: (JsonDisplayMode) -> Unit,
    onJsonProjectionModeChange: (JsonProjectionMode) -> Unit,
    onCustomJsonFieldsChange: (String) -> Unit,
    onJsonSyntaxThemeChange: (JsonSyntaxTheme) -> Unit,
    onMonoFontSizeChange: (Int) -> Unit,
    onAutoReplyJsonChange: (Boolean) -> Unit,
    onIncludeSendReceiptChange: (Boolean) -> Unit,
    onToggleRule: (AutoReplyRuleEntity) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onAddRule: (String, String, () -> Unit) -> Unit,
    onLogOut: () -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var customFieldsDraft by remember(uiSettings.customJsonFields) {
        mutableStateOf(uiSettings.customJsonFields)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Studio Kustomisasi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Sesuaikan tampilan dalam & format balasan JSON Telegram Anda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. JSON Reply Format Section
        item {
            SectionCard(
                title = "Format Balasan JSON",
                icon = Icons.Default.Code
            ) {
                Text(
                    text = "Mode Tampilan Pesan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JsonDisplayMode.entries.forEach { mode ->
                        val selected = uiSettings.jsonDisplayMode == mode
                        Surface(
                            onClick = { onJsonDisplayModeChange(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Dipilih",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Struktur / Proyeksi JSON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JsonProjectionMode.entries.forEach { proj ->
                        FilterChip(
                            selected = uiSettings.jsonProjectionMode == proj,
                            onClick = { onJsonProjectionModeChange(proj) },
                            label = { Text(proj.label) }
                        )
                    }
                }
                Text(
                    text = uiSettings.jsonProjectionMode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiSettings.jsonProjectionMode == JsonProjectionMode.CUSTOM_FIELDS) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customFieldsDraft,
                        onValueChange = {
                            customFieldsDraft = it
                            onCustomJsonFieldsChange(it)
                        },
                        label = { Text("Field JSON Kustom (pisahkan koma)") },
                        placeholder = { Text("id,chatId,content.text.text,date") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Tema Warna Syntax JSON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JsonSyntaxTheme.entries.forEach { syntax ->
                        FilterChip(
                            selected = uiSettings.jsonSyntaxTheme == syntax,
                            onClick = { onJsonSyntaxThemeChange(syntax) },
                            label = { Text(syntax.label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ukuran Font Kode JSON (${uiSettings.monospaceFontSizeSp}sp)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiSettings.monospaceFontSizeSp.toFloat(),
                    onValueChange = { onMonoFontSizeChange(it.roundToInt()) },
                    valueRange = 10f..16f,
                    steps = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tampilkan JSON Saat Mengirim",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Menampilkan payload objek TDLib di bawah pesan keluar Anda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiSettings.includeSendReceiptJson,
                        onCheckedChange = onIncludeSendReceiptChange
                    )
                }
            }
        }

        // 2. Auto-Reply JSON Engine
        item {
            SectionCard(
                title = "Auto-Reply JSON (Chat Pribadi)",
                icon = Icons.Default.SmartToy
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktifkan Balas Otomatis JSON",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Membalas otomatis chat pribadi yang masuk di Telegram dengan blok kode JSON kustom",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiSettings.autoReplyJsonEnabled,
                        onCheckedChange = onAutoReplyJsonChange,
                        modifier = Modifier.testTag("auto_reply_json_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aturan Template JSON (${autoReplyRules.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showAddRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah Aturan")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    autoReplyRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (rule.triggerKeyword == "*") "Trigger: Semua Pesan (*)" else "Trigger: \"${rule.triggerKeyword}\"",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { onToggleRule(rule) }
                                        )
                                        IconButton(onClick = { onDeleteRule(rule.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus aturan",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = rule.jsonTemplate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Appearance Customization
        item {
            SectionCard(
                title = "Kustomisasi Tampilan Dalam",
                icon = Icons.Default.Palette
            ) {
                Text(
                    text = "Mode Tema",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiSettings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Warna Aksen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccentTheme.entries.forEach { accent ->
                        val selected = uiSettings.accentTheme == accent
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onAccentThemeChange(accent) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(accent.primaryHex))
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = accent.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = accent.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Wallpaper Ruang Obrolan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperStyle.entries.forEach { wp ->
                        FilterChip(
                            selected = uiSettings.wallpaperStyle == wp,
                            onClick = { onWallpaperChange(wp) },
                            label = { Text(wp.label) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Kelengkungan Gelembung Chat (${uiSettings.bubbleCornerDp}dp)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiSettings.bubbleCornerDp.toFloat(),
                    onValueChange = { onBubbleCornerChange(it.roundToInt()) },
                    valueRange = 4f..24f,
                    steps = 9
                )
            }
        }

        // 4. Connected Telegram Account
        item {
            SectionCard(
                title = "Akun Telegram Terhubung",
                icon = Icons.Default.Person
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = buildString {
                                append(me.displayName)
                                if (me.username.isNotBlank()) append(" (@${me.username})")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "User ID: ${me.id} • +${me.phoneNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = EmeraldSuccess
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar dari Akun Telegram")
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddAutoReplyRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onSave = { keyword, template ->
                onAddRule(keyword, template) {
                    showAddRuleDialog = false
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Keluar dari Akun Telegram?") },
            text = { Text("Sesi TDLib pada perangkat ini akan ditutup dan Anda dapat masuk kembali dengan nomor lain.") },
            confirmButton = {
                Button(onClick = {
                    showLogoutDialog = false
                    onLogOut()
                }) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AddAutoReplyRuleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var template by remember {
        mutableStateOf(
            """
            {
              "status": "replied",
              "input": "{{text}}",
              "from": "@{{username}}",
              "chat_id": {{chat_id}}
            }
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aturan Auto-Reply JSON Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Kata Kunci Trigger (atau * untuk semua)") },
                    placeholder = { Text("/status") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Template Balasan JSON") },
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Variabel: {{text}}, {{chat_id}}, {{username}}, {{first_name}}, {{message_id}}, {{date}}, {{bot_username}}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyword, template) },
                enabled = template.isNotBlank()
            ) {
                Text("Simpan Aturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
