package com.pcscreencast

import android.graphics.Bitmap

/**
 * Event from the WebSocket stream: a frame, a connection error, or stream closed.
 */
sealed class StreamEvent {
    data class Frame(val bitmap: Bitmap) : StreamEvent()
    data class Error(val throwable: Throwable) : StreamEvent()
    object Closed : StreamEvent()
}
