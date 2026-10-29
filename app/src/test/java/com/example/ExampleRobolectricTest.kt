package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.core.BrowserAiService
import com.example.core.DeviceOptimizationManager
import com.example.core.DownloadEngine
import com.example.core.GpuRenderController
import com.example.core.WallpaperLuminanceAnalyzer
import com.example.core.configureAppCheck
import com.example.data.BrowserDatabase
import com.example.data.BrowserRepository
import com.example.data.DownloadItemEntity
import com.example.ui.TopHubSection
import com.example.ui.components.GlassMotionTracker
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `gpu rasterization configures WebView pipeline and guards missing rendernode on virtualized gpu`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "Enable GPU Rasterization",
            context.getString(R.string.enable_gpu_rasterization_label)
        )
        assertTrue(com.example.data.BrowserSettingsState().enableGpuRasterization)

        // Verify virtualized emulator without /dev/dri/renderD128 avoids offscreen FBO allocation
        assertFalse(
            GpuRenderController.shouldUseOffscreenHardwareLayer(
                hasRenderNode = false,
                isVirtualizedGpu = true
            )
        )
        assertTrue(
            GpuRenderController.shouldUseOffscreenHardwareLayer(
                hasRenderNode = true,
                isVirtualizedGpu = true
            )
        )
        assertTrue(
            GpuRenderController.shouldUseOffscreenHardwareLayer(
                hasRenderNode = false,
                isVirtualizedGpu = false
            )
        )

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
    fun `video playback downloads hub and siri ios26 state transitions work end-to-end`() =
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val dao = BrowserDatabase.getInstance(app).browserDao()

            // Verify DownloadEngine formatting & filename sanitization
            assertEquals("1.5 MB", DownloadEngine.formatBytes(1_572_864L))
            assertEquals("spec_sheet_v2.pdf", DownloadEngine.sanitizeFileName("spec/sheet:v2.pdf"))

            val insertedId = dao.insertDownload(
                DownloadItemEntity(
                    fileName = "glass_architecture.pdf",
                    url = "https://example.com/glass_architecture.pdf",
                    mimeType = "application/pdf",
                    totalBytes = 2048L,
                    downloadedBytes = 2048L,
                    status = "COMPLETED"
                )
            )
            assertTrue(insertedId > 0L)
            val allDownloads = dao.observeDownloads().first()
            assertTrue(allDownloads.any { it.fileName == "glass_architecture.pdf" })

            // Verify Siri URL directive extraction & stripping
            val sampleReply = "Opening Wikipedia for you.\nOPEN_URL: https://www.wikipedia.org"
            assertEquals(
                "https://www.wikipedia.org",
                BrowserAiService.extractOpenUrlDirective(sampleReply)
            )
            assertEquals(
                "Opening Wikipedia for you.",
                BrowserAiService.stripOpenUrlDirective(sampleReply).trim()
            )

            // Verify GlassMotionTracker draw-phase tick & webScrollDeltaY updates
            val tracker = GlassMotionTracker()
            val t0 = tracker.tick
            tracker.updateWebScrollDelta(148f)
            assertEquals(148f, tracker.webScrollDeltaY, 0.01f)
            assertTrue(tracker.tick > t0)

            // Verify BrowserViewModel video playback, Downloads Hub, WebTools & Siri iOS 26 state
            val vm = com.example.ui.BrowserViewModel(app)
            val collectJob = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
                vm.uiState.collect {}
            }
            try {
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

                vm.openDownloadsHub()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertEquals(TopHubSection.DOWNLOADS, vm.uiState.value.activeSection)

                vm.toggleSiriOverlay()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isSiriOpen)

                vm.closeSiriOverlay()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.isSiriOpen)

                dao.clearCompletedDownloads()
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `firebase app check debug token is persisted synchronously from launch intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token-ios26")
        }
        configureAppCheck(context, launchIntent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertNotNull(prefs)
        assertEquals(
            "test-debug-token-ios26",
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )
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
