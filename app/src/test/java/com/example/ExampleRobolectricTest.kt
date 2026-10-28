package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.core.DeviceOptimizationManager
import com.example.core.GpuRenderController
import com.example.core.WallpaperLuminanceAnalyzer
import com.example.data.BrowserRepository
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Glass Browser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Glass Browser", appName)
    }

    @Test
    fun `wallpaper luminance distinguishes dark and light wallpapers for favorites labels`() {
        val darkBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(18, 28, 20))
        }
        val lightBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(238, 240, 244))
        }

        val darkLum = WallpaperLuminanceAnalyzer.computeWallpaperLuminance(darkBmp)
        val lightLum = WallpaperLuminanceAnalyzer.computeWallpaperLuminance(lightBmp)

        assertFalse("Dark wallpaper should use light labels", darkLum >= 0.46)
        assertTrue("Light wallpaper should use dark labels", lightLum >= 0.46)
    }

    @Test
    fun `url and search normalization formats domains and search queries`() {
        assertEquals("https://dezeen.com", BrowserRepository.normalizeUrlOrSearch("dezeen.com"))
        assertTrue(
            BrowserRepository.normalizeUrlOrSearch("minimalist ceramics")
                .startsWith("https://www.google.com/search?q=")
        )
    }

    @Test
    fun `device optimization scales across compact phones foldables and tablets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = DeviceOptimizationManager.detectProfile(context)
        assertTrue(profile.maxCachedTabSnapshots >= 3)

        val smallPhone = DeviceOptimizationManager.computeAdaptiveMetrics(330.dp, 680.dp)
        val foldable = DeviceOptimizationManager.computeAdaptiveMetrics(680.dp, 840.dp)
        val tablet = DeviceOptimizationManager.computeAdaptiveMetrics(960.dp, 720.dp)

        assertTrue(smallPhone.isUltraCompactPhone)
        assertEquals(4, smallPhone.favoritesColumns)
        assertEquals(2, smallPhone.tabGridColumns)

        assertEquals(6, foldable.favoritesColumns)
        assertEquals(3, foldable.tabGridColumns)

        assertEquals(6, tablet.favoritesColumns)
        assertEquals(4, tablet.tabGridColumns)
        assertEquals(2, tablet.readingListColumns)
    }

    @Test
    fun `gpu rasterization configures WebView hardware rasterization pipeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "Enable GPU Rasterization",
            context.getString(R.string.enable_gpu_rasterization_label)
        )
        assertTrue(com.example.data.BrowserSettingsState().enableGpuRasterization)

        val webView = WebView(context)
        GpuRenderController.applyWebViewGpuPipeline(
            webView = webView,
            forceGpuRendering = false,
            disableHardwareOverlays = false,
            enableGpuRasterization = true
        )
        assertEquals(android.view.View.LAYER_TYPE_HARDWARE, webView.layerType)

        GpuRenderController.applyWebViewGpuPipeline(
            webView = webView,
            forceGpuRendering = false,
            disableHardwareOverlays = false,
            enableGpuRasterization = false
        )
        assertEquals(android.view.View.LAYER_TYPE_NONE, webView.layerType)
    }

    @Test
    fun `video playback state hides bottom search bar and restores on pause`() =
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val vm = com.example.ui.BrowserViewModel(app)

            val collectJob = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
                vm.uiState.collect {}
            }
            try {
                // Wait for initial database & DataStore streams to seed and emit
                var attempts = 0
                while (!vm.uiState.value.isReady && attempts < 50) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(40)
                    attempts++
                }

                val activeTabId = vm.uiState.value.activeTab?.id ?: -1L
                vm.onWebVideoPlaybackChanged(tabId = activeTabId, isPlaying = true)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isVideoPlaying)

                vm.onWebVideoPlaybackChanged(tabId = activeTabId, isPlaying = false)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.isVideoPlaying)
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `browser typography uses multi-weight SF Pro across all styles and resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fontResIds = listOf(
            R.font.inter_300,
            R.font.inter_400,
            R.font.inter_500,
            R.font.inter_600,
            R.font.inter_700,
            R.font.inter_800,
            R.font.inter_900
        )
        for (resId in fontResIds) {
            context.resources.openRawResource(resId).use { stream ->
                assertTrue("Bundled SF Pro font stream must be non-empty", stream.available() > 1024)
            }
        }

        val typography = com.example.ui.theme.BrowserTypography
        val sfPro = com.example.ui.theme.SfProFontFamily
        assertEquals(sfPro, typography.displayLarge.fontFamily)
        assertEquals(sfPro, typography.headlineMedium.fontFamily)
        assertEquals(sfPro, typography.titleLarge.fontFamily)
        assertEquals(sfPro, typography.titleMedium.fontFamily)
        assertEquals(sfPro, typography.bodyLarge.fontFamily)
        assertEquals(sfPro, typography.bodyMedium.fontFamily)
        assertEquals(sfPro, typography.labelLarge.fontFamily)
        assertEquals(sfPro, typography.labelMedium.fontFamily)
    }
}
