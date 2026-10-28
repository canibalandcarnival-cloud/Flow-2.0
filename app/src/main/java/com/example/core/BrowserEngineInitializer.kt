package com.example.core

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes uncapped Chromium / WebView engine command-line flags prior to first WebView
 * instantiation (`--disable-frame-rate-limit`, `--disable-gpu-vsync`, `--enable-gpu-rasterization`,
 * `--enable-oop-rasterization`, `--enable-zero-copy`) so scrolling and GPU tile swaps are not
 * throttled by frame rate caps or GPU VSync wait stalls.
 */
object BrowserEngineInitializer {

    const val FLAG_DISABLE_FRAME_RATE_LIMIT = "disable-frame-rate-limit"
    const val FLAG_DISABLE_GPU_VSYNC = "disable-gpu-vsync"
    const val FLAG_ENABLE_GPU_RASTERIZATION = "enable-gpu-rasterization"
    const val FLAG_ENABLE_OOP_RASTERIZATION = "enable-oop-rasterization"
    const val FLAG_ENABLE_ZERO_COPY = "enable-zero-copy"
    const val FLAG_ENABLE_SMOOTH_SCROLLING = "enable-smooth-scrolling"

    private val initialized = AtomicBoolean(false)

    @Volatile
    var activeEngineFlags: Set<String> = emptySet()
        private set

    fun buildCommandLineArgs(
        disableGpuVsync: Boolean = true,
        disableFrameRateLimit: Boolean = true,
        enableGpuRasterization: Boolean = true
    ): List<String> {
        return buildList {
            add("glass-browser")
            if (disableFrameRateLimit) {
                add("--$FLAG_DISABLE_FRAME_RATE_LIMIT")
            }
            if (disableGpuVsync) {
                add("--$FLAG_DISABLE_GPU_VSYNC")
            }
            if (enableGpuRasterization) {
                add("--$FLAG_ENABLE_GPU_RASTERIZATION")
                add("--$FLAG_ENABLE_OOP_RASTERIZATION")
                add("--$FLAG_ENABLE_ZERO_COPY")
            }
            add("--$FLAG_ENABLE_SMOOTH_SCROLLING")
        }
    }

    fun initialize(
        @Suppress("UNUSED_PARAMETER") context: Context,
        disableGpuVsync: Boolean = true,
        disableFrameRateLimit: Boolean = true,
        enableGpuRasterization: Boolean = true
    ) {
        val args = buildCommandLineArgs(
            disableGpuVsync = disableGpuVsync,
            disableFrameRateLimit = disableFrameRateLimit,
            enableGpuRasterization = enableGpuRasterization
        )
        activeEngineFlags = args.drop(1).map { it.removePrefix("--") }.toSet()

        try {
            System.setProperty(
                "webview.chromium.disable_gpu_vsync",
                disableGpuVsync.toString()
            )
            System.setProperty(
                "webview.chromium.disable_frame_rate_limit",
                disableFrameRateLimit.toString()
            )
        } catch (_: Throwable) {}

        if (initialized.compareAndSet(false, true) || args.size > 1) {
            applyChromiumCommandLineIfExposed(args.toTypedArray())
        }
    }

    private fun applyChromiumCommandLineIfExposed(args: Array<String>) {
        runCatching {
            val cmdClass = Class.forName("org.chromium.base.CommandLine")
            val isInitMethod = cmdClass.getMethod("isInitialized")
            val alreadyInit = isInitMethod.invoke(null) as? Boolean ?: false
            if (!alreadyInit) {
                val initMethod = cmdClass.getMethod("init", Array<String>::class.java)
                initMethod.invoke(null, args as Any)
            } else {
                val getInstance = cmdClass.getMethod("getInstance")
                val instance = getInstance.invoke(null) ?: return@runCatching
                val hasSwitch = instance.javaClass.getMethod("hasSwitch", String::class.java)
                val appendSwitch = instance.javaClass.getMethod("appendSwitch", String::class.java)
                for (raw in args.drop(1)) {
                    val switchName = raw.removePrefix("--")
                    val present = hasSwitch.invoke(instance, switchName) as? Boolean ?: false
                    if (!present) {
                        appendSwitch.invoke(instance, switchName)
                    }
                }
            }
        }
    }
}
