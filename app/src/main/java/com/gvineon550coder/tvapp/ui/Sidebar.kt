package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.gvineon550coder.tvapp.data.Channel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    channels: List<Channel>,
    search: String,
    onSearch: (String) -> Unit,
    onPick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(channels, search) {
        if (search.isBlank()) channels
        else channels.filter { it.title.contains(search, ignoreCase = true) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.category ?: "Без группы" }
    }

    Column(modifier = modifier.padding(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { M3Text("Поиск") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (cat, list) ->
                item(key = "cat_$cat") {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
                items(list, key = { it.id }) { ch ->
                    Card(onClick = { onPick(ch) }) {
                        Text(
                            ch.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
