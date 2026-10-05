package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedTextField
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialApiProxy: String,
    initialApiProxyEnabled: Boolean,
    initialStreamProxy: String,
    initialStreamProxyEnabled: Boolean,
    initialMaxHeight: Int,
    onSave: (String, Boolean, String, Boolean, Int) -> Unit,
    onCancel: () -> Unit
) {
    var apiProxy by remember { mutableStateOf(initialApiProxy) }
    var apiProxyEnabled by remember { mutableStateOf(initialApiProxyEnabled) }
    var streamProxy by remember { mutableStateOf(initialStreamProxy) }
    var streamProxyEnabled by remember { mutableStateOf(initialStreamProxyEnabled) }
    var maxHeight by remember { mutableStateOf(initialMaxHeight) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = apiProxyEnabled, onCheckedChange = { apiProxyEnabled = it })
            Text("RU API proxy", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = apiProxy,
            onValueChange = { apiProxy = it },
            label = { Text("https://ip:port") },
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
                    streamProxyEnabled, maxHeight)
            }) { Text("Сохранить") }
            Button(onClick = onCancel) { Text("Отмена") }
        }
    }
}
