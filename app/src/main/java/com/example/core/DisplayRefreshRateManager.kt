package com.example.core

import android.app.Activity
import android.os.Build
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/**
 * Configures the Activity window, DecorView, and Choreographer frame pacing to unlock
 * the highest available hardware refresh rate up to 360Hz (supporting 60/90/120/144/240/360Hz).
 */
object DisplayRefreshRateManager {

    const val TARGET_MAX_HZ = 360f

    data class DisplayCapability(
        val activeHz: Int,
        val maxSupportedHz: Int,
        val engineTargetHz: Int = 360
    )

    fun applyHighRefreshRate(
        activity: Activity,
        enabled: Boolean
    ): DisplayCapability {
        val window = activity.window ?: return DisplayCapability(60, 60)
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        var maxSupported = 60f
        var chosenHz = 60f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display != null) {
            val modes = display.supportedModes
            val currentMode = display.mode
            if (modes.isNotEmpty()) {
                maxSupported = modes.maxOf { it.refreshRate }.coerceAtMost(TARGET_MAX_HZ)
                // Prefer highest refresh rate at native resolution up to 360Hz
                val sameResModes = modes.filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight &&
                        it.refreshRate <= TARGET_MAX_HZ + 0.5f
                }
                val bestMode = if (enabled) {
                    (sameResModes.maxByOrNull { it.refreshRate }
                        ?: modes.filter { it.refreshRate <= TARGET_MAX_HZ + 0.5f }
                            .maxByOrNull { it.refreshRate })
                } else {
                    sameResModes.minByOrNull { it.refreshRate } ?: currentMode
                }

                val params: WindowManager.LayoutParams = window.attributes
                if (bestMode != null) {
                    params.preferredDisplayModeId = bestMode.modeId
                    chosenHz = bestMode.refreshRate
                }
                params.preferredRefreshRate = if (enabled) TARGET_MAX_HZ else 60f
                window.attributes = params
            }
        } else {
            val params = window.attributes
            params.preferredRefreshRate = if (enabled) TARGET_MAX_HZ else 60f
            window.attributes = params
        }

        // Android 14+ (API 34+): Request high frame rate category on root view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.decorView.requestedFrameRate = if (enabled) {
                View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_NORMAL
            }
        }

        // Android 15+ (API 35+): Enable immediate frame rate boost on touch/fling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.frameRateBoostOnTouchEnabled = enabled
        }

        return DisplayCapability(
            activeHz = chosenHz.roundToInt().coerceAtLeast(60),
            maxSupportedHz = maxSupported.roundToInt().coerceAtLeast(60),
            engineTargetHz = 360
        )
    }

    /**
     * Live Choreographer FPS monitor (used when viewing the Physics & Display sheet).
     */
    fun observeLiveFps(): Flow<Int> = callbackFlow {
        val choreographer = Choreographer.getInstance()
        var frameCount = 0
        var windowStartNs = 0L

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (windowStartNs == 0L) {
                    windowStartNs = frameTimeNanos
                }
                frameCount++
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 500_000_000L) { // Update every 500ms
                    val fps = ((frameCount * 1_000_000_000.0) / elapsedNs).roundToInt()
                    trySend(fps.coerceIn(1, 360))
                    frameCount = 0
                    windowStartNs = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }

        choreographer.postFrameCallback(callback)
        awaitClose {
            choreographer.removeFrameCallback(callback)
        }
    }
}
