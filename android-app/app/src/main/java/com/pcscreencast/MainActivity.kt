package com.pcscreencast

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
    }

    private lateinit var binding: ActivityMainBinding
    private var streamJob: Job? = null
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener { connect() }
        binding.btnDisconnect.setOnClickListener { disconnect() }
    }

    private fun connect() {
        val ip = binding.editIp.text.toString().trim()
        val port = binding.editPort.text.toString().trim().ifEmpty { "9090" }
        if (ip.isBlank()) {
            Toast.makeText(this, "Enter PC IP address", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "ws://$ip:$port"
        Log.d(TAG, "Connecting to $url")
        streamJob?.cancel()
        frameCount = 0
        showConnecting(true, getString(R.string.connecting))
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                WebSocketStream(url).stream()
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
                                if (frameCount++ == 0) Log.d(TAG, "First frame, streaming")
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
        binding.connectPanel.visibility = if (show) View.GONE else View.VISIBLE
        binding.streamContainer.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnDisconnect.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        streamJob?.cancel()
        super.onDestroy()
    }
}
