package com.gvineon550coder.tvapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gvineon550coder.tvapp.data.Channel

@Composable
fun Sidebar(
    channels: List<Channel>,
    onPick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(channels) {
        channels.groupBy { it.category ?: "Без группы" }
    }

    LazyColumn(
        modifier = modifier,
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
                Card(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ch) }
                ) {
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
