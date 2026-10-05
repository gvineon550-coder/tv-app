package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    if (state.showSettings) {
        SettingsScreen(
            initialApiProxy = "",
            initialApiProxyEnabled = false,
            initialStreamProxy = "",
            initialStreamProxyEnabled = false,
            initialMaxHeight = state.maxHeight,
            onSave = { a, ae, s, se, mh -> vm.saveSettings(a, ae, s, se, mh) },
            onCancel = { vm.closeSettings() }
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.width(320.dp).fillMaxHeight()
        ) {
            Sidebar(
                channels = state.channels,
                search = state.search,
                onSearch = vm::setSearch,
                onPick = vm::play,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.padding(8.dp)) {
                Button(onClick = { vm.openSettings() }) { Text("Настройки") }
                Button(
                    onClick = { vm.loadChannels() },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Обновить") }
            }
        }
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
                vm.loadProgram(id, java.time.LocalDate.now())
            },
            modifier = Modifier.width(280.dp).fillMaxHeight()
        )
    }
}
