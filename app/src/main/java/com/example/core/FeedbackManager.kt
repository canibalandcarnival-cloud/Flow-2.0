package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency acoustic glass SFX synthesizer & tactile Haptic Feedback controller.
 * Pre-generates crisp PCM micro-buffers in memory so UI interactions respond in <2ms.
 */
class FeedbackManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var sfxEnabled: Boolean = true

    @Volatile
    var hapticsEnabled: Boolean = true

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    private val sampleRate = 44100

    private val glassTapPcm: ShortArray by lazy {
        synthesizeTone(durationMs = 20, startHz = 1480.0, endHz = 960.0, decay = 155.0, gain = 0.24)
    }

    private val switchTickPcm: ShortArray by lazy {
        synthesizeTone(durationMs = 24, startHz = 1820.0, endHz = 1320.0, decay = 140.0, gain = 0.22)
    }

    private val tabOpenPcm: ShortArray by lazy {
        synthesizeTone(durationMs = 42, startHz = 540.0, endHz = 1080.0, decay = 78.0, gain = 0.20)
    }

    private val tabClosePcm: ShortArray by lazy {
        synthesizeTone(durationMs = 34, startHz = 880.0, endHz = 430.0, decay = 95.0, gain = 0.20)
    }

    private val bounceEdgePcm: ShortArray by lazy {
        synthesizeTone(durationMs = 32, startHz = 195.0, endHz = 92.0, decay = 92.0, gain = 0.26)
    }

    fun onGlassTap() {
        triggerHaptic(HapticKind.LIGHT_TICK)
        playPcmAsync(glassTapPcm, volume = 0.75f)
    }

    fun onSegmentSwitch() {
        triggerHaptic(HapticKind.CRISP_CLICK)
        playPcmAsync(switchTickPcm, volume = 0.85f)
    }

    fun onTabZoom(opening: Boolean) {
        triggerHaptic(if (opening) HapticKind.QUICK_RISE else HapticKind.LIGHT_TICK)
        playPcmAsync(if (opening) tabOpenPcm else tabClosePcm, volume = 0.80f)
    }

    fun onTabClosed() {
        triggerHaptic(HapticKind.CRISP_CLICK)
        playPcmAsync(tabClosePcm, volume = 0.80f)
    }

    fun onEdgeBounce(velocityPxPerSec: Float) {
        val normalized = (kotlin.math.abs(velocityPxPerSec) / 4200f).coerceIn(0.18f, 1.0f)
        if (normalized > 0.22f) {
            triggerHaptic(HapticKind.SOFT_THUD)
            playPcmAsync(bounceEdgePcm, volume = normalized * 0.65f)
        }
    }

    private enum class HapticKind {
        LIGHT_TICK,
        CRISP_CLICK,
        QUICK_RISE,
        SOFT_THUD
    }

    private fun triggerHaptic(kind: HapticKind) {
        if (!hapticsEnabled) return
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val composition = VibrationEffect.startComposition()
                when (kind) {
                    HapticKind.LIGHT_TICK ->
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.55f)
                    HapticKind.CRISP_CLICK ->
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.72f)
                    HapticKind.QUICK_RISE ->
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.65f)
                    HapticKind.SOFT_THUD ->
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.42f)
                }
                vib.vibrate(composition.compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = when (kind) {
                    HapticKind.LIGHT_TICK, HapticKind.SOFT_THUD -> VibrationEffect.EFFECT_TICK
                    HapticKind.CRISP_CLICK, HapticKind.QUICK_RISE -> VibrationEffect.EFFECT_CLICK
                }
                vib.vibrate(VibrationEffect.createPredefined(effectId))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(10L)
            }
        } catch (_: Throwable) {
            // Ignore vibration failures in restricted environments
        }
    }

    private fun playPcmAsync(pcm: ShortArray, volume: Float) {
        if (!sfxEnabled || pcm.isEmpty()) return
        audioScope.launch {
            var track: AudioTrack? = null
            try {
                val byteCount = pcm.size * 2
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(byteCount.coerceAtLeast(2048))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                kotlinx.coroutines.delay(65L)
            } catch (_: Throwable) {
                // Audio output unavailable (e.g. headless container) — fail silently
            } finally {
                try {
                    track?.stop()
                } catch (_: Throwable) {}
                try {
                    track?.release()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun synthesizeTone(
        durationMs: Int,
        startHz: Double,
        endHz: Double,
        decay: Double,
        gain: Double
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs) / 1000
        val out = ShortArray(totalSamples)
        var phase = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / totalSamples.coerceAtLeast(1)
            val freq = startHz + (endHz - startHz) * progress
            phase += (2.0 * PI * freq) / sampleRate
            // Fast 1.5ms attack + smooth exponential glass resonance decay
            val attack = (i / (sampleRate * 0.0015)).coerceAtMost(1.0)
            val env = attack * exp(-decay * t)
            // Subtle octave harmonic for crystalline glass character
            val wave = (sin(phase) * 0.82 + sin(phase * 2.0) * 0.18) * env * gain
            out[i] = (wave * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }
}
