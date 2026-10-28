package com.example.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Hardware capability profiler and responsive viewport metrics calculator so Glass Browser
 * runs smoothly across entry-level low-RAM phones, compact handsets, foldables, tablets,
 * desktop multi-window environments, and 120Hz–360Hz flagship displays.
 */
object DeviceOptimizationManager {

    enum class DeviceTier {
        EFFICIENCY,
        BALANCED,
        ULTRA_FLAGSHIP
    }

    @Immutable
    data class DeviceProfile(
        val tier: DeviceTier = DeviceTier.BALANCED,
        val tierLabel: String = "Balanced Hardware",
        val memoryClassMb: Int = 256,
        val cpuCores: Int = 8,
        val isLowRamDevice: Boolean = false,
        val maxCachedTabSnapshots: Int = 6,
        val snapshotScale: Float = 0.31f,
        val preferRgb565Snapshots: Boolean = false,
        val maxWallpaperDimensionPx: Int = 1440,
        val fallbackStackBlurDownscale: Int = 4,
        val fallbackStackBlurRadius: Int = 15,
        val cardBlurDp: Float = 26f,
        val barBlurDp: Float = 32f
    )

    @Immutable
    data class AdaptiveLayoutMetrics(
        val isUltraCompactPhone: Boolean,
        val isCompact: Boolean,
        val isMedium: Boolean,
        val isExpanded: Boolean,
        val isShortLandscape: Boolean,
        val contentMaxWidth: Dp,
        val chromeBarMaxWidth: Dp,
        val sheetMaxWidth: Dp,
        val horizontalPadding: Dp,
        val favoritesColumns: Int,
        val favoriteIconSize: Dp,
        val readingListColumns: Int,
        val tabGridColumns: Int,
        val privacyPillWidth: Dp,
        val floatingActionSize: Dp
    )

    @Volatile
    private var cachedProfile: DeviceProfile? = null

    fun detectProfile(context: Context): DeviceProfile {
        cachedProfile?.let { return it }

        val appContext = context.applicationContext ?: context
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: false
        val baseMemMb = activityManager?.memoryClass ?: 256
        val largeMemMb = activityManager?.largeMemoryClass ?: baseMemMb
        val effectiveHeapMb = max(baseMemMb, largeMemMb)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val metrics = appContext.resources.displayMetrics
        val maxDisplaySidePx = max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(800)

        val tier = when {
            isLowRam || baseMemMb <= 192 || cores <= 4 || Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 ->
                DeviceTier.EFFICIENCY
            baseMemMb >= 384 && cores >= 7 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                DeviceTier.ULTRA_FLAGSHIP
            else ->
                DeviceTier.BALANCED
        }

        val profile = when (tier) {
            DeviceTier.EFFICIENCY -> DeviceProfile(
                tier = DeviceTier.EFFICIENCY,
                tierLabel = "Efficiency Tuned",
                memoryClassMb = effectiveHeapMb,
                cpuCores = cores,
                isLowRamDevice = isLowRam,
                maxCachedTabSnapshots = 3,
                snapshotScale = 0.24f,
                preferRgb565Snapshots = true,
                maxWallpaperDimensionPx = maxDisplaySidePx.coerceIn(720, 1080),
                fallbackStackBlurDownscale = 6,
                fallbackStackBlurRadius = 12,
                cardBlurDp = 22f,
                barBlurDp = 26f
            )
            DeviceTier.BALANCED -> DeviceProfile(
                tier = DeviceTier.BALANCED,
                tierLabel = "Balanced Multi-Core",
                memoryClassMb = effectiveHeapMb,
                cpuCores = cores,
                isLowRamDevice = isLowRam,
                maxCachedTabSnapshots = 6,
                snapshotScale = 0.31f,
                preferRgb565Snapshots = false,
                maxWallpaperDimensionPx = maxDisplaySidePx.coerceIn(960, 1440),
                fallbackStackBlurDownscale = 4,
                fallbackStackBlurRadius = 15,
                cardBlurDp = 26f,
                barBlurDp = 32f
            )
            DeviceTier.ULTRA_FLAGSHIP -> DeviceProfile(
                tier = DeviceTier.ULTRA_FLAGSHIP,
                tierLabel = "Flagship Ultra",
                memoryClassMb = effectiveHeapMb,
                cpuCores = cores,
                isLowRamDevice = isLowRam,
                maxCachedTabSnapshots = 10,
                snapshotScale = 0.36f,
                preferRgb565Snapshots = false,
                maxWallpaperDimensionPx = maxDisplaySidePx.coerceIn(1080, 1600),
                fallbackStackBlurDownscale = 4,
                fallbackStackBlurRadius = 16,
                cardBlurDp = 28f,
                barBlurDp = 34f
            )
        }

        cachedProfile = profile
        return profile
    }

    /**
     * Computes power-of-two BitmapFactory downsampling factor so large images decode directly
     * into device-appropriate bounds without spiking heap memory on low-RAM handsets.
     */
    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxDimensionPx: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDimensionPx <= 0) return 1
        var inSampleSize = 1
        val largestSide = max(srcWidth, srcHeight)
        while ((largestSide / (inSampleSize * 2)) >= maxDimensionPx) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Container-driven responsive layout metrics computed from BoxWithConstraints (never fixed
     * screen pixel assumptions), supporting small phones (<360dp), standard phones, landscape,
     * foldables (600–839dp), and tablets / desktop windows (>=840dp).
     */
    fun computeAdaptiveMetrics(maxWidth: Dp, maxHeight: Dp): AdaptiveLayoutMetrics {
        val isUltraCompact = maxWidth < 360.dp
        val isCompact = maxWidth < 600.dp
        val isMedium = maxWidth >= 600.dp && maxWidth < 840.dp
        val isExpanded = maxWidth >= 840.dp
        val isShortLandscape = maxHeight < 480.dp

        return AdaptiveLayoutMetrics(
            isUltraCompactPhone = isUltraCompact,
            isCompact = isCompact,
            isMedium = isMedium,
            isExpanded = isExpanded,
            isShortLandscape = isShortLandscape,
            contentMaxWidth = when {
                isExpanded -> 960.dp
                isMedium -> 760.dp
                else -> 600.dp
            },
            chromeBarMaxWidth = when {
                isExpanded -> 680.dp
                isMedium -> 600.dp
                else -> 560.dp
            },
            sheetMaxWidth = when {
                isExpanded -> 680.dp
                isMedium -> 620.dp
                else -> 600.dp
            },
            horizontalPadding = when {
                isUltraCompact -> 12.dp
                isExpanded -> 28.dp
                isMedium -> 24.dp
                else -> 18.dp
            },
            favoritesColumns = when {
                maxWidth >= 580.dp -> 6
                else -> 4
            },
            favoriteIconSize = when {
                maxWidth < 350.dp -> 58.dp
                maxWidth < 390.dp -> 64.dp
                isExpanded -> 74.dp
                else -> 70.dp
            },
            readingListColumns = if (maxWidth >= 700.dp) 2 else 1,
            tabGridColumns = when {
                maxWidth >= 880.dp -> 4
                maxWidth >= 580.dp -> 3
                else -> 2
            },
            privacyPillWidth = if (isUltraCompact) 174.dp else 204.dp,
            floatingActionSize = if (isUltraCompact) 48.dp else 50.dp
        )
    }
}
