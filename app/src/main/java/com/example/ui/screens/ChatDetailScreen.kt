package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.tdlib.PhoneLookupState
import com.example.data.tdlib.TelegramChatUi
import com.example.data.tdlib.TelegramMessageUi
import com.example.model.CustomUiSettings
import com.example.model.JsonDisplayMode
import com.example.model.WallpaperStyle
import com.example.ui.components.JsonCodeBlock
import com.example.ui.components.JsonInspectorBottomSheet
import com.example.ui.components.PhoneLookupPanel
import com.example.ui.components.TelegramChatAvatar
import com.example.util.JsonUtils
import com.example.util.PhoneLookupMatch
import java.io.File

@Composable
fun ChatDetailScreen(
    chat: TelegramChatUi?,
    chatId: Long,
    messages: List<TelegramMessageUi>,
    uiSettings: CustomUiSettings,
    phoneLookupState: PhoneLookupState,
    onBack: () -> Unit,
    onRefreshHistory: () -> Unit,
    onCycleDisplayMode: (JsonDisplayMode) -> Unit,
    onSendMessage: (String, Boolean) -> Unit,
    onSendPhoto: (Uri, String?) -> Unit,
    onTogglePhoneLookup: (Boolean?) -> Unit,
    onPhoneLookupQueryChange: (String) -> Unit,
    onPhoneLookupFilterChange: (String, String) -> Unit,
    onSendAndFindPhone: (Boolean) -> Unit,
    onScanExistingPhone: () -> Unit,
    onClearPhoneLookup: () -> Unit,
    onPhoneCopied: (String) -> Unit,
    onCopiedJson: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var sendAsJsonBlock by remember { mutableStateOf(false) }
    var inspectingMessage by remember { mutableStateOf<TelegramMessageUi?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendPhoto(uri, inputText.takeIf { it.isNotBlank() })
            inputText = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(style = uiSettings.wallpaperStyle)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }

                    TelegramChatAvatar(
                        chatId = chatId,
                        title = chat?.title ?: "Chat $chatId",
                        chatType = chat?.chatType ?: "private",
                        size = 40.dp
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat?.title ?: "Chat $chatId",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = buildString {
                            if (!chat?.username.isNullOrBlank()) append("@${chat?.username} • ")
                            append("ID: $chatId • ${uiSettings.jsonDisplayMode.label}")
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!phoneLookupState.isPanelExpanded &&
                                phoneLookupState.queryInput.isBlank() &&
                                inputText.isNotBlank()
                            ) {
                                onPhoneLookupQueryChange(inputText)
                            }
                            onTogglePhoneLookup(null)
                        },
                        modifier = Modifier.testTag("toggle_phone_lookup_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (phoneLookupState.matches.isNotEmpty()) {
                                    Badge { Text("${phoneLookupState.matches.size}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = "Cari nomor telepon di balasan JSON",
                                tint = if (phoneLookupState.isPanelExpanded || phoneLookupState.matches.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val next = when (uiSettings.jsonDisplayMode) {
                                JsonDisplayMode.JSON_PRIMARY -> JsonDisplayMode.HYBRID
                                JsonDisplayMode.HYBRID -> JsonDisplayMode.CLASSIC
                                JsonDisplayMode.CLASSIC -> JsonDisplayMode.JSON_PRIMARY
                            }
                            onCycleDisplayMode(next)
                        },
                        modifier = Modifier.testTag("cycle_json_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = "Ganti mode tampilan JSON",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onRefreshHistory) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat riwayat Telegram"
                        )
                    }
                }
            }

            PhoneLookupPanel(
                state = phoneLookupState,
                uiSettings = uiSettings,
                onQueryChange = onPhoneLookupQueryChange,
                onFilterChange = onPhoneLookupFilterChange,
                onSendAndFind = { onSendAndFindPhone(sendAsJsonBlock) },
                onScanExisting = onScanExistingPhone,
                onClearResults = onClearPhoneLookup,
                onClosePanel = { onTogglePhoneLookup(false) },
                onPhoneCopied = onPhoneCopied,
                onJsonCopied = onCopiedJson
            )

            if (phoneLookupState.isWaitingForReply && !phoneLookupState.isPanelExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onTogglePhoneLookup(true) }
                        .testTag("auto_phone_watch_banner"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        val targetLabel = buildString {
                            append("Auto-cari balasan JSON: ")
                            append(phoneLookupState.targetName.ifBlank { phoneLookupState.queryInput })
                            if (phoneLookupState.phoneSuffix.isNotBlank()) {
                                append(" (...${phoneLookupState.phoneSuffix})")
                            }
                        }
                        Text(
                            text = targetLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Lihat",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val matchesByMessageId = remember(phoneLookupState.matches) {
                phoneLookupState.matches.groupBy { it.messageId }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    TelegramMessageBubble(
                        message = msg,
                        uiSettings = uiSettings,
                        matchedPhones = matchesByMessageId[msg.id].orEmpty(),
                        onInspectJson = { inspectingMessage = msg },
                        onCopiedJson = onCopiedJson
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = sendAsJsonBlock,
                            onClick = { sendAsJsonBlock = !sendAsJsonBlock },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    if (sendAsJsonBlock) "Blok JSON"
                                    else "Teks Biasa"
                                )
                            }
                        )
                        FilterChip(
                            selected = phoneLookupState.isPanelExpanded,
                            onClick = {
                                if (!phoneLookupState.isPanelExpanded &&
                                    phoneLookupState.queryInput.isBlank() &&
                                    inputText.isNotBlank()
                                ) {
                                    onPhoneLookupQueryChange(inputText)
                                }
                                onTogglePhoneLookup(null)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Cari Nomor") },
                            modifier = Modifier.testTag("chip_toggle_phone_lookup")
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = uiSettings.jsonProjectionMode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("attach_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Kirim foto ke Telegram",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    if (sendAsJsonBlock) "{\"reply\": \"Halo dari TeleCraft\"}"
                                    else "Tulis pesan ke Telegram..."
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input")
                        )

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText, sendAsJsonBlock)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            shape = CircleShape,
                            color = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Kirim pesan",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingMessage?.let { msg ->
        val projected = remember(msg.rawJson, uiSettings.jsonProjectionMode, uiSettings.customJsonFields) {
            JsonUtils.projectTelegramJson(
                msg.rawJson,
                uiSettings.jsonProjectionMode,
                uiSettings.customJsonFields
            )
        }
        JsonInspectorBottomSheet(
            title = if (msg.isOutgoing) "TDLib Message JSON (Terkirim)" else "TDLib Update JSON (Diterima)",
            projectedJson = projected,
            fullRawJson = msg.rawJson,
            syntaxTheme = uiSettings.jsonSyntaxTheme,
            fontSizeSp = uiSettings.monospaceFontSizeSp,
            onDismiss = { inspectingMessage = null },
            onCopied = onCopiedJson
        )
    }
}

@Composable
private fun WallpaperBackground(style: WallpaperStyle) {
    when (style) {
        WallpaperStyle.NEBULA -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Image(
                    painter = painterResource(id = R.drawable.img_chat_wallpaper_nebula),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f)
                )
            }
        }
        WallpaperStyle.MIDNIGHT_GRADIENT -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0B1120))
                        )
                    )
            )
        }
        WallpaperStyle.OCEAN_SLATE -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF082F49), Color(0xFF0F172A))
                        )
                    )
            )
        }
        WallpaperStyle.CLEAN_MINIMAL -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
}

@Composable
private fun TelegramMessageBubble(
    message: TelegramMessageUi,
    uiSettings: CustomUiSettings,
    matchedPhones: List<PhoneLookupMatch>,
    onInspectJson: () -> Unit,
    onCopiedJson: () -> Unit
) {
    val isOutgoing = message.isOutgoing
    val corner = uiSettings.bubbleCornerDp.dp
    val bubbleShape = RoundedCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomStart = if (isOutgoing) corner else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else corner
    )

    val bubbleContainerColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }

    val projectedJson = remember(
        message.rawJson,
        uiSettings.jsonProjectionMode,
        uiSettings.customJsonFields
    ) {
        JsonUtils.projectTelegramJson(
            message.rawJson,
            uiSettings.jsonProjectionMode,
            uiSettings.customJsonFields
        )
    }

    val showJsonDirectly = when (uiSettings.jsonDisplayMode) {
        JsonDisplayMode.JSON_PRIMARY -> !isOutgoing || uiSettings.includeSendReceiptJson || message.isJsonAutoReply
        JsonDisplayMode.HYBRID -> true
        JsonDisplayMode.CLASSIC -> false
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleContainerColor,
            tonalElevation = 2.dp,
            border = if (matchedPhones.isNotEmpty()) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clickable { onInspectJson() }
        ) {
            Column(modifier = Modifier.padding(11.dp)) {
                if (matchedPhones.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = matchedPhones.joinToString(" | ") { m ->
                                    val emailPart = m.email?.let { " • $it" }.orEmpty()
                                    "${m.matchedName}$emailPart • ${m.phoneNumber}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOutgoing) {
                            "Anda"
                        } else {
                            buildString {
                                append(message.senderName)
                                if (!message.senderUsername.isNullOrBlank()) append(" (@${message.senderUsername})")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = JsonUtils.formatTimestamp(message.timestampSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (message.localPhotoPath != null) {
                    Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = File(message.localPhotoPath),
                        contentDescription = "Foto Telegram",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                if (uiSettings.jsonDisplayMode != JsonDisplayMode.JSON_PRIMARY || isOutgoing) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showJsonDirectly) {
                    Spacer(Modifier.height(8.dp))
                    JsonCodeBlock(
                        jsonText = projectedJson,
                        syntaxTheme = uiSettings.jsonSyntaxTheme,
                        fontSizeSp = uiSettings.monospaceFontSizeSp,
                        headerLabel = if (isOutgoing) "TDLib Send JSON" else "Telegram Reply JSON",
                        collapsible = true,
                        initiallyExpanded = uiSettings.jsonDisplayMode == JsonDisplayMode.JSON_PRIMARY || !isOutgoing,
                        onInspectFull = onInspectJson,
                        onCopied = onCopiedJson
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "{ } Ketuk untuk lihat JSON Telegram",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
