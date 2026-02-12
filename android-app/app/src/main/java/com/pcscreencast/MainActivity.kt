package com.pcscreencast

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pcscreencast.databinding.ActivityMainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import android.graphics.Bitmap
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PcScreenCast"
        private const val PREFS_NAME = "PcScreenCast"
        private const val KEY_IP = "last_ip"
        private const val KEY_PORT = "last_port"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var streamJob: Job? = null
    private var frameCount = 0
    private var webSocketStream: WebSocketStream? = null
    private var fullCaptureWidth = 0
    private var fullCaptureHeight = 0
    private var currentCropRegion: IntArray? = null
    private var lastSentViewport: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editIp.setText(prefs.getString(KEY_IP, "") ?: "")
        binding.editPort.setText(prefs.getString(KEY_PORT, "9090") ?: "9090")

        binding.btnConnect.setOnClickListener { connect() }
        binding.btnToggleControls.setOnClickListener {
            val panel = binding.controlsPanel
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val streamView = binding.imageStream as? ZoomableStreamView ?: return
        binding.switchZoom.setOnCheckedChangeListener { _, checked -> streamView.enableZoom = checked }
        binding.switchClicks.setOnCheckedChangeListener { _, checked -> streamView.enableClicks = checked }
        binding.switchTapRight.setOnCheckedChangeListener { _, checked -> streamView.tapIsRightClick = checked }
        binding.btnResetZoom.setOnClickListener { streamView.resetZoom() }
        streamView.onDoubleTapView = {
            binding.btnToggleControls.visibility = if (binding.btnToggleControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun connect() {
        val ip = binding.editIp.text.toString().trim()
        val port = binding.editPort.text.toString().trim().ifEmpty { "9090" }
        if (ip.isBlank()) {
            Toast.makeText(this, "Enter PC IP address", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(KEY_IP, ip).putString(KEY_PORT, port).apply()
        val url = "ws://$ip:$port"
        Log.d(TAG, "Connecting to $url")
        streamJob?.cancel()
        frameCount = 0
        fullCaptureWidth = 0
        fullCaptureHeight = 0
        currentCropRegion = null
        lastSentViewport = null
        showConnecting(true, getString(R.string.connecting))
        val stream = WebSocketStream(url)
        webSocketStream = stream
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                stream.stream()
                    .buffer(16)
                    .catch { e: Throwable ->
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Stream error: ${e.javaClass.simpleName} - ${e.message}", e)
                        runOnUiThread {
                            val msg = when (e) {
                                is SocketTimeoutException -> getString(R.string.error_timeout)
                                else -> getString(R.string.error_generic, e.message ?: e.javaClass.simpleName)
                            }
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            showStreaming(false)
                        }
                    }
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Frame -> runOnUiThread {
                                val bitmap = event.bitmap
                                val isFirst = frameCount++ == 0
                                if (isFirst) {
                                    Log.d(TAG, "First frame, streaming")
                                    fullCaptureWidth = bitmap.width
                                    fullCaptureHeight = bitmap.height
                                    currentCropRegion = intArrayOf(0, 0, bitmap.width, bitmap.height)
                                    lastSentViewport = intArrayOf(0, 0, bitmap.width, bitmap.height)
                                    stream.sendViewport(0, 0, bitmap.width, bitmap.height)
                                    (binding.imageStream as? ZoomableStreamView)?.onControl = { type, x, y, button ->
                                        stream.sendControl(type, x, y, button)
                                    }
                                } else {
                                    currentCropRegion = lastSentViewport?.copyOf()
                                }
                                showStreaming(true)
                                (binding.imageStream.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { old ->
                                    if (old != bitmap && !old.isRecycled) old.recycle()
                                }
                                binding.imageStream.setImageBitmap(bitmap)
                                binding.imageStream.invalidate()
                                if (!isFirst) {
                                    val streamView = binding.imageStream as? ZoomableStreamView
                                    val crop = currentCropRegion
                                    if (streamView != null && crop != null && fullCaptureWidth > 0 && fullCaptureHeight > 0) {
                                        val isCrop = bitmap.width < fullCaptureWidth || bitmap.height < fullCaptureHeight
                                        val viewportToSend = when {
                                            isCrop && streamView.scale < 0.9f -> intArrayOf(0, 0, fullCaptureWidth, fullCaptureHeight)
                                            isCrop -> crop
                                            else -> {
                                                val visible = streamView.getVisibleViewport() ?: return@runOnUiThread
                                                val fullX = (crop[0] + visible[0]).coerceIn(0, fullCaptureWidth - 1)
                                                val fullY = (crop[1] + visible[1]).coerceIn(0, fullCaptureHeight - 1)
                                                val fullW = visible[2].coerceIn(1, fullCaptureWidth - fullX)
                                                val fullH = visible[3].coerceIn(1, fullCaptureHeight - fullY)
                                                intArrayOf(fullX, fullY, fullW, fullH)
                                            }
                                        }
                                        lastSentViewport = viewportToSend
                                        stream.sendViewport(viewportToSend[0], viewportToSend[1], viewportToSend[2], viewportToSend[3])
                                    }
                                }
                            }
                            is StreamEvent.Error -> runOnUiThread {
                                Log.w(TAG, "Stream error: ${event.throwable.message}")
                                val msg = when (event.throwable) {
                                    is SocketTimeoutException -> getString(R.string.error_timeout)
                                    else -> getString(R.string.error_generic, event.throwable.message ?: event.throwable.javaClass.simpleName)
                                }
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                showStreaming(false)
                            }
                            is StreamEvent.Closed -> runOnUiThread {
                                showStreaming(false)
                            }
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Connect failed: ${e.javaClass.simpleName} - ${e.message}", e)
                runOnUiThread {
                    val msg = when (e) {
                        is SocketTimeoutException -> getString(R.string.error_timeout)
                        else -> getString(R.string.error_generic, e.message ?: e.javaClass.simpleName)
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    showStreaming(false)
                }
            }
        }
    }

    private fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        webSocketStream = null
        showStreaming(false)
    }

    private fun showConnecting(show: Boolean, message: String = getString(R.string.loading)) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.loadingText.text = message
        binding.btnConnect.isEnabled = !show
    }

    private fun showStreaming(show: Boolean) {
        showConnecting(false)
        val wasStreaming = binding.streamContainer.visibility == View.VISIBLE
        binding.connectPanel.visibility = if (show) View.GONE else View.VISIBLE
        binding.streamContainer.visibility = if (show) View.VISIBLE else View.GONE
        if (show && !wasStreaming) binding.controlsPanel.visibility = View.GONE
    }

    override fun onDestroy() {
        streamJob?.cancel()
        super.onDestroy()
    }
}
