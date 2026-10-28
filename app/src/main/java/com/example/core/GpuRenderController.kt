package com.example.core

import android.app.Activity
import android.content.pm.ActivityInfo
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

    /**
     * Applies "Force GPU Rendering", "Disable Hardware Overlays", and "Enable GPU Rasterization"
     * to the Activity Window without forcing an extra full-window offscreen FBO on DecorView,
     * allowing SurfaceFlinger/RenderThread to composite RenderNodes directly with zero wrapper layer clash.
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
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }

        // Keep DecorView on direct RenderNode hardware acceleration (LAYER_TYPE_NONE) rather than an
        // extra full-screen offscreen FBO texture blit that clashes with WebView's composite layer.
        if (window.decorView.layerType != View.LAYER_TYPE_NONE) {
            window.decorView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    /**
     * Applies isolated hardware composite layer, GPU tile pre-rasterization (`offscreenPreRaster`),
     * and uncapped frame-rate / GPU VSync bypass settings directly to the active Chromium WebView.
     */
    @Suppress("DEPRECATION")
    fun applyWebViewGpuPipeline(
        webView: WebView,
        forceGpuRendering: Boolean,
        disableHardwareOverlays: Boolean,
        enableGpuRasterization: Boolean = true,
        disableGpuVsync: Boolean = true,
        disableFrameRateLimit: Boolean = true
    ) {
        BrowserEngineInitializer.initialize(
            context = webView.context,
            disableGpuVsync = disableGpuVsync,
            disableFrameRateLimit = disableFrameRateLimit,
            enableGpuRasterization = enableGpuRasterization
        )

        val useHardwareLayer = forceGpuRendering || disableHardwareOverlays || enableGpuRasterization
        val desiredLayer = if (useHardwareLayer) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (webView.layerType != desiredLayer) {
            // Pass null Paint so RenderThread composites the isolated WebView hardware layer directly
            // without extra software Paint shader/dither passes on every scroll frame.
            webView.setLayerType(desiredLayer, null)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                webView.requestedFrameRate = when {
                    disableFrameRateLimit -> DisplayRefreshRateManager.TARGET_MAX_HZ
                    disableGpuVsync -> View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                    else -> View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
                }
            } catch (_: Throwable) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                webView.settings.offscreenPreRaster = enableGpuRasterization
            } catch (_: Throwable) {
                // Guard against non-standard OEM WebView implementations
            }
        }

        try {
            webView.settings.standardFontFamily = "SF Pro Text"
            webView.settings.sansSerifFontFamily = "SF Pro Text"
        } catch (_: Throwable) {
            // Guard against stubbed or restricted WebSettings implementations
        }

        try {
            webView.settings.setRenderPriority(
                if (forceGpuRendering || enableGpuRasterization || disableFrameRateLimit || disableGpuVsync) {
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
     * Injects local multi-weight SF Pro (`SF Pro Text`, `SF Pro Display`, `SF Pro`, `-apple-system`,
     * `BlinkMacSystemFont`) `@font-face` declarations into web pages so web typography renders in
     * crisp SF Pro without breaking custom icon fonts or monospace code blocks.
     */
    fun injectPageSfProTypography(webView: WebView) {
        val js = """
            (function() {
                try {
                    if (!document || !document.head || document.getElementById('__glass_sf_pro_font')) return;
                    var weights = [300, 400, 500, 600, 700, 800, 900];
                    var families = ['SF Pro Text', 'SF Pro Display', 'SF Pro', '-apple-system', 'BlinkMacSystemFont'];
                    var css = '';
                    for (var f = 0; f < families.length; f++) {
                        for (var w = 0; w < weights.length; w++) {
                            var wt = weights[w];
                            css += "@font-face{font-family:'" + families[f] + "';font-style:normal;font-weight:" + wt + ";font-display:swap;src:url('https://glassbrowser.font.local/sf-pro-" + wt + ".ttf') format('truetype');}";
                        }
                    }
                    css += ":root,html,body,button,input,select,textarea{font-family:'SF Pro Text','SF Pro Display','SF Pro',-apple-system,BlinkMacSystemFont,sans-serif;-webkit-font-smoothing:antialiased;}";
                    var st = document.createElement('style');
                    st.id = '__glass_sf_pro_font';
                    st.textContent = css;
                    document.head.insertBefore(st, document.head.firstChild);
                } catch (e) {}
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {}
    }

    /**
     * Applies isolated GPU composite layer hints and safe bottom clearance to loaded web documents
     * without dirtying layout if bottom padding is already set.
     */
    fun injectPageGpuRasterizationScript(
        webView: WebView,
        enableGpuRasterization: Boolean
    ) {
        injectPageSfProTypography(webView)
        val js = if (enableGpuRasterization) {
            """
            (function() {
                try {
                    if (document && document.body && !window.__glassVideoActivelyPlaying) {
                        if (document.body.style.paddingBottom !== '88px') {
                            document.body.style.paddingBottom = '88px';
                        }
                    }
                    if (document && document.head && !document.getElementById('__glass_gpu_raster')) {
                        var st = document.createElement('style');
                        st.id = '__glass_gpu_raster';
                        st.textContent = 'html{will-change:scroll-position;-webkit-overflow-scrolling:touch;}body{-webkit-font-smoothing:antialiased;text-rendering:optimizeSpeed;}img,video,canvas{backface-visibility:hidden;transform:translateZ(0);}';
                        document.head.appendChild(st);
                    }
                } catch (e) {}
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    if (document && document.body && !window.__glassVideoActivelyPlaying) {
                        if (document.body.style.paddingBottom !== '88px') {
                            document.body.style.paddingBottom = '88px';
                        }
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
