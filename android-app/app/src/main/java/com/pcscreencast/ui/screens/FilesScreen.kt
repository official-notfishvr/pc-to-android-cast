package com.pcscreencast.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pcscreencast.ui.model.FsItem

@Composable
fun FilesScreen(
    path: String,
    items: List<FsItem>,
    onItemClick: (FsItem) -> Unit
) {
    Column {
        Text(
            "Files: /$path",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )
        LazyColumn {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    val label = if (item.type == "dir") "📁 ${item.name}" else "${item.name} (${item.size ?: 0} bytes)"
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
