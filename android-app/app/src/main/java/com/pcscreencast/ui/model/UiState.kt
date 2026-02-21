package com.pcscreencast.ui.model

import android.graphics.Bitmap

data class ConnectionUiState(
    val ip: String = "",
    val port: String = "9090",
    val isConnecting: Boolean = false,
    val isStreaming: Boolean = false,
    val requireAuth: Boolean = false,
    val isAuthed: Boolean = false,
    val status: String? = null,
    val frame: Bitmap? = null
)
