package com.example

import android.app.Application
import android.content.Context
import com.example.core.BrowserEngineInitializer

/**
 * Sanitizes Chromium WebView variations seed cache files in [attachBaseContext] prior to any
 * ContentProvider or WebView provider load, and initializes uncapped browser engine flags
 * (`--disable-frame-rate-limit` & `--disable-gpu-vsync`) in [onCreate].
 */
class GlassBrowserApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        BrowserEngineInitializer.sanitizeWebViewVariationsSeeds(base)
    }

    override fun onCreate() {
        super.onCreate()
        BrowserEngineInitializer.initialize(
            context = this,
            disableGpuVsync = true,
            disableFrameRateLimit = true,
            enableGpuRasterization = true
        )
    }
}
