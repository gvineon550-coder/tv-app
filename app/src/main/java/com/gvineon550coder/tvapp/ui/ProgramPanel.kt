package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.gvineon550coder.tvapp.data.Program
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalTvMaterial3Api::class)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onPrevDay) { Text("◀") }
            Text(
                text = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Button(onClick = onNextDay) { Text("▶") }
            Button(onClick = onToday, modifier = Modifier.padding(start = 8.dp)) {
                Text("Сегодня")
            }
        }

        when {
            loading -> Text("Загрузка программы…", modifier = Modifier.padding(top = 8.dp))
            programs.isEmpty() -> Text(
                "На эту дату программы нет",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val now = LocalDateTime.now()
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(programs) { p ->
                val current = p.start != null && p.end != null &&
                        !now.isBefore(p.start) && now.isBefore(p.end)
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "${formatTime(p.start)}–${formatTime(p.end)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = p.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
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
