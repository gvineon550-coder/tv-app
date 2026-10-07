package com.tv.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tv.player.data.Channel

private val FOCUS_BORDER = Color(0xFFFFD54F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(
    channels: List<Channel>,
    favorites: Set<String>,
    currentId: String?,
    onPick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocus: FocusRequester? = null
) {
    val favChannels = remember(channels, favorites) {
        channels.filter { it.id in favorites }
    }
    val grouped = remember(channels) {
        channels.groupBy { it.category ?: "Без группы" }
    }

    var isFirstRendered by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (favChannels.isNotEmpty()) {
            item(key = "cat_favorites") {
                CategoryHeader("⭐ Избранное", Color(0xFFFFC107))
            }
            itemsIndexed(favChannels, key = { _, ch -> "fav_${ch.id}" }) { idx, ch ->
                val shouldFocus = !isFirstRendered && idx == 0 && firstItemFocus != null
                if (shouldFocus) isFirstRendered = true
                ChannelRow(
                    ch = ch,
                    isFav = true,
                    isCurrent = ch.id == currentId,
                    onPick = onPick,
                    onToggleFavorite = onToggleFavorite,
                    focusRequester = if (shouldFocus) firstItemFocus else null
                )
            }
        }
        grouped.forEach { (cat, list) ->
            item(key = "cat_$cat") {
                CategoryHeader(cat, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(list, key = { _, ch -> "all_${ch.id}" }) { idx, ch ->
                val shouldFocus = !isFirstRendered && idx == 0 && firstItemFocus != null
                if (shouldFocus) isFirstRendered = true
                ChannelRow(
                    ch = ch,
                    isFav = ch.id in favorites,
                    isCurrent = ch.id == currentId,
                    onPick = onPick,
                    onToggleFavorite = onToggleFavorite,
                    focusRequester = if (shouldFocus) firstItemFocus else null
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    ch: Channel,
    isFav: Boolean,
    isCurrent: Boolean,
    onPick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    focusRequester: FocusRequester? = null
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
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(
                onClick = { onPick(ch) },
                onLongClick = { onToggleFavorite(ch) }
            )
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
