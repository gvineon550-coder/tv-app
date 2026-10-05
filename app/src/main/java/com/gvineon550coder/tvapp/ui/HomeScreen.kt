package com.gvineon550coder.tvapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    val config = LocalConfiguration.current
    val isPhone = config.screenWidthDp < 900

    BackHandler(enabled = state.playerFullscreen) {
        if (state.fullscreenControlsVisible) {
            vm.hideFullscreenControls()
        } else {
            vm.exitPlayerFullscreen()
        }
    }
    BackHandler(enabled = state.showSettings && !state.playerFullscreen) {
        vm.closeSettings()
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

    // ПОЛНОЭКРАННЫЙ ПЛЕЕР: только видео + всплывающая панель
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
                useController = false,
                modifier = Modifier.fillMaxSize()
            )

            // Прозрачный слой — ловит тап/OK и показывает панель
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { vm.toggleFullscreenControls() }
            )

            // Всплывающая панель снизу
            if (state.fullscreenControlsVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        state.currentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    if (state.resolution.isNotEmpty()) {
                        Text(
                            "Разрешение: ${state.resolution}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { vm.prevChannel() }) { Text("◀ Предыдущий") }
                        Button(onClick = { vm.nextChannel() }) { Text("Следующий ▶") }
                        Button(onClick = { vm.hideFullscreenControls() }) { Text("Скрыть") }
                        Button(onClick = { vm.exitPlayerFullscreen() }) { Text("Выйти") }
                    }
                }
            }
        }
        return
    }

    // ОБЫЧНЫЙ ВИД
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.loadChannels() }) { Text("Обновить") }
            Button(onClick = { vm.openSettings() }) { Text("Настройки") }
            if (state.streamUrl != null) {
                Button(onClick = { vm.togglePlayerFullscreen() }) {
                    Text("На весь экран")
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                channels = state.channels,
                onPick = { ch ->
                    vm.play(ch)
                    if (isPhone) vm.togglePlayerFullscreen()
                },
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )

            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PlayerScreen(
                        streamUrl = state.streamUrl,
                        onResolutionChanged = { vm.setResolution(it) }
                    )
                }
                if (state.streamUrl != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                state.currentTitle,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (state.resolution.isNotEmpty()) {
                                Text(
                                    "Разрешение: ${state.resolution}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { vm.prevChannel() }) { Text("◀") }
                            Button(onClick = { vm.nextChannel() }) { Text("▶") }
                        }
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
    }
}
