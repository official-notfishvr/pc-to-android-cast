package com.pcscreencast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Parses MJPEG multipart stream and emits Bitmap frames.
 */
class MjpegInputStream(private val url: URL) {

    companion object {
        private const val TAG = "MjpegInputStream"
        private val BOUNDARY = "--frame".toByteArray()
        private val CRLF = byteArrayOf(13, 10)
        private val CRLFCRLF = byteArrayOf(13, 10, 13, 10)
    }

    fun stream(): Flow<Bitmap?> = flow {
        var connection: HttpURLConnection? = null
        try {
            Log.d(TAG, "Connecting to $url (timeout: 10s connect, 30s read)")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                requestMethod = "GET"
            }
            Log.d(TAG, "Calling connect()...")
            connection.connect()
            val code = connection.responseCode
            Log.d(TAG, "connect() returned, response $code")
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP $code")
                emit(null)
                return@flow
            }
            Log.d(TAG, "Reading stream...")
            parseStream(BufferedInputStream(connection.inputStream)) { bitmap ->
                emit(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream error: ${e.javaClass.simpleName} - ${e.message}", e)
            emit(null)
        } finally {
            Log.d(TAG, "Disconnecting")
            connection?.disconnect()
        }
    }

    private suspend fun parseStream(
        input: BufferedInputStream,
        onFrame: suspend (Bitmap?) -> Unit
    ) {
        val buffer = ByteArray(65536)
        var frameData = ByteArrayOutputStream(256 * 1024)
        var state = 0  // 0=seek boundary, 1=skip headers, 2=read jpeg
        var frameCount = 0
        var totalBytesRead = 0L

        while (true) {
            val n = input.read(buffer)
            if (n <= 0) {
                Log.d(TAG, "Stream ended (n=$n), read $totalBytesRead bytes, $frameCount frames")
                break
            }
            totalBytesRead += n
            if (totalBytesRead <= 65536) Log.d(TAG, "First chunk: $n bytes, total=$totalBytesRead")

            var i = 0
            while (i < n) {
                when (state) {
                    0 -> {
                        val idx = indexOf(buffer, i, n, BOUNDARY)
                        if (idx >= 0) {
                            if (frameCount == 0) Log.d(TAG, "Found boundary at offset $idx")
                            i = idx + BOUNDARY.size
                            state = 1
                        } else {
                            i = n
                        }
                    }
                    1 -> {
                        val idx = indexOf(buffer, i, n, CRLFCRLF)
                        if (idx >= 0) {
                            if (frameCount == 0) Log.d(TAG, "Found headers end, JPEG starts")
                            i = idx + CRLFCRLF.size
                            state = 2
                            frameData.reset()
                        } else {
                            i = n
                        }
                    }
                    2 -> {
                        // Server sends JPEG bytes directly followed by --frame (no \r\n before it)
                        val idx = indexOf(buffer, i, n, BOUNDARY)
                        if (idx >= 0) {
                            frameData.write(buffer, i, idx - i)
                            val bytes = frameData.toByteArray()
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                if (frameCount++ == 0) Log.d(TAG, "First frame: ${bytes.size} bytes -> ${bmp.width}x${bmp.height}")
                                else if (frameCount % 30 == 0) Log.d(TAG, "Frame $frameCount")
                                onFrame(bmp)
                            } else {
                                Log.w(TAG, "BitmapFactory returned null for ${bytes.size} bytes")
                            }
                            state = 0
                            frameData.reset()
                            i = idx
                        } else {
                            // Boundary may be split across reads - don't include last 6 bytes
                            val safeEnd = maxOf(i, n - BOUNDARY.size + 1)
                            if (safeEnd > i) frameData.write(buffer, i, safeEnd - i)
                            i = n  // Advance to end, read next chunk
                        }
                    }
                }
            }
        }
    }

    private fun indexOf(source: ByteArray, start: Int, end: Int, target: ByteArray): Int {
        if (start + target.size > end) return -1
        for (i in start..(end - target.size)) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) { match = false; break }
            }
            if (match) return i
        }
        return -1
    }

}
