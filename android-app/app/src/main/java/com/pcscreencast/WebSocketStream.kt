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

/**
 * WebSocket stream that receives JPEG frames and emits Bitmaps.
 */
class WebSocketStream(private val url: String) {

    companion object {
        private const val TAG = "WebSocketStream"
    }

    fun stream(): Flow<Bitmap?> = callbackFlow {
        Log.d(TAG, "Connecting to $url")
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        var webSocket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val bmp = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size)
                if (bmp != null) trySend(bmp)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySend(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                trySend(null)
            }
        }

        webSocket = client.newWebSocket(request, listener)

        awaitClose {
            webSocket?.close(1000, null)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
