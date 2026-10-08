package com.tv.player.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

private val FOCUS_BORDER = Color(0xFFFFD54F)

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    val config = LocalConfiguration.current
    val isPhone = config.screenWidthDp < 900

    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // ---------- ЭКРАН АКТИВАЦИИ ----------
    when (state.licenseState) {
        LicenseState.Checking -> {
            LicenseCheckingScreen()
            return
        }
        LicenseState.Denied, LicenseState.Unknown -> {
            LicenseActivationScreen(
                deviceId = state.deviceId,
                reason = state.licenseReason,
                onRecheck = { vm.recheckLicense() },
                onExit = { activity?.finishAffinity() }
            )
            return
        }
        LicenseState.Allowed -> {
            // Продолжаем обычный HomeScreen
        }
    }

    LaunchedEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
    }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }

    var showSleepDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val overlayFirstItem = remember { FocusRequester() }
    val panelFirstBtn = remember { FocusRequester() }
    val fullscreenBox = remember { FocusRequester() }
    val firstChannelFocus = remember { FocusRequester() }

    if (state.showPinDialog) {
        PinDialog(
            mode = state.pinDialogMode,
            hasPin = state.hasPin,
            error = state.pinError,
            onSubmit = { vm.submitPin(it) },
            onCancel = { vm.cancelPinDialog() }
        )
    }

    LaunchedEffect(state.channels.size) {
        if (state.channels.isNotEmpty() && !state.playerFullscreen) {
            delay(300)
            runCatching { firstChannelFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val s = vm.state.value
            if (s.sleepDeadline > 0 && now >= s.sleepDeadline) {
                activity?.finishAffinity(); break
            }
            delay(1000)
        }
    }

    LaunchedEffect(state.playerFullscreen) {
        if (state.playerFullscreen) {
            delay(300)
            runCatching { fullscreenBox.requestFocus() }
            delay(700)
            runCatching { fullscreenBox.requestFocus() }
        }
    }

    LaunchedEffect(state.showChannelOverlay, state.channels.size) {
        if (state.showChannelOverlay && state.channels.isNotEmpty()) {
            delay(200)
            runCatching { overlayFirstItem.requestFocus() }
        }
    }

    LaunchedEffect(state.showChannelOverlay) {
        if (!state.showChannelOverlay && state.playerFullscreen) {
            delay(200)
            runCatching { fullscreenBox.requestFocus() }
        }
    }

    LaunchedEffect(state.fullscreenControlsVisible) {
        if (state.fullscreenControlsVisible) {
            delay(150)
            runCatching { panelFirstBtn.requestFocus() }
        }
    }

    BackHandler(enabled = state.playerFullscreen && state.showChannelOverlay) {
        vm.closeChannelOverlay()
    }
    BackHandler(enabled = state.playerFullscreen && !state.showChannelOverlay
            && state.fullscreenControlsVisible) {
        vm.hideFullscreenControls()
    }
    BackHandler(enabled = state.playerFullscreen && !state.showChannelOverlay
            && !state.fullscreenControlsVisible) {
        vm.exitPlayerFullscreen()
    }
    BackHandler(enabled = state.showSettings && !state.playerFullscreen) {
        vm.closeSettings()
    }

    // ---------- ТАЙМЕР СНА (расширенный) ----------
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                Column {
                    listOf(
                        0 to "Выключить",
                        15 to "15 минут",
                        30 to "30 минут",
                        60 to "1 час",
                        120 to "2 часа",
                        240 to "4 часа",
                        360 to "6 часов",
                        480 to "8 часов",
                        720 to "12 часов"
                    ).forEach { (m, label) ->
                        TextButton(onClick = {
                            vm.setSleepTimer(m)
                            showSleepDialog = false
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (state.showSettings) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                initialApiProxy = "", initialApiProxyEnabled = false,
                initialStreamProxy = "", initialStreamProxyEnabled = false,
                initialMaxHeight = state.maxHeight,
                initialJsonSourceUrl = state.jsonSourceUrl,
                hasPin = state.hasPin,
                onSave = { a, ae, s, se, mh, jsu -> vm.saveSettings(a, ae, s, se, mh, jsu) },
                onCancel = { vm.closeSettings() },
                onSetPin = { vm.requestSetPin() },
                onRemovePin = { vm.requestRemovePin() }
            )
        }
        return
    }

    // ---------- ПОЛНОЭКРАННЫЙ ПЛЕЕР ----------
    if (state.playerFullscreen) {

        LaunchedEffect(state.fullscreenControlsVisible) {
            if (state.fullscreenControlsVisible) {
                delay(5000)
                vm.hideFullscreenControls()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(fullscreenBox)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (state.showChannelOverlay) return@onPreviewKeyEvent false
                    if (state.fullscreenControlsVisible) return@onPreviewKeyEvent false

                    when (e.key) {
                        Key.DirectionLeft, Key.ChannelDown -> { vm.prevChannel(); true }
                        Key.DirectionRight, Key.ChannelUp -> { vm.nextChannel(); true }
                        Key.DirectionUp, Key.DirectionDown -> {
                            vm.openChannelOverlay(); true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            vm.toggleFullscreenControls(); true
                        }
                        else -> false
                    }
                }
        ) {
            PlayerScreen(
                streamUrl = state.streamUrl,
                onResolutionChanged = { vm.setResolution(it) },
                onPlayingChanged = { vm.setPlaying(it) },
                onFatalError = { vm.refreshCurrentStream() },
                showDiagnostics = state.showDiagnostics,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            vm.registerActivity()
                            if (!state.showChannelOverlay) vm.toggleFullscreenControls()
                        }
                    }
            )

            if (state.fullscreenControlsVisible && !state.showChannelOverlay) {
                val isFav = state.currentId?.let { it in state.favorites } == true
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xE6000000))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        state.currentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.resolution.isNotEmpty()) {
                            Text(
                                "🔸 ${state.resolution}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.LightGray
                            )
                        }
                        Text(
                            "← → канал · ↑ ↓ список · OK панель",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvButton("⏮", "Предыдущий канал", focusRequester = panelFirstBtn,
                            onClick = { vm.prevChannel() })
                        TvButton("⏭", "Следующий канал",
                            onClick = { vm.nextChannel() })
                        TvButton("☰", "Список каналов",
                            onClick = { vm.openChannelOverlay() })
                        TvButton(if (isFav) "★" else "☆", "Избранное",
                            highlight = isFav,
                            onClick = { state.currentId?.let { vm.toggleFavorite(it) } })
                        TvButton("⛶", "Свернуть",
                            onClick = { vm.exitPlayerFullscreen() })
                        TvButton("🐞", "Диагностика",
                            highlight = state.showDiagnostics,
                            onClick = { vm.toggleDiagnostics() })
                        Box(Modifier.weight(1f))
                        TvButton("✕ Выйти", "Закрыть приложение",
                            danger = true,
                            onClick = { activity?.finishAffinity() })
                    }
                }
            }

            if (state.showChannelOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA000000))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                vm.closeChannelOverlay()
                            }
                        }
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(400.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Text(
                                "📺 Выбор канала",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (state.channels.isEmpty()) {
                                Text(
                                    "Каналы не загружены",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(
                                        state.channels,
                                        key = { _, ch -> ch.id }
                                    ) { idx, ch ->
                                        val isCurrent = ch.id == state.currentId
                                        var isFocused by remember { mutableStateOf(false) }
                                        val bg = when {
                                            isFocused -> MaterialTheme.colorScheme.primary
                                            isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val fg = when {
                                            isFocused -> MaterialTheme.colorScheme.onPrimary
                                            isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = bg),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (idx == 0)
                                                        Modifier.focusRequester(overlayFirstItem)
                                                    else Modifier
                                                )
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .focusable()
                                                .clickable { vm.play(ch) }
                                                .then(
                                                    if (isFocused) Modifier.border(
                                                        2.dp, FOCUS_BORDER,
                                                        RoundedCornerShape(8.dp)
                                                    ) else Modifier
                                                )
                                        ) {
                                            Text(
                                                text = ch.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isCurrent || isFocused)
                                                    FontWeight.Bold else FontWeight.Normal,
                                                color = fg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ---------- ОБЫЧНЫЙ ВИД ----------
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TvButton("⟳", "Обновить",
                onClick = { vm.loadChannels() }, enabled = !state.loading)
            TvButton("⚙", "Настройки",
                onClick = { vm.onSettingsClick() })
            TvButton("⏱", "Таймер сна", onClick = { showSleepDialog = true })
            TvButton("🐞", "Диагностика",
                highlight = state.showDiagnostics,
                onClick = { vm.toggleDiagnostics() })
            Box(Modifier.weight(1f))
            TvButton("✕", "Выход", danger = true,
                onClick = { activity?.finishAffinity() })
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                channels = state.channels,
                favorites = state.favorites,
                currentId = state.currentId,
                onPick = { ch ->
                    vm.play(ch)
                    if (isPhone) vm.togglePlayerFullscreen()
                },
                onToggleFavorite = { ch -> vm.toggleFavorite(ch.id) },
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                firstItemFocus = firstChannelFocus
            )

            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PlayerScreen(
                        streamUrl = state.streamUrl,
                        onResolutionChanged = { vm.setResolution(it) },
                        onPlayingChanged = { vm.setPlaying(it) },
                        onFatalError = { vm.refreshCurrentStream() },
                        showDiagnostics = state.showDiagnostics
                    )
                }
                if (state.streamUrl != null) {
                    val isFav = state.currentId?.let { it in state.favorites } == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            state.currentTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.resolution.isNotEmpty()) {
                            Text(
                                state.resolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TvButton("⛶", "На весь экран",
                            onClick = { vm.togglePlayerFullscreen() })
                        TvButton("◀", "Предыдущий канал",
                            onClick = { vm.prevChannel() })
                        TvButton("▶", "Следующий канал",
                            onClick = { vm.nextChannel() })
                        TvButton(if (isFav) "★" else "☆", "Избранное",
                            highlight = isFav,
                            onClick = { state.currentId?.let { vm.toggleFavorite(it) } })
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                state.status.ifBlank { "Готово" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state.sleepDeadline > 0) {
                val leftSec = ((state.sleepDeadline - now) / 1000).coerceAtLeast(0)
                val mm = leftSec / 60
                val ss = leftSec % 60
                Text(
                    "⏱ %02d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ============================================================
// ЭКРАН ПРОВЕРКИ ЛИЦЕНЗИИ
// ============================================================

@Composable
private fun LicenseCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                "Проверка лицензии…",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ============================================================
// ЭКРАН АКТИВАЦИИ
// ============================================================

@Composable
private fun LicenseActivationScreen(
    deviceId: String,
    reason: String,
    onRecheck: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "🔒 Устройство не активировано",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                "Попросите владельца добавить этот ID\nв список разрешённых устройств:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = deviceId.ifBlank { "----" },
                    fontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                )
            }

            if (reason.isNotBlank()) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onRecheck,
                modifier = Modifier.width(280.dp)
            ) {
                Text("Проверить ещё раз", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(onClick = onExit) {
                Text("Выйти")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================================
// PIN-ДИАЛОГ
// ============================================================

@Composable
private fun PinDialog(
    mode: PinDialogMode,
    hasPin: Boolean,
    error: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    val title = when (mode) {
        PinDialogMode.OPEN_SETTINGS -> "Введите PIN"
        PinDialogMode.SET_PIN -> "Установить PIN"
        PinDialogMode.REMOVE_PIN -> "Снять PIN"
        PinDialogMode.NONE -> ""
    }

    val hint = when (mode) {
        PinDialogMode.SET_PIN -> "Минимум 4 цифры"
        PinDialogMode.REMOVE_PIN -> "Введите текущий PIN"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = { onCancel() },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hint.isNotEmpty()) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (input.isEmpty()) "○ ○ ○ ○"
                           else input.map { "●" }.joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (error.isNotEmpty()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                PinKeyboard(
                    onDigit = { d -> if (input.length < 6) input += d },
                    onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    onSubmit = { if (input.isNotEmpty()) onSubmit(input) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (input.isNotEmpty()) onSubmit(input) }) {
                Text("ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancel() }) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun PinKeyboard(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "ОК")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in row) {
                    PinKey(
                        label = key,
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "ОК" -> onSubmit()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) FOCUS_BORDER else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = fg
        )
    }
}

// ============================================================
// КНОПКА
// ============================================================

@Composable
private fun TvButton(
    label: String,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    highlight: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val container = when {
        danger -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        highlight -> Color(0xFF3A2F00)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        danger || isFocused -> Color.White
        highlight -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderMod = if (isFocused)
        Modifier.border(2.dp, FOCUS_BORDER, RoundedCornerShape(24.dp))
    else Modifier

    val baseMod = Modifier
        .height(44.dp)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { isFocused = it.isFocused }
        .then(borderMod)

    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = baseMod
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
