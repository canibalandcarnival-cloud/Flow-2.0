package com.example.core

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Controls the browser's hardware GPU rasterization and unified GPU screen compositing
 * ("Force GPU Rendering" and "Disable Hardware Overlays").
 */
object GpuRenderController {

    private fun createGpuPaint(): Paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )

    /**
     * Applies "Force GPU Rendering" and "Disable Hardware Overlays" to the Activity Window & DecorView.
     * - [forceGpuRendering]: Forces hardware-accelerated 2D GPU rendering flags on the window & root view.
     * - [disableHardwareOverlays]: Forces unified single-pass GPU composition on the root DecorView
     *   so child surfaces composite on the GPU instead of separate Hardware Composer (HWC) overlay planes.
     */
    fun applyWindowGpuPipeline(
        activity: Activity,
        forceGpuRendering: Boolean,
        disableHardwareOverlays: Boolean
    ) {
        val window = activity.window ?: return
        if (forceGpuRendering || disableHardwareOverlays) {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Keep unified default sRGB GPU composition when overlays are disabled to prevent HWC plane splits
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }

        val targetLayerType = when {
            disableHardwareOverlays -> View.LAYER_TYPE_HARDWARE
            forceGpuRendering -> View.LAYER_TYPE_HARDWARE
            else -> View.LAYER_TYPE_NONE
        }
        val paint = if (targetLayerType == View.LAYER_TYPE_HARDWARE) createGpuPaint() else null
        if (window.decorView.layerType != targetLayerType) {
            window.decorView.setLayerType(targetLayerType, paint)
        }
    }

    /**
     * Applies "Force GPU Rendering" and "Disable Hardware Overlays" directly to the active WebView.
     */
    @Suppress("DEPRECATION")
    fun applyWebViewGpuPipeline(
        webView: WebView,
        forceGpuRendering: Boolean,
        disableHardwareOverlays: Boolean
    ) {
        val useHardwareLayer = forceGpuRendering || disableHardwareOverlays
        val desiredLayer = if (useHardwareLayer) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (webView.layerType != desiredLayer) {
            webView.setLayerType(
                desiredLayer,
                if (useHardwareLayer) createGpuPaint() else null
            )
        }
        try {
            webView.settings.setRenderPriority(
                if (forceGpuRendering) WebSettings.RenderPriority.HIGH else WebSettings.RenderPriority.NORMAL
            )
        } catch (_: Throwable) {
            // Ignored on engines that manage render priority automatically
        }
    }
}
