package com.pcscreencast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RemoteControls(
    onAction: (RemoteAction) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(RemoteAction.DoubleClick) }, modifier = Modifier.weight(1f)) { Text("2×") }
                Button(onClick = { onAction(RemoteAction.MiddleClick) }, modifier = Modifier.weight(1f)) { Text("Mid") }
                Button(onClick = { onAction(RemoteAction.AltTab) }, modifier = Modifier.weight(1f)) { Text("Alt+Tab") }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(RemoteAction.Copy) }, modifier = Modifier.weight(1f)) { Text("Copy") }
                Button(onClick = { onAction(RemoteAction.Paste) }, modifier = Modifier.weight(1f)) { Text("Paste") }
                Button(onClick = { onAction(RemoteAction.TaskMgr) }, modifier = Modifier.weight(1f)) { Text("Task") }
            }

            Spacer(Modifier.height(16.dp))

            Text("Media", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(RemoteAction.VolDown) }, modifier = Modifier.weight(1f)) { Text("Vol-") }
                Button(onClick = { onAction(RemoteAction.Mute) }, modifier = Modifier.weight(1f)) { Text("Mute") }
                Button(onClick = { onAction(RemoteAction.VolUp) }, modifier = Modifier.weight(1f)) { Text("Vol+") }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(RemoteAction.Prev) }, modifier = Modifier.weight(1f)) { Text("Prev") }
                Button(onClick = { onAction(RemoteAction.PlayPause) }, modifier = Modifier.weight(1f)) { Text("Play") }
                Button(onClick = { onAction(RemoteAction.Next) }, modifier = Modifier.weight(1f)) { Text("Next") }
            }
        }
    }
}

sealed interface RemoteAction {
    data object DoubleClick : RemoteAction
    data object MiddleClick : RemoteAction
    data object AltTab : RemoteAction
    data object Copy : RemoteAction
    data object Paste : RemoteAction
    data object TaskMgr : RemoteAction

    data object VolDown : RemoteAction
    data object Mute : RemoteAction
    data object VolUp : RemoteAction
    data object Prev : RemoteAction
    data object PlayPause : RemoteAction
    data object Next : RemoteAction
}
