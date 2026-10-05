package com.gvineon550coder.tvapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    // Системная кнопка «Назад» (пульт, телефон):
    //  - открыт полный экран  -> выйти из него
    //  - открыты настройки    -> закрыть их
    //  - иначе                -> стандартный выход из приложения
    BackHandler(enabled = state.playerFullscreen) {
        vm.exitPlayerFullscreen()
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

    // Режим полного экрана плеера — только видео
    if (state.playerFullscreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PlayerScreen(streamUrl = state.streamUrl)
            Button(
                onClick = { vm.exitPlayerFullscreen() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text("Выйти")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Верхняя панель
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

        // Основная область
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                channels = state.channels,
                onPick = vm::play,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )
            Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                PlayerScreen(streamUrl = state.streamUrl)
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
