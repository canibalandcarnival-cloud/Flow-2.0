package com.example

import android.app.Application
import com.example.core.BrowserEngineInitializer

/**
 * Initializes uncapped browser engine flags (`--disable-frame-rate-limit` & `--disable-gpu-vsync`)
 * at process startup before any Activity or Chromium WebView instance is created.
 */
class GlassBrowserApplication : Application() {
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
