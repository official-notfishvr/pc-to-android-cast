package com.pcscreencast.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pcscreencast.WebSocketStream
import com.pcscreencast.ZoomableStreamView

@Composable
fun StreamScreen(
    frame: Bitmap?,
    stream: WebSocketStream?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            ZoomableStreamView(ctx).apply {
                onControl = { type, x, y, button ->
                    stream?.sendControl(type, x, y, button)
                }
                onScroll = { dx, dy ->
                    stream?.sendScroll(dx, dy)
                }
            }
        },
        update = { view ->
            if (frame != null) view.setImageBitmap(frame)
        }
    )
}
