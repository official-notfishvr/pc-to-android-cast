package com.pcscreencast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Initial stream config to send as soon as the WebSocket opens (so server can apply scale/quality/fps). */
data class StreamConfigData(
    val scale: Double,
    val quality: Int,
    val fps: Int,
    val qualityZoomed: Int?,
    val fpsZoomed: Int?
)

/**
 * WebSocket stream that receives JPEG frames and can send control messages.
 */
class WebSocketStream(
    private val url: String,
    private val initialConfig: StreamConfigData? = null
) {

    companion object {
        private const val TAG = "WebSocketStream"
    }

    private val webSocketRef = AtomicReference<WebSocket?>(null)

    fun stream(): Flow<StreamEvent> = callbackFlow {
        Log.d(TAG, "Connecting to $url")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocketRef.set(ws)
                Log.d(TAG, "Connected")
                initialConfig?.let { c ->
                    sendConfig(c.scale, c.quality, c.fps, c.qualityZoomed, c.fpsZoomed)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val bmp = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size)
                if (bmp != null) trySend(StreamEvent.Frame(bmp))
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                trySend(StreamEvent.Closed)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                trySend(StreamEvent.Error(t))
            }
        }

        val webSocket = client.newWebSocket(request, listener)

        awaitClose {
            webSocketRef.set(null)
            webSocket.close(1000, null)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    fun sendControl(type: String, x: Int, y: Int, button: Int = 0) {
        val json = """{"t":"$type","x":$x,"y":$y,"b":$button}"""
        webSocketRef.get()?.send(json)
    }

    fun sendViewport(x: Int, y: Int, w: Int, h: Int) {
        val json = """{"t":"v","x":$x,"y":$y,"w":$w,"h":$h}"""
        webSocketRef.get()?.send(json)
    }

    /** Send a key press (Windows VK code). keyDown: 1 = press+release, 0 = release only. */
    fun sendKey(keyCode: Int, keyDown: Int = 1, ctrl: Boolean = false, alt: Boolean = false, win: Boolean = false) {
        val c = if (ctrl) "true" else "false"
        val a = if (alt) "true" else "false"
        val w = if (win) "true" else "false"
        val json = """{"t":"k","k":$keyCode,"d":$keyDown,"ctrl":$c,"alt":$a,"win":$w}"""
        webSocketRef.get()?.send(json)
    }

    /** Send Unicode text (for IME / on-screen keyboard input). */
    fun sendUnicodeText(text: String) {
        if (text.isEmpty()) return
        val json = JSONObject().apply { put("t", "u"); put("s", text) }.toString()
        webSocketRef.get()?.send(json)
    }

    /** Send scroll delta (e.g. dy=1 for scroll down, dy=-1 for scroll up). */
    fun sendScroll(dx: Int = 0, dy: Int = 0) {
        if (dx == 0 && dy == 0) return
        val json = """{"t":"s","dx":$dx,"dy":$dy}"""
        webSocketRef.get()?.send(json)
    }

    /** Send stream config (scale, quality, fps; zoomed overrides). 0 for zoomed = use same as normal. */
    fun sendConfig(scale: Double, quality: Int, fps: Int, qualityZoomed: Int?, fpsZoomed: Int?) {
        val qz = qualityZoomed ?: 0
        val fz = fpsZoomed ?: 0
        val json = """{"t":"config","scale":$scale,"quality":$quality,"fps":$fps,"qualityZoomed":$qz,"fpsZoomed":$fz}"""
        webSocketRef.get()?.send(json)
    }
}
