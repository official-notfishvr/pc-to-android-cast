package com.pcscreencast

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.pcscreencast.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(StreamPreferences.PREFS_NAME, MODE_PRIVATE)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Scale: 25–100 → 0.25–1.0
        val scaleProgress = ((StreamPreferences.getScale(prefs) - 0.25) / 0.75 * 75).toInt().coerceIn(0, 75)
        binding.seekScale.progress = scaleProgress
        binding.textScaleValue.text = formatScale(scaleToValue(scaleProgress))
        binding.seekScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textScaleValue.text = formatScale(scaleToValue(progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                StreamPreferences.setScale(prefs, scaleToValue(seekBar?.progress ?: 0))
            }
        })

        // Quality 1–100
        binding.seekQuality.progress = StreamPreferences.getQuality(prefs) - 1
        binding.textQualityValue.text = "${StreamPreferences.getQuality(prefs)}"
        binding.seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textQualityValue.text = "${progress + 1}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                StreamPreferences.setQuality(prefs, (seekBar?.progress ?: 0) + 1)
            }
        })

        // FPS 1–60
        binding.seekFps.progress = StreamPreferences.getFps(prefs) - 1
        binding.textFpsValue.text = "${StreamPreferences.getFps(prefs)}"
        binding.seekFps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textFpsValue.text = "${progress + 1}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                StreamPreferences.setFps(prefs, (seekBar?.progress ?: 0) + 1)
            }
        })

        // Quality zoomed: 0 = use same, 1–100 = value
        val qz = StreamPreferences.getQualityZoomed(prefs)
        binding.seekQualityZoomed.max = 100
        binding.seekQualityZoomed.progress = qz ?: 0
        binding.textQualityZoomedValue.text = if (qz != null) "$qz" else getString(R.string.use_same)
        binding.seekQualityZoomed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textQualityZoomedValue.text = if (progress == 0) getString(R.string.use_same) else "$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = seekBar?.progress ?: 0
                StreamPreferences.setQualityZoomed(prefs, if (p == 0) null else p)
            }
        })

        // FPS zoomed: 0 = use same, 1–60 = value
        val fz = StreamPreferences.getFpsZoomed(prefs)
        binding.seekFpsZoomed.max = 60
        binding.seekFpsZoomed.progress = fz ?: 0
        binding.textFpsZoomedValue.text = if (fz != null) "$fz" else getString(R.string.use_same)
        binding.seekFpsZoomed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textFpsZoomedValue.text = if (progress == 0) getString(R.string.use_same) else "$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val p = seekBar?.progress ?: 0
                StreamPreferences.setFpsZoomed(prefs, if (p == 0) null else p)
            }
        })

        binding.btnDone.setOnClickListener { finish() }
    }

    private fun scaleToValue(progress: Int): Double = 0.25 + (progress / 75.0) * 0.75

    private fun formatScale(v: Double): String = when {
        v >= 1.0 -> "100%"
        v >= 0.99 -> "99%"
        else -> "${(v * 100).toInt()}%"
    }
}
