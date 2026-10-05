package com.gvineon550coder.tvapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val AUTO_CLOSE_MS = 10 * 60 * 1000L

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val activity = LocalActivity.current

    val config = LocalConfiguration.current
    val isPhone = config.screenWidthDp < 900

    var showSleepDialog by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Тик каждую секунду:
    //  - Таймер сна срабатывает всегда
    //  - Автозакрытие по неактивности — ТОЛЬКО если канал НЕ играет
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val s = vm.state.value

            if (s.sleepDeadline > 0 && now >= s.sleepDeadline) {
                activity?.finishAffinity()
                break
            }
            if (!s.isPlaying && (now - s.lastActivity) > AUTO_CLOSE_MS) {
                activity?.finishAffinity()
                break
            }
            delay(1000)
        }
    }

    BackHandler(enabled = state.playerFullscreen) {
        if (state.fullscreenControlsVisible) vm.hideFullscreenControls()
        else vm.exitPlayerFullscreen()
    }
    BackHandler(enabled = state.showSettings && !state.playerFullscreen) {
        vm.closeSettings()
    }

    // Диалог таймера сна
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
                initialApiProxy = "",
                initialApiProxyEnabled = false,
                initialStreamProxy = "",
                initialStreamProxyEnabled = false,
                initialMaxHeight = state.maxHeight,
                onSave = { a, ae, s, se, mh -> vm.saveSettings(a, ae, s, se, mh) },
                onCancel = { vm.closeSettings() }
            )
        }
        return
    }

    // ПОЛНОЭКРАННЫЙ ПЛЕЕР
    if (state.playerFullscreen) {

        LaunchedEffect(state.fullscreenControlsVisible) {
            if (state.fullscreenControlsVisible) {
                delay(5000)
                vm.hideFullscreenControls()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                        vm.toggleFullscreenControls()
                    }
            )

            if (state.fullscreenControlsVisible) {
                val isFav = state.currentId?.let { it in state.favorites } == true
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xE6000000))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        state.currentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.resolution.isNotEmpty()) {
                        Text(
                            "🔸 ${state.resolution}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { vm.prevChannel() }) { Text("⏮ Назад") }
                        Button(onClick = { vm.nextChannel() }) { Text("Вперёд ⏭") }
                        Button(onClick = {
                            state.currentId?.let { vm.toggleFavorite(it) }
                        }) { Text(if (isFav) "★" else "☆") }
                        Box(Modifier.weight(1f))
                        Button(
                            onClick = { vm.hideFullscreenControls() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text("Скрыть") }
                        Button(
                            onClick = { vm.exitPlayerFullscreen() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) { Text("✕") }
                    }
                }
            }
        }
        return
    }

    // ОБЫЧНЫЙ ВИД
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
            IconBtn("⟳", { vm.loadChannels() }, enabled = !state.loading)
            IconBtn("⚙", { vm.openSettings() })
            IconBtn("⏱", { showSleepDialog = true })
            if (state.streamUrl != null) {
                IconBtn("⛶", { vm.togglePlayerFullscreen() })
            }
            IconBtn(
                "✕",
                { activity?.finishAffinity() },
                danger = true
            )
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
                        Button(
                            onClick = { vm.prevChannel() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("◀") }
                        Button(
                            onClick = { vm.nextChannel() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("▶") }
                        Button(
                            onClick = { state.currentId?.let { vm.toggleFavorite(it) } },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text(if (isFav) "★" else "☆") }
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
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
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
private fun IconBtn(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val container = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (danger) Color.White
                  else MaterialTheme.colorScheme.onSurfaceVariant
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = Modifier.height(44.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
