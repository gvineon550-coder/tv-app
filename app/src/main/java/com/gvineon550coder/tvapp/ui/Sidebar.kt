package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedTextField
import androidx.tv.material3.Text
import coil.compose.AsyncImage
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
            label = { Text("Поиск") },
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            AsyncImage(
                                model = ch.category,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                ch.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
