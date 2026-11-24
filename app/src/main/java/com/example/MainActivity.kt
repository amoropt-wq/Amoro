package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.tdlib.PhoneLookupState
import com.example.data.tdlib.TdAuthUiState
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ConnectTelegramScreen
import com.example.ui.screens.CustomizeStudioScreen
import com.example.ui.screens.JsonConsoleScreen
import com.example.ui.theme.TeleCraftTheme
import com.example.ui.viewmodel.TeleCraftViewModel
import kotlinx.coroutines.launch

private const val ROUTE_CHATS = "chats"
private const val ROUTE_CONSOLE = "console"
private const val ROUTE_CUSTOMIZE = "customize"
private const val ROUTE_CHAT_DETAIL = "chat/{chatId}"

private data class TopNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topNavTabs = listOf(
    TopNavTab(
        route = ROUTE_CHATS,
        labelRes = R.string.nav_chats,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_chats"
    ),
    TopNavTab(
        route = ROUTE_CONSOLE,
        labelRes = R.string.nav_console,
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal,
        testTag = "nav_console"
    ),
    TopNavTab(
        route = ROUTE_CUSTOMIZE,
        labelRes = R.string.nav_customize,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_customize"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: TeleCraftViewModel = viewModel(
                factory = TeleCraftViewModel.factory(context)
            )
            val uiSettings by viewModel.uiSettings.collectAsStateWithLifecycle()

            TeleCraftTheme(
                themeMode = uiSettings.themeMode,
                accentTheme = uiSettings.accentTheme
            ) {
                TeleCraftApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TeleCraftApp(viewModel: TeleCraftViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val uiSettings by viewModel.uiSettings.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val jsonLogs by viewModel.jsonLogs.collectAsStateWithLifecycle()
    val autoReplyRules by viewModel.autoReplyRules.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val jsonCopiedText = stringResource(R.string.json_copied)

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (authState !is TdAuthUiState.Ready) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ConnectTelegramScreen(
                    authState = authState,
                    isBusy = isBusy,
                    onRetryInit = viewModel::retryInit,
                    onSubmitPhone = viewModel::submitPhoneNumber,
                    onSubmitCode = viewModel::submitAuthCode,
                    onResendCode = viewModel::resendAuthCode,
                    onSubmitPassword = viewModel::submitPassword
                )
            }
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopLevelNav = currentRoute in setOf(ROUTE_CHATS, ROUTE_CONSOLE, ROUTE_CUSTOMIZE)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topNavTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        topNavTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CHATS,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_CHATS) {
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            ChatListScreen(
                                me = me,
                                isBusy = isBusy,
                                uiSettings = uiSettings,
                                chats = chats,
                                onChatClick = { chatId ->
                                    viewModel.markChatOpened(chatId)
                                    viewModel.loadChatHistory(chatId)
                                    navController.navigate("chat/$chatId")
                                },
                                onRefreshChats = viewModel::refreshChats,
                                onOpenTargetChat = { target ->
                                    viewModel.openChatByTarget(target) { openedChatId ->
                                        viewModel.loadChatHistory(openedChatId)
                                        navController.navigate("chat/$openedChatId")
                                    }
                                }
                            )
                        }
                    }

                    composable(ROUTE_CONSOLE) {
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            JsonConsoleScreen(
                                logs = jsonLogs,
                                uiSettings = uiSettings,
                                onClearLogs = viewModel::clearJsonLogs,
                                onCopiedJson = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(jsonCopiedText)
                                    }
                                }
                            )
                        }
                    }

                    composable(ROUTE_CUSTOMIZE) {
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            CustomizeStudioScreen(
                                me = me,
                                uiSettings = uiSettings,
                                autoReplyRules = autoReplyRules,
                                onAccentThemeChange = viewModel::setAccentTheme,
                                onThemeModeChange = viewModel::setThemeMode,
                                onWallpaperChange = viewModel::setWallpaperStyle,
                                onBubbleCornerChange = viewModel::setBubbleCornerDp,
                                onJsonDisplayModeChange = viewModel::setJsonDisplayMode,
                                onJsonProjectionModeChange = viewModel::setJsonProjectionMode,
                                onCustomJsonFieldsChange = viewModel::setCustomJsonFields,
                                onJsonSyntaxThemeChange = viewModel::setJsonSyntaxTheme,
                                onMonoFontSizeChange = viewModel::setMonoFontSizeSp,
                                onAutoReplyJsonChange = viewModel::setAutoReplyJsonEnabled,
                                onIncludeSendReceiptChange = viewModel::setIncludeSendReceiptJson,
                                onToggleRule = viewModel::toggleRule,
                                onDeleteRule = viewModel::deleteRule,
                                onAddRule = viewModel::addAutoReplyRule,
                                onLogOut = viewModel::logOut
                            )
                        }
                    }

                    composable(
                        route = ROUTE_CHAT_DETAIL,
                        arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                    ) { backStack ->
                        val chatId = backStack.arguments?.getLong("chatId") ?: 0L
                        val chat by viewModel.observeChat(chatId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        val messages by viewModel.observeMessages(chatId)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        val phoneLookupState by viewModel.observePhoneLookup(chatId)
                            .collectAsStateWithLifecycle(initialValue = PhoneLookupState(chatId = chatId))

                        LaunchedEffect(chatId) {
                            viewModel.markChatOpened(chatId)
                            viewModel.loadChatHistory(chatId)
                        }

                        ChatDetailScreen(
                            chat = chat,
                            chatId = chatId,
                            messages = messages,
                            uiSettings = uiSettings,
                            phoneLookupState = phoneLookupState,
                            onBack = { navController.popBackStack() },
                            onRefreshHistory = { viewModel.loadChatHistory(chatId) },
                            onCycleDisplayMode = viewModel::setJsonDisplayMode,
                            onSendMessage = { text, asJson ->
                                viewModel.sendMessage(chatId, text, asJson)
                            },
                            onSendPhoto = { uri, caption ->
                                viewModel.sendPhoto(chatId, uri, caption)
                            },
                            onTogglePhoneLookup = { expanded ->
                                viewModel.togglePhoneLookupPanel(chatId, expanded)
                            },
                            onPhoneLookupQueryChange = { query ->
                                viewModel.updatePhoneLookupQuery(chatId, query)
                            },
                            onPhoneLookupFilterChange = { name, suffix ->
                                viewModel.updatePhoneLookupFilter(chatId, name, suffix)
                            },
                            onSendAndFindPhone = { asJson ->
                                viewModel.sendAndFindPhone(chatId, messages, asJson)
                            },
                            onScanExistingPhone = {
                                viewModel.scanExistingMessagesForPhone(chatId, messages)
                            },
                            onClearPhoneLookup = {
                                viewModel.clearPhoneLookupResults(chatId)
                            },
                            onPhoneCopied = { phone ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Nomor disalin: $phone")
                                }
                            },
                            onCopiedJson = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(jsonCopiedText)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
