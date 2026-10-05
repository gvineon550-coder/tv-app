package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gvineon550coder.tvapp.data.Channel

private val FOCUS_BORDER = Color(0xFFFFD54F)

@Composable
fun Sidebar(
    channels: List<Channel>,
    favorites: Set<String>,
    currentId: String?,
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
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (favChannels.isNotEmpty()) {
            item(key = "cat_favorites") {
                CategoryHeader("⭐ Избранное", Color(0xFFFFC107))
            }
            items(favChannels, key = { "fav_${it.id}" }) { ch ->
                ChannelRow(ch, true, ch.id == currentId, onPick, onToggleFavorite)
            }
        }
        grouped.forEach { (cat, list) ->
            item(key = "cat_$cat") {
                CategoryHeader(cat, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(list, key = { "all_${it.id}" }) { ch ->
                ChannelRow(ch, ch.id in favorites, ch.id == currentId, onPick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun CategoryHeader(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun ChannelRow(
    ch: Channel,
    isFav: Boolean,
    isCurrent: Boolean,
    onPick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onBg = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPick(ch) }
            .then(
                if (isFocused) Modifier.border(
                    2.dp, FOCUS_BORDER, RoundedCornerShape(8.dp)
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (ch.avatar.isNotBlank()) {
                    AsyncImage(
                        model = ch.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Text(
                        ch.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                ch.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent || isFocused) FontWeight.Bold else FontWeight.Normal,
                color = onBg,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )

            Text(
                if (isFav) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (isFav) Color(0xFFFFC107) else Color.Gray,
                modifier = Modifier.padding(start = 6.dp, end = 2.dp)
            )
        }
    }
}
