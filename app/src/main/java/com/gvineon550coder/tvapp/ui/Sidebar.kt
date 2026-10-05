package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gvineon550coder.tvapp.data.Channel

@Composable
fun Sidebar(
    channels: List<Channel>,
    favorites: Set<String>,
    onPick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val favChannels = remember(channels, favorites) {
        channels.filter { it.id in favorites }
    }
    val grouped = remember(channels) {
        channels.groupBy { it.category ?: "Без группы" }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Группа "Избранное" — только если есть избранные
        if (favChannels.isNotEmpty()) {
            item(key = "cat_favorites") {
                Text(
                    "⭐ Избранное",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFC107),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
            items(favChannels, key = { "fav_${it.id}" }) { ch ->
                ChannelRow(ch, true, onPick, onToggleFavorite)
            }
        }

        // Обычные группы
        grouped.forEach { (cat, list) ->
            item(key = "cat_$cat") {
                Text(
                    cat,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
            items(list, key = { "all_${it.id}" }) { ch ->
                ChannelRow(ch, ch.id in favorites, onPick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun ChannelRow(
    ch: Channel,
    isFav: Boolean,
    onPick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    Card(
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(ch) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ch.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (isFav) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (isFav) Color(0xFFFFC107) else Color.Gray,
                modifier = Modifier
                    .clickable { onToggleFavorite(ch) }
                    .padding(start = 8.dp, end = 4.dp)
            )
        }
    }
}
