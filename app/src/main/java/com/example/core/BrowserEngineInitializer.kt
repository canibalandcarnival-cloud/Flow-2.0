package com.example.core

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes uncapped Chromium / WebView engine command-line flags prior to first WebView
 * instantiation (`--disable-frame-rate-limit`, `--disable-gpu-vsync`, `--enable-gpu-rasterization`,
 * `--enable-oop-rasterization`, `--enable-zero-copy`) and sanitizes invalid/unsigned Chromium
 * `variations_seed` / `variations_seed_new` files before `variations_seed_loader.cc` executes.
 */
object BrowserEngineInitializer {

    const val FLAG_DISABLE_FRAME_RATE_LIMIT = "disable-frame-rate-limit"
    const val FLAG_DISABLE_GPU_VSYNC = "disable-gpu-vsync"
    const val FLAG_ENABLE_GPU_RASTERIZATION = "enable-gpu-rasterization"
    const val FLAG_ENABLE_OOP_RASTERIZATION = "enable-oop-rasterization"
    const val FLAG_ENABLE_ZERO_COPY = "enable-zero-copy"
    const val FLAG_ENABLE_SMOOTH_SCROLLING = "enable-smooth-scrolling"
    const val FLAG_DISABLE_FIELD_TRIAL_CONFIG = "disable-field-trial-config"
    const val FLAG_DISABLE_VARIATIONS_SEED_FETCH = "disable-variations-seed-fetch"

    const val SEED_FILE_NAME = "variations_seed"
    const val NEW_SEED_FILE_NAME = "variations_seed_new"
    const val STAMP_FILE_NAME = "variations_stamp"

    private const val MAX_SEED_FILE_BYTES = 8L * 1024L * 1024L

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
            add("--$FLAG_DISABLE_FIELD_TRIAL_CONFIG")
            add("--$FLAG_DISABLE_VARIATIONS_SEED_FETCH")
        }
    }

    fun initialize(
        context: Context,
        disableGpuVsync: Boolean = true,
        disableFrameRateLimit: Boolean = true,
        enableGpuRasterization: Boolean = true
    ) {
        sanitizeWebViewVariationsSeeds(context)

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

    /**
     * Purges empty, truncated, or unsigned Chromium `AwVariationsSeed` cache files across the app's
     * WebView data directories before `VariationsSeedLoader` / `variations_seed_loader.cc` runs,
     * eliminating `[ERROR:variations_seed_loader.cc(37)] Seed missing signature.` while preserving
     * any genuinely signed variations seed on production devices.
     */
    fun sanitizeWebViewVariationsSeeds(context: Context): Int {
        var purgedCount = 0
        val visitedPaths = HashSet<String>()
        val nowMillis = System.currentTimeMillis()

        fun inspectDir(dir: File?) {
            if (dir == null) return
            val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (!visitedPaths.add(canonical)) return
            purgedCount += sanitizeDirectoryVariationsSeeds(dir, nowMillis)
        }

        runCatching {
            val primaryWebViewDir = context.getDir("webview", Context.MODE_PRIVATE)
            inspectDir(primaryWebViewDir)
        }

        runCatching {
            val dataRoots = buildList {
                context.dataDir?.let { add(it) }
                context.applicationInfo?.dataDir?.let { add(File(it)) }
            }
            for (root in dataRoots) {
                if (!root.exists() || !root.isDirectory) continue
                // Check any suffixed or existing app_webview* directories
                root.listFiles()?.forEach { child ->
                    if (child.isDirectory && child.name.startsWith("app_webview")) {
                        inspectDir(child)
                    }
                }
                // Remove any stray unsigned seed files directly under dataDir without creating stamp there
                for (seedName in listOf(SEED_FILE_NAME, NEW_SEED_FILE_NAME)) {
                    val stray = File(root, seedName)
                    if (stray.isFile && !isSeedFileValid(stray)) {
                        if (stray.delete()) purgedCount++
                    }
                }
            }
        }

        return purgedCount
    }

    /**
     * Validates `variations_seed` and `variations_seed_new` in [webViewDir], deleting any empty
     * or unsigned seed files and refreshing `variations_stamp` when no valid seed is cached so
     * `VariationsSeedLoader` does not create a 0-byte `variations_seed_new` file via IPC.
     */
    fun sanitizeDirectoryVariationsSeeds(
        webViewDir: File,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        var purged = 0
        runCatching {
            if (!webViewDir.exists()) {
                webViewDir.mkdirs()
            }
            if (!webViewDir.isDirectory) return 0

            val seedFile = File(webViewDir, SEED_FILE_NAME)
            val newSeedFile = File(webViewDir, NEW_SEED_FILE_NAME)

            var hasValidRemainingSeed = false

            for (candidate in listOf(seedFile, newSeedFile)) {
                if (candidate.exists()) {
                    if (candidate.isFile && isSeedFileValid(candidate)) {
                        hasValidRemainingSeed = true
                    } else if (candidate.delete()) {
                        purged++
                    }
                }
            }

            // Also clean any stray unsigned seeds inside `Default` profile subdirectory if present
            val defaultSubdir = File(webViewDir, "Default")
            if (defaultSubdir.isDirectory) {
                for (seedName in listOf(SEED_FILE_NAME, NEW_SEED_FILE_NAME)) {
                    val subCandidate = File(defaultSubdir, seedName)
                    if (subCandidate.exists() && (!subCandidate.isFile || !isSeedFileValid(subCandidate))) {
                        if (subCandidate.delete()) purged++
                    }
                }
            }

            // When no valid signed seed exists (e.g. emulator / fresh environment where VariationsSeedServer
            // has no seed and would otherwise leave a 0-byte variations_seed_new), keep variations_stamp fresh
            // to throttle empty seed fetch attempts.
            if (!hasValidRemainingSeed) {
                val stampFile = File(webViewDir, STAMP_FILE_NAME)
                if (!stampFile.exists()) {
                    stampFile.createNewFile()
                }
                stampFile.setLastModified(nowMillis)
            }
        }
        return purged
    }

    private fun isSeedFileValid(file: File): Boolean {
        val len = file.length()
        if (len <= 0L || len > MAX_SEED_FILE_BYTES) return false
        return runCatching {
            isValidAwVariationsSeed(file.readBytes())
        }.getOrDefault(false)
    }

    /**
     * Pure binary Protocol Buffer wire-format validator for `org.chromium.android_webview.proto.AwVariationsSeed`
     * matching every required field check in Chromium's `variations_seed_loader.cc` (`IsSeedValid`):
     * - field 1: `optional string signature = 1` (non-empty)
     * - field 2: `optional string country = 2`
     * - field 3 / 6: `optional string date_header = 3` or `optional int64 date = 6`
     * - field 4: `optional bool is_gzip_compressed = 4`
     * - field 5: `optional bytes seed_data = 5` (non-empty)
     */
    fun isValidAwVariationsSeed(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false

        var pos = 0
        val limit = bytes.size

        var hasSignature = false
        var hasCountry = false
        var hasDate = false
        var hasIsGzipCompressed = false
        var hasSeedData = false

        fun readVarint(): Long? {
            var result = 0L
            var shift = 0
            while (pos < limit && shift < 64) {
                val b = bytes[pos].toInt() and 0xFF
                pos++
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) {
                    return result
                }
                shift += 7
            }
            return null
        }

        while (pos < limit) {
            val tag = readVarint() ?: return false
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07L).toInt()
            if (fieldNumber <= 0) return false

            when (wireType) {
                0 -> { // Varint
                    readVarint() ?: return false
                    when (fieldNumber) {
                        4 -> hasIsGzipCompressed = true
                        6 -> hasDate = true
                    }
                }
                1 -> { // 64-bit fixed
                    if (limit - pos < 8) return false
                    pos += 8
                }
                2 -> { // Length-delimited bytes / string
                    val length = readVarint() ?: return false
                    if (length < 0L || length > (limit - pos).toLong()) return false
                    val intLen = length.toInt()
                    when (fieldNumber) {
                        1 -> if (intLen > 0) hasSignature = true
                        2 -> if (intLen > 0) hasCountry = true
                        3 -> if (intLen > 0) hasDate = true
                        5 -> if (intLen > 0) hasSeedData = true
                    }
                    pos += intLen
                }
                5 -> { // 32-bit fixed
                    if (limit - pos < 4) return false
                    pos += 4
                }
                else -> return false
            }
        }

        return hasSignature && hasCountry && hasDate && hasIsGzipCompressed && hasSeedData
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
