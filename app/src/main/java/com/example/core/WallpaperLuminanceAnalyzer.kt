package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PresetWallpaper(
    val id: String,
    val title: String,
    @DrawableRes val drawableRes: Int
)

data class ResolvedWallpaper(
    val id: String,
    val fullBitmap: ImageBitmap,
    val blurredBitmap: ImageBitmap,
    val averageLuminance: Double,
    val isLightWallpaper: Boolean
)

object WallpaperLuminanceAnalyzer {

    val presets: List<PresetWallpaper> = listOf(
        PresetWallpaper("preset:forest", "Forest Canopy", R.drawable.wp_forest_canopy),
        PresetWallpaper("preset:alpine", "Midnight Peaks", R.drawable.wp_midnight_peaks),
        PresetWallpaper("preset:obsidian", "Obsidian Silk", R.drawable.wp_obsidian_silk),
        PresetWallpaper("preset:dunes", "Alabaster Dunes", R.drawable.wp_alabaster_dunes),
        PresetWallpaper("preset:mist", "Morning Mist", R.drawable.wp_morning_mist)
    )

    /**
     * Copies a user-selected image from Android Photo Picker into internal storage so
     * it persists reliably across restarts without requiring broad storage permissions.
     */
    suspend fun importWallpaperFromUri(context: Context, uri: Uri): Pair<File, Boolean>? =
        withContext(Dispatchers.IO) {
            try {
                val profile = DeviceOptimizationManager.detectProfile(context)
                val targetMaxPx = profile.maxWallpaperDimensionPx
                val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                val destFile = File(dir, "custom_wp_${System.currentTimeMillis()}.jpg")

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = DeviceOptimizationManager.calculateInSampleSize(
                        srcWidth = boundsOpts.outWidth,
                        srcHeight = boundsOpts.outHeight,
                        maxDimensionPx = targetMaxPx
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext null

                // Cap maximum dimension to device-optimal size for crisp display & fast GPU upload
                val maxSide = max(decoded.width, decoded.height)
                val scaled = if (maxSide > targetMaxPx) {
                    val ratio = targetMaxPx.toFloat() / maxSide
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * ratio).toInt().coerceAtLeast(1),
                        (decoded.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }

                FileOutputStream(destFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                val lum = computeWallpaperLuminance(scaled)
                destFile to (lum >= 0.46)
            } catch (_: Throwable) {
                null
            }
        }

    suspend fun loadAndAnalyze(
        context: Context,
        wallpaperId: String,
        customFilePath: String?
    ): ResolvedWallpaper? = withContext(Dispatchers.Default) {
        try {
            val profile = DeviceOptimizationManager.detectProfile(context)
            val targetMaxPx = profile.maxWallpaperDimensionPx

            val rawBitmap: Bitmap = if (customFilePath != null && File(customFilePath).exists()) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(customFilePath, boundsOpts)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = DeviceOptimizationManager.calculateInSampleSize(
                        srcWidth = boundsOpts.outWidth,
                        srcHeight = boundsOpts.outHeight,
                        maxDimensionPx = targetMaxPx
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(customFilePath, opts)
            } else {
                val resId = presets.firstOrNull { it.id == wallpaperId }?.drawableRes
                    ?: R.drawable.wp_forest_canopy
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, resId, boundsOpts)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = DeviceOptimizationManager.calculateInSampleSize(
                        srcWidth = boundsOpts.outWidth,
                        srcHeight = boundsOpts.outHeight,
                        maxDimensionPx = targetMaxPx
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(context.resources, resId, opts)
            } ?: return@withContext null

            val luminance = computeWallpaperLuminance(rawBitmap)
            // Downscale according to device profile and apply StackBlur for frosted glass fallback
            val downFactor = profile.fallbackStackBlurDownscale.coerceAtLeast(2)
            val downW = (rawBitmap.width / downFactor).coerceAtLeast(48)
            val downH = (rawBitmap.height / downFactor).coerceAtLeast(96)
            val smallCopy = Bitmap.createScaledBitmap(rawBitmap, downW, downH, true)
                .copy(Bitmap.Config.ARGB_8888, true)
            val blurred = stackBlur(smallCopy, radius = profile.fallbackStackBlurRadius)

            ResolvedWallpaper(
                id = wallpaperId,
                fullBitmap = rawBitmap.asImageBitmap(),
                blurredBitmap = blurred.asImageBitmap(),
                averageLuminance = luminance,
                isLightWallpaper = luminance >= 0.46
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Computes perceptual WC3 relative luminance across the wallpaper, giving slightly higher
     * weight to the upper-middle region where the Favorites icons & labels sit directly over
     * the untinted wallpaper.
     */
    fun computeWallpaperLuminance(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0.0
        val stepX = (w / 24).coerceAtLeast(1)
        val stepY = (h / 32).coerceAtLeast(1)

        var weightedSum = 0.0
        var totalWeight = 0.0

        var y = stepY / 2
        while (y < h) {
            val ny = y.toDouble() / h
            // Favorites labels sit primarily between 12% and 56% of viewport height
            val rowWeight = if (ny in 0.12..0.58) 1.6 else 0.85
            var x = stepX / 2
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val lum = ColorUtils.calculateLuminance(pixel)
                weightedSum += lum * rowWeight
                totalWeight += rowWeight
                x += stepX
            }
            y += stepY
        }
        return if (totalWeight > 0.0) weightedSum / totalWeight else 0.0
    }

    /**
     * Produces a lightweight downscaled & StackBlurred ImageBitmap on a background dispatcher
     * so live WebView content can refract through floating frosted-glass surfaces while scrolling
     * or dragging without blocking UI frame pacing.
     */
    fun blurBitmapForBackdrop(
        source: Bitmap,
        downscale: Int = 4,
        radius: Int = 14
    ): ImageBitmap? {
        return try {
            if (source.width <= 8 || source.height <= 8) return null
            val factor = downscale.coerceAtLeast(2)
            val downW = (source.width / factor).coerceAtLeast(40)
            val downH = (source.height / factor).coerceAtLeast(64)
            val scaled = Bitmap.createScaledBitmap(source, downW, downH, true)
                .copy(Bitmap.Config.ARGB_8888, true)
            stackBlur(scaled, radius.coerceIn(4, 24)).asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fast in-place Mario Klingemann StackBlur for silky, artifact-free Gaussian-like blur.
     */
    private fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in dv.indices) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
