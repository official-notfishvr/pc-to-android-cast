package com.pcscreencast

import android.content.SharedPreferences

/**
 * Stream settings (scale, quality, fps) for zoomed in/out.
 * Stored in SharedPreferences; 0 for zoomed values means "use same as normal".
 */
object StreamPreferences {
    const val PREFS_NAME = "PcScreenCast"
    const val KEY_SCALE = "stream_scale"
    const val KEY_QUALITY = "stream_quality"
    const val KEY_FPS = "stream_fps"
    const val KEY_QUALITY_ZOOMED = "stream_quality_zoomed"
    const val KEY_FPS_ZOOMED = "stream_fps_zoomed"

    const val DEFAULT_SCALE = 1.0
    const val DEFAULT_QUALITY = 75
    const val DEFAULT_FPS = 20
    const val DEFAULT_QUALITY_ZOOMED = 0   // 0 = use same as quality
    const val DEFAULT_FPS_ZOOMED = 0       // 0 = use same as fps

    fun getScale(prefs: SharedPreferences): Double =
        prefs.getString(KEY_SCALE, null)?.toDoubleOrNull() ?: DEFAULT_SCALE

    fun getQuality(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_QUALITY, DEFAULT_QUALITY).coerceIn(1, 100)

    fun getFps(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_FPS, DEFAULT_FPS).coerceIn(1, 60)

    /** Quality when zoomed; 0 means use same as getQuality() */
    fun getQualityZoomed(prefs: SharedPreferences): Int? {
        val v = prefs.getInt(KEY_QUALITY_ZOOMED, DEFAULT_QUALITY_ZOOMED)
        return if (v in 1..100) v else null
    }

    /** FPS when zoomed; 0 means use same as getFps() */
    fun getFpsZoomed(prefs: SharedPreferences): Int? {
        val v = prefs.getInt(KEY_FPS_ZOOMED, DEFAULT_FPS_ZOOMED)
        return if (v in 1..60) v else null
    }

    fun setScale(prefs: SharedPreferences, value: Double) {
        prefs.edit().putString(KEY_SCALE, value.toString()).apply()
    }

    fun setQuality(prefs: SharedPreferences, value: Int) {
        prefs.edit().putInt(KEY_QUALITY, value.coerceIn(1, 100)).apply()
    }

    fun setFps(prefs: SharedPreferences, value: Int) {
        prefs.edit().putInt(KEY_FPS, value.coerceIn(1, 60)).apply()
    }

    fun setQualityZoomed(prefs: SharedPreferences, value: Int?) {
        prefs.edit().putInt(KEY_QUALITY_ZOOMED, value ?: 0).apply()
    }

    fun setFpsZoomed(prefs: SharedPreferences, value: Int?) {
        prefs.edit().putInt(KEY_FPS_ZOOMED, value ?: 0).apply()
    }
}
