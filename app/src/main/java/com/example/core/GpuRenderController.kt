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
 * Controls the browser's hardware GPU rendering, unified GPU screen compositing, and Chromium
 * WebView GPU tile & vector rasterization ("Force GPU Rendering", "Disable Hardware Overlays",
 * and "Enable GPU Rasterization").
 */
object GpuRenderController {

    private val sharedGpuPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }

    /**
     * Applies "Force GPU Rendering", "Disable Hardware Overlays", and "Enable GPU Rasterization"
     * to the Activity Window & root DecorView.
     */
    fun applyWindowGpuPipeline(
        activity: Activity,
        forceGpuRendering: Boolean,
        disableHardwareOverlays: Boolean,
        enableGpuRasterization: Boolean = true
    ) {
        val window = activity.window ?: return
        if (forceGpuRendering || disableHardwareOverlays || enableGpuRasterization) {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Keep unified default sRGB GPU composition when overlays are disabled to prevent HWC plane splits
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }

        val targetLayerType = when {
            disableHardwareOverlays -> View.LAYER_TYPE_HARDWARE
            forceGpuRendering -> View.LAYER_TYPE_HARDWARE
            enableGpuRasterization -> View.LAYER_TYPE_HARDWARE
            else -> View.LAYER_TYPE_NONE
        }
        val paint = if (targetLayerType == View.LAYER_TYPE_HARDWARE) sharedGpuPaint else null
        if (window.decorView.layerType != targetLayerType) {
            window.decorView.setLayerType(targetLayerType, paint)
        }
    }

    /**
     * Applies "Force GPU Rendering", "Disable Hardware Overlays", and "Enable GPU Rasterization"
     * directly to the active Chromium WebView instance.
     *
     * - [enableGpuRasterization]: Enables GPU offscreen tile pre-rasterization (`offscreenPreRaster`)
     *   and hardware GPU texture backing so vector paths, fonts, and scroll tiles rasterize directly
     *   on the GPU without blank checkerboarding during fast momentum flings.
     */
    @Suppress("DEPRECATION")
    fun applyWebViewGpuPipeline(
        webView: WebView,
        forceGpuRendering: Boolean,
        disableHardwareOverlays: Boolean,
        enableGpuRasterization: Boolean = true
    ) {
        val useHardwareLayer = forceGpuRendering || disableHardwareOverlays || enableGpuRasterization
        val desiredLayer = if (useHardwareLayer) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (webView.layerType != desiredLayer) {
            webView.setLayerType(
                desiredLayer,
                if (useHardwareLayer) sharedGpuPaint else null
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                webView.settings.offscreenPreRaster = enableGpuRasterization
            } catch (_: Throwable) {
                // Guard against non-standard OEM WebView implementations
            }
        }

        try {
            webView.settings.setRenderPriority(
                if (forceGpuRendering || enableGpuRasterization) {
                    WebSettings.RenderPriority.HIGH
                } else {
                    WebSettings.RenderPriority.NORMAL
                }
            )
        } catch (_: Throwable) {
            // Ignored on engines that manage render priority automatically
        }
    }

    /**
     * Applies GPU-composited rasterization hints and safe bottom clearance to loaded web documents
     * so scrolling layers, media elements, and typography rasterize cleanly on the GPU.
     */
    fun injectPageGpuRasterizationScript(
        webView: WebView,
        enableGpuRasterization: Boolean
    ) {
        val js = if (enableGpuRasterization) {
            """
            (function() {
                try {
                    if (document && document.body) {
                        document.body.style.paddingBottom = '88px';
                    }
                    if (document && document.head && !document.getElementById('__glass_gpu_raster')) {
                        var st = document.createElement('style');
                        st.id = '__glass_gpu_raster';
                        st.textContent = 'html,body{-webkit-font-smoothing:antialiased;text-rendering:optimizeSpeed;}img,video,canvas{backface-visibility:hidden;transform:translateZ(0);}';
                        document.head.appendChild(st);
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    if (document && document.body) {
                        document.body.style.paddingBottom = '88px';
                    }
                    var existing = document && document.getElementById('__glass_gpu_raster');
                    if (existing && existing.parentNode) {
                        existing.parentNode.removeChild(existing);
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        }
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {}
    }
}
