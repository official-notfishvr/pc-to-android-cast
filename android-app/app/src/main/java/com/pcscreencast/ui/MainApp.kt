package com.pcscreencast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcscreencast.PcKeyCodes
import com.pcscreencast.ui.screens.ConnectScreen
import com.pcscreencast.ui.screens.FilesScreen
import com.pcscreencast.ui.screens.PairingDialog
import com.pcscreencast.ui.screens.RemoteAction
import com.pcscreencast.ui.screens.RemoteControls
import com.pcscreencast.ui.screens.StreamScreen
import com.pcscreencast.ui.theme.PcScreenCastTheme

@Composable
fun MainApp(vm: MainViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val fsPath by vm.fsPath.collectAsState()
    val fsItems by vm.fsItems.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(ui.status) {
        val s = ui.status
        if (!s.isNullOrBlank()) snack.showSnackbar(s)
    }

    PcScreenCastTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) }
        ) { padding ->
            if (!ui.isStreaming) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectScreen(
                        ip = ui.ip,
                        port = ui.port,
                        status = null,
                        onIpChange = vm::setIp,
                        onPortChange = vm::setPort,
                        onConnect = vm::connect
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    Text("Streaming", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))

                    StreamScreen(
                        frame = ui.frame,
                        stream = vm.getStream(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            RemoteControls { action ->
                                when (action) {
                                    RemoteAction.DoubleClick -> vm.sendDoubleClick()
                                    RemoteAction.MiddleClick -> vm.sendMiddleClick()
                                    RemoteAction.AltTab -> vm.sendKeyCombo(PcKeyCodes.VK_TAB, alt = true)
                                    RemoteAction.Copy -> vm.sendKeyCombo(PcKeyCodes.VK_C, ctrl = true)
                                    RemoteAction.Paste -> vm.sendKeyCombo(PcKeyCodes.VK_V, ctrl = true)
                                    RemoteAction.TaskMgr -> vm.sendKeyCombo(PcKeyCodes.VK_ESCAPE, ctrl = true, shift = true)

                                    RemoteAction.VolDown -> vm.sendKeyTap(PcKeyCodes.VK_VOLUME_DOWN)
                                    RemoteAction.Mute -> vm.sendKeyTap(PcKeyCodes.VK_VOLUME_MUTE)
                                    RemoteAction.VolUp -> vm.sendKeyTap(PcKeyCodes.VK_VOLUME_UP)
                                    RemoteAction.Prev -> vm.sendKeyTap(PcKeyCodes.VK_MEDIA_PREV)
                                    RemoteAction.PlayPause -> vm.sendKeyTap(PcKeyCodes.VK_MEDIA_PLAY_PAUSE)
                                    RemoteAction.Next -> vm.sendKeyTap(PcKeyCodes.VK_MEDIA_NEXT)
                                }
                            }
                        }

                        item {
                            FilesScreen(
                                path = fsPath,
                                items = fsItems,
                                onItemClick = { item ->
                                    if (item.type == "dir") {
                                        val next = if (fsPath.isBlank()) item.name else "$fsPath/${item.name}"
                                        vm.fsOpenDir(next)
                                    } else {
                                        val p = if (fsPath.isBlank()) item.name else "$fsPath/${item.name}"
                                        vm.fsGetFile(p)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (ui.requireAuth && !ui.isAuthed) {
                PairingDialog(
                    onPair = { pin -> vm.pair(pin) },
                    onDismiss = {}
                )
            }
        }
    }
}
