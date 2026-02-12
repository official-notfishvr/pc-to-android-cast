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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * WebSocket stream that receives JPEG frames and can send control messages.
 */
class WebSocketStream(private val url: String) {

    companion object {
        private const val TAG = "WebSocketStream"
    }

    private val webSocketRef = AtomicReference<WebSocket?>(null)

    fun stream(): Flow<Bitmap?> = callbackFlow {
        Log.d(TAG, "Connecting to $url")
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocketRef.set(ws)
                Log.d(TAG, "Connected")
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val bmp = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size)
                if (bmp != null) trySend(bmp)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                trySend(null)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                trySend(null)
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
}
