package com.pcscreencast

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pcscreencast.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import android.graphics.Bitmap
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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
        showConnecting(true, getString(R.string.connecting))
        val stream = WebSocketStream(url)
        webSocketStream = stream
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                stream.stream()
                    .buffer(16)
                    .catch { e: Throwable ->
                        Log.e(TAG, "Stream error: ${e.javaClass.simpleName} - ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            showStreaming(false)
                        }
                    }
                    .collect { bitmap: Bitmap? ->
                        runOnUiThread {
                            if (bitmap != null) {
                                if (frameCount++ == 0) {
                                    Log.d(TAG, "First frame, streaming")
                                    (binding.imageStream as? ZoomableStreamView)?.onControl = { type, x, y, button ->
                                        stream.sendControl(type, x, y, button)
                                    }
                                }
                                showStreaming(true)
                                (binding.imageStream.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { old ->
                                    if (old != bitmap && !old.isRecycled) old.recycle()
                                }
                                binding.imageStream.setImageBitmap(bitmap)
                                binding.imageStream.invalidate()
                            } else {
                                Log.w(TAG, "Stream ended or error (bitmap=null)")
                                showStreaming(false)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed: ${e.javaClass.simpleName} - ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
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
