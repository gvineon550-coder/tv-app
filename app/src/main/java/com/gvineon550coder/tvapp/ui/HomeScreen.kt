package com.gvineon550coder.tvapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val AUTO_CLOSE_MS = 10 * 60 * 1000L
private val FOCUS_BORDER = Color(0xFFFFD54F)

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    val config = LocalConfiguration.current
    val isPhone = config.screenWidthDp < 900

    val context = LocalContext.current
    val activity = context as? Activity

    var showSleepDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val overlayFocus = remember { FocusRequester() }
    val panelFirstBtn = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val s = vm.state.value
            if (s.sleepDeadline > 0 && now >= s.sleepDeadline) {
                activity?.finishAffinity(); break
            }
            if (!s.isPlaying && (now - s.lastActivity) > AUTO_CLOSE_MS) {
                activity?.finishAffinity(); break
            }
            delay(1000)
        }
    }

    LaunchedEffect(state.showChannelOverlay) {
        if (state.showChannelOverlay) {
            delay(100)
            runCatching { overlayFocus.requestFocus() }
        }
    }

    LaunchedEffect(state.fullscreenControlsVisible) {
        if (state.fullscreenControlsVisible) {
            delay(100)
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

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                Column {
                    listOf(0 to "Выключить", 15 to "15 минут", 30 to "30 минут",
                        60 to "1 час", 120 to "2 часа").forEach { (m, label) ->
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
                onSave = { a, ae, s, se, mh -> vm.saveSettings(a, ae, s, se, mh) },
                onCancel = { vm.closeSettings() }
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
                useController = false,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        vm.registerActivity()
                        if (!state.showChannelOverlay) vm.toggleFullscreenControls()
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
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { vm.closeChannelOverlay() }
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(380.dp)
                            .focusRequester(overlayFocus)
                            .focusable(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            Text(
                                "Выбор канала",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(8.dp)
                            )
                            Sidebar(
                                channels = state.channels,
                                favorites = state.favorites,
                                currentId = state.currentId,
                                onPick = { ch -> vm.play(ch) },
                                onToggleFavorite = { ch -> vm.toggleFavorite(ch.id) },
                                modifier = Modifier.fillMaxSize()
                            )
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
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.weight(1f).height(52.dp)
            )
            TvButton("⟳", "Обновить",
                onClick = { vm.loadChannels() }, enabled = !state.loading)
            TvButton("⚙", "Настройки", onClick = { vm.openSettings() })
            TvButton("⏱", "Таймер сна", onClick = { showSleepDialog = true })
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
                    .padding(horizontal = 4.dp)
            )

            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PlayerScreen(
                        streamUrl = state.streamUrl,
                        onResolutionChanged = { vm.setResolution(it) },
                        onPlayingChanged = { vm.setPlaying(it) }
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

            ProgramPanel(
                programs = state.program,
                date = state.programDate,
                loading = state.programLoading,
                onPrevDay = {
                    val id = state.currentId ?: return@ProgramPanel
                    vm.loadProgram(id, state.programDate.minusDays(1))
                },
                onNextDay = {
                    val id = state.currentId ?: return@ProgramPanel
                    vm.loadProgram(id, state.programDate.plusDays(1))
                },
                onToday = {
                    val id = state.currentId ?: return@ProgramPanel
                    vm.loadProgram(id, LocalDate.now())
                },
                modifier = Modifier.width(260.dp).fillMaxHeight()
            )
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
