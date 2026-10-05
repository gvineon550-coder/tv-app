package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gvineon550coder.tvapp.data.Program
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ProgramPanel(
    programs: List<Program>,
    date: LocalDate,
    loading: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            "📅 Программа",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Button(
                onClick = onPrevDay,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("◀", style = MaterialTheme.typography.bodySmall) }

            Text(
                text = date.format(DateTimeFormatter.ofPattern("dd.MM")),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Button(
                onClick = onNextDay,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("▶", style = MaterialTheme.typography.bodySmall) }
        }

        when {
            loading -> Text(
                "Загрузка…",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            programs.isEmpty() -> Text(
                "Нет данных",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val now = LocalDateTime.now()
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(programs) { p ->
                val current = p.start != null && p.end != null &&
                        !now.isBefore(p.start) && now.isBefore(p.end)
                Column(modifier = Modifier.padding(2.dp)) {
                    Text(
                        text = "${formatTime(p.start)}–${formatTime(p.end)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = p.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatTime(t: LocalDateTime?): String {
    if (t == null) return "—"
    return "%02d:%02d".format(t.hour, t.minute)
}
