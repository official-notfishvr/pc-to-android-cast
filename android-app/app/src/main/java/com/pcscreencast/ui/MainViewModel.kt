package com.pcscreencast.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pcscreencast.StreamConfigData
import com.pcscreencast.StreamEvent
import com.pcscreencast.StreamPreferences
import com.pcscreencast.WebSocketStream
import com.pcscreencast.ui.model.ConnectionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.SocketTimeoutException
import android.util.Base64
import com.pcscreencast.ui.model.FsItem

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(StreamPreferences.PREFS_NAME, 0)

    private val _ui = MutableStateFlow(
        ConnectionUiState(
            ip = prefs.getString("last_ip", "") ?: "",
            port = prefs.getString("last_port", "9090") ?: "9090"
        )
    )
    val ui: StateFlow<ConnectionUiState> = _ui.asStateFlow()

    private var streamJob: Job? = null
    private var stream: WebSocketStream? = null

    private var lastFrame: Bitmap? = null

    private val _fsPath = MutableStateFlow("")
    val fsPath: StateFlow<String> = _fsPath.asStateFlow()

    private val _fsItems = MutableStateFlow<List<FsItem>>(emptyList())
    val fsItems: StateFlow<List<FsItem>> = _fsItems.asStateFlow()

    fun getStream(): WebSocketStream? = stream

    fun setIp(ip: String) {
        _ui.value = _ui.value.copy(ip = ip)
    }

    fun setPort(port: String) {
        _ui.value = _ui.value.copy(port = port)
    }

    fun connect() {
        val ip = _ui.value.ip.trim()
        val port = _ui.value.port.trim().ifEmpty { "9090" }
        if (ip.isBlank()) {
            _ui.value = _ui.value.copy(status = "Enter PC IP address")
            return
        }

        prefs.edit().putString("last_ip", ip).putString("last_port", port).apply()

        val url = "ws://$ip:$port"

        streamJob?.cancel()
        _ui.value = _ui.value.copy(
            isConnecting = true,
            isStreaming = false,
            requireAuth = false,
            isAuthed = false,
            status = "Connecting…"
        )

        val initialConfig = StreamConfigData(
            scale = StreamPreferences.getScale(prefs),
            quality = StreamPreferences.getQuality(prefs),
            fps = StreamPreferences.getFps(prefs),
            qualityZoomed = StreamPreferences.getQualityZoomed(prefs),
            fpsZoomed = StreamPreferences.getFpsZoomed(prefs)
        )

        val s = WebSocketStream(
            url = url,
            initialConfig = initialConfig,
            deviceId = null,
            deviceName = Build.MODEL
        )
        stream = s

        // First file list
        s.fsList("")

        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                s.stream()
                    .buffer(16)
                    .catch { e: Throwable ->
                        _ui.value = _ui.value.copy(
                            isConnecting = false,
                            isStreaming = false,
                            status = when (e) {
                                is SocketTimeoutException -> "Connection timed out"
                                else -> "Connection failed: ${e.message ?: e.javaClass.simpleName}"
                            }
                        )
                    }
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Frame -> {
                                val cur = _ui.value
                                if (cur.requireAuth && !cur.isAuthed) {
                                    _ui.value = cur.copy(isConnecting = false, isStreaming = false)
                                } else {
                                    lastFrame = event.bitmap
                                    _ui.value = cur.copy(isConnecting = false, isStreaming = true, status = null, frame = event.bitmap)
                                }
                            }
                            is StreamEvent.Message -> handleServerMessage(event.text)
                            is StreamEvent.Error -> {
                                _ui.value = _ui.value.copy(
                                    isConnecting = false,
                                    isStreaming = false,
                                    status = "Connection failed: ${event.throwable.message ?: event.throwable.javaClass.simpleName}"
                                )
                            }
                            is StreamEvent.Closed -> {
                                lastFrame = null
                                _ui.value = _ui.value.copy(isConnecting = false, isStreaming = false, frame = null)
                            }
                        }
                    }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isConnecting = false, isStreaming = false, status = "Connection failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        stream = null
        lastFrame = null
        _ui.value = _ui.value.copy(isConnecting = false, isStreaming = false, requireAuth = false, isAuthed = false, frame = null)
    }

    fun pair(pin: String) {
        stream?.sendAuth(pin)
    }

    fun fsOpenDir(path: String) {
        _fsPath.value = path
        stream?.fsList(path)
    }

    fun fsGetFile(path: String) {
        stream?.fsGet(path)
    }

    fun sendKeyTap(vk: Int) {
        stream?.sendKeyTap(vk)
    }

    fun sendKeyCombo(vk: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, win: Boolean = false) {
        stream?.sendKeyCombo(vk, ctrl = ctrl, shift = shift, alt = alt, win = win)
    }

    fun sendDoubleClick() {
        stream?.sendDoubleClick()
    }

    fun sendMiddleClick() {
        stream?.sendMiddleClick()
    }

    private fun handleServerMessage(text: String) {
        try {
            val obj = JSONObject(text)
            when (obj.optString("t", "")) {
                "hello_ack" -> {
                    val requireAuth = obj.optBoolean("requireAuth", false)
                    _ui.value = _ui.value.copy(requireAuth = requireAuth)
                }
                "status" -> {
                    val s = obj.optString("s", "")
                    if (s.isNotEmpty()) _ui.value = _ui.value.copy(status = s)
                }
                "auth_ok" -> _ui.value = _ui.value.copy(isAuthed = true, status = "Paired")
                "auth_fail" -> _ui.value = _ui.value.copy(isAuthed = false, status = "Wrong PIN")

                "fs_list_resp" -> {
                    val path = obj.optString("path", "")
                    val arr = obj.optJSONArray("items")
                    val items = mutableListOf<FsItem>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val it = arr.getJSONObject(i)
                            items.add(
                                FsItem(
                                    name = it.optString("name", ""),
                                    type = it.optString("type", "file"),
                                    size = if (it.has("size")) it.optLong("size") else null
                                )
                            )
                        }
                    }
                    _fsPath.value = path
                    _fsItems.value = items
                }
                "fs_get_resp" -> {
                    val path = obj.optString("path", "")
                    val data = obj.optString("data", "")
                    if (path.isNotEmpty() && data.isNotEmpty()) {
                        // For now just confirm we received bytes; Phase 3 next step saves to Downloads via SAF.
                        val bytes = Base64.decode(data, Base64.DEFAULT)
                        _ui.value = _ui.value.copy(status = "Downloaded ${bytes.size} bytes")
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
