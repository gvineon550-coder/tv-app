package com.tv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Хардкод PIN для показа URL-адреса JSON-источника.
// Чтобы изменить — просто поменяй строку.
private const val MASTER_PIN = "103610"

@Composable
fun SettingsScreen(
    initialApiProxy: String,
    initialApiProxyEnabled: Boolean,
    initialStreamProxy: String,
    initialStreamProxyEnabled: Boolean,
    initialMaxHeight: Int,
    initialJsonSourceUrl: String,
    hasPin: Boolean,
    onSave: (String, Boolean, String, Boolean, Int, String) -> Unit,
    onCancel: () -> Unit,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit
) {
    var apiProxy by remember { mutableStateOf(initialApiProxy) }
    var apiProxyEnabled by remember { mutableStateOf(initialApiProxyEnabled) }
    var streamProxy by remember { mutableStateOf(initialStreamProxy) }
    var streamProxyEnabled by remember { mutableStateOf(initialStreamProxyEnabled) }
    var maxHeight by remember { mutableIntStateOf(initialMaxHeight) }
    var jsonSourceUrl by remember { mutableStateOf(initialJsonSourceUrl) }

    // ---------- Скрытие URL ----------
    var urlRevealed by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        // ---------- Родительский контроль (PIN на настройки) ----------
        Text(
            "Родительский контроль",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (hasPin) "PIN установлен — настройки защищены"
            else "PIN не установлен — настройки открыты",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { if (hasPin) onRemovePin() else onSetPin() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasPin) MaterialTheme.colorScheme.error
                                 else MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Text(if (hasPin) "Снять PIN" else "Установить PIN")
        }

        // ---------- Источник каналов ----------
        Text(
            "Источник каналов (JSON)",
            style = MaterialTheme.typography.titleMedium
        )

        if (urlRevealed) {
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
        } else {
            OutlinedTextField(
                value = "●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●",
                onValueChange = {},
                enabled = false,
                label = { Text("URL скрыт") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { showPinDialog = true }) {
                Text("Показать URL")
            }
        }

        // ---------- RU API proxy ----------
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

        // ---------- Stream proxy ----------
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

        // ---------- Max height ----------
        Text("Макс. высота потока: ${if (maxHeight == 0) "без ограничения" else "${maxHeight}p"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 480, 720, 1080, 1440).forEach { h ->
                Button(onClick = { maxHeight = h }) {
                    Text(if (h == 0) "∞" else "${h}p")
                }
            }
        }

        // ---------- Сохранить / Отмена ----------
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

    // ---------- Диалог ввода мастер-PIN ----------
    if (showPinDialog) {
        MasterPinDialog(
            onSuccess = {
                urlRevealed = true
                showPinDialog = false
            },
            onCancel = { showPinDialog = false }
        )
    }
}

// ============================================================
// МАСТЕР-PIN ДИАЛОГ
// ============================================================

@Composable
private fun MasterPinDialog(
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val check = {
        if (input == MASTER_PIN) onSuccess()
        else {
            error = "Неверный PIN"
            input = ""
        }
    }

    AlertDialog(
        onDismissRequest = { onCancel() },
        title = { Text("Введите PIN") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (input.isEmpty()) "○ ○ ○ ○ ○ ○"
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
                    onSubmit = { check() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { check() }) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = { onCancel() }) { Text("Отмена") }
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
    val focusBorder = Color(0xFFFFD54F)

    val bg = if (isFocused) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isFocused) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) focusBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}
