package com.tv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    initialApiProxy: String,
    initialApiProxyEnabled: Boolean,
    initialStreamProxy: String,
    initialStreamProxyEnabled: Boolean,
    initialMaxHeight: Int,
    initialJsonSourceUrl: String,
    onSave: (String, Boolean, String, Boolean, Int, String) -> Unit,
    onCancel: () -> Unit
) {
    var apiProxy by remember { mutableStateOf(initialApiProxy) }
    var apiProxyEnabled by remember { mutableStateOf(initialApiProxyEnabled) }
    var streamProxy by remember { mutableStateOf(initialStreamProxy) }
    var streamProxyEnabled by remember { mutableStateOf(initialStreamProxyEnabled) }
    var maxHeight by remember { mutableIntStateOf(initialMaxHeight) }
    var jsonSourceUrl by remember { mutableStateOf(initialJsonSourceUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Text(
            "Источник каналов (JSON)",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = jsonSourceUrl,
            onValueChange = { jsonSourceUrl = it },
            label = { Text("https://.../channels.json") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Оставьте пустым — приложение будет использовать API Rutube напрямую.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = apiProxyEnabled, onCheckedChange = { apiProxyEnabled = it })
            Text("RU API proxy", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = apiProxy,
            onValueChange = { apiProxy = it },
            label = { Text("https://ip:port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = streamProxyEnabled, onCheckedChange = { streamProxyEnabled = it })
            Text("Stream proxy", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = streamProxy,
            onValueChange = { streamProxy = it },
            label = { Text("http://ip:port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Макс. высота потока: ${if (maxHeight == 0) "без ограничения" else "${maxHeight}p"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 480, 720, 1080, 1440).forEach { h ->
                Button(onClick = { maxHeight = h }) {
                    Text(if (h == 0) "∞" else "${h}p")
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Button(onClick = {
                onSave(apiProxy, apiProxyEnabled, streamProxy,
                    streamProxyEnabled, maxHeight, jsonSourceUrl)
            }) { Text("Сохранить") }
            Button(onClick = onCancel) { Text("Отмена") }
        }
    }
}
