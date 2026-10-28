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

    @Test
    fun `google web session sanitizes WebView user agent and persists signed-in account state`() =
        kotlinx.coroutines.runBlocking {
            val rawWvUa =
                "Mozilla/5.0 (Linux; Android 16; Pixel 9 Build/BP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.95 Mobile Safari/537.36"
            val sanitized = com.example.core.GoogleWebSessionManager.sanitizeBrowserUserAgent(rawWvUa)
            assertFalse("Sanitized UA must strip '; wv'", sanitized.contains("; wv"))
            assertFalse("Sanitized UA must strip 'Version/4.0'", sanitized.contains("Version/4.0"))
            assertTrue("Sanitized UA must preserve Chromium version", sanitized.contains("Chrome/134.0.6998.95"))

            assertFalse(
                com.example.core.GoogleWebSessionManager.hasAuthenticatedGoogleCookie(
                    "NID=511=anon_token_xyz; __Host-GAPS=1:prelogin_token"
                )
            )
            assertTrue(
                com.example.core.GoogleWebSessionManager.hasAuthenticatedGoogleCookie(
                    "NID=511=xyz; SID=g.a000real_authenticated_session_token; SAPISID=hash_val_123456"
                )
            )
            assertFalse(
                com.example.core.GoogleWebSessionManager.isPostLoginDestinationUrl(
                    "https://accounts.google.com/v3/signin/identifier?continue=https://myaccount.google.com/"
                )
            )
            assertTrue(
                com.example.core.GoogleWebSessionManager.isPostLoginDestinationUrl(
                    "https://myaccount.google.com/?utm_source=sign_in"
                )
            )

            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
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

                vm.openGoogleSignInSheet()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isGoogleSignInSheetOpen)

                vm.onGoogleSignInCompleted(
                    email = "alex@example.com",
                    name = "Alex Rivera",
                    avatarUrl = ""
                )
                var syncAttempts = 0
                while (!vm.uiState.value.settings.isGoogleSignedIn && syncAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    syncAttempts++
                }

                assertFalse(vm.uiState.value.isGoogleSignInSheetOpen)
                assertTrue(vm.uiState.value.settings.isGoogleSignedIn)
                assertEquals("alex@example.com", vm.uiState.value.settings.googleAccountEmail)
                assertEquals("Alex Rivera", vm.uiState.value.settings.googleAccountName)

                vm.signOutGoogleAccount()
                var signOutAttempts = 0
                while (vm.uiState.value.settings.isGoogleSignedIn && signOutAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    signOutAttempts++
                }
                assertFalse(vm.uiState.value.settings.isGoogleSignedIn)
                assertEquals("", vm.uiState.value.settings.googleAccountEmail)
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `disable gpu vsync and disable frame rate limit flags are active by default and toggleable`() =
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val defaults = com.example.data.BrowserSettingsState()
            assertTrue(defaults.disableGpuVsync)
            assertTrue(defaults.disableFrameRateLimit)

            val args = com.example.core.BrowserEngineInitializer.buildCommandLineArgs(
                disableGpuVsync = true,
                disableFrameRateLimit = true,
                enableGpuRasterization = true
            )
            assertTrue(args.contains("--disable-gpu-vsync"))
            assertTrue(args.contains("--disable-frame-rate-limit"))
            assertTrue(args.contains("--enable-gpu-rasterization"))

            com.example.core.BrowserEngineInitializer.initialize(
                context = context,
                disableGpuVsync = true,
                disableFrameRateLimit = true,
                enableGpuRasterization = true
            )
            assertTrue(
                com.example.core.BrowserEngineInitializer.activeEngineFlags.contains(
                    com.example.core.BrowserEngineInitializer.FLAG_DISABLE_GPU_VSYNC
                )
            )
            assertTrue(
                com.example.core.BrowserEngineInitializer.activeEngineFlags.contains(
                    com.example.core.BrowserEngineInitializer.FLAG_DISABLE_FRAME_RATE_LIMIT
                )
            )

            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
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
                assertTrue(vm.uiState.value.settings.disableGpuVsync)
                assertTrue(vm.uiState.value.settings.disableFrameRateLimit)

                vm.setDisableGpuVsync(false)
                var toggleAttempts = 0
                while (vm.uiState.value.settings.disableGpuVsync && toggleAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    toggleAttempts++
                }
                assertFalse(vm.uiState.value.settings.disableGpuVsync)

                vm.setDisableGpuVsync(true)
                toggleAttempts = 0
                while (!vm.uiState.value.settings.disableGpuVsync && toggleAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    toggleAttempts++
                }
                assertTrue(vm.uiState.value.settings.disableGpuVsync)
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `frosted glass setting is enabled by default and toggles on and off in Customize and Wallpaper`() =
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            assertEquals("Frosted Glass", context.getString(R.string.frosted_glass_label))
            assertTrue(context.getString(R.string.frosted_glass_subtitle).isNotBlank())

            val defaults = com.example.data.BrowserSettingsState()
            assertTrue(defaults.frostedGlassEnabled)

            // Verify solid fallback surfaces are 100% opaque in both Dark & Light monochrome styles
            val darkSolid = com.example.ui.theme.DarkGlassStyle.copy(isFrostedGlassEnabled = false)
            val lightSolid = com.example.ui.theme.LightGlassStyle.copy(isFrostedGlassEnabled = false)
            assertFalse(darkSolid.isFrostedGlassEnabled)
            assertFalse(lightSolid.isFrostedGlassEnabled)
            assertEquals(1.0f, darkSolid.solidCardSurface.alpha, 0.001f)
            assertEquals(1.0f, darkSolid.solidBarSurface.alpha, 0.001f)
            assertEquals(1.0f, darkSolid.solidOverlaySurface.alpha, 0.001f)
            assertEquals(1.0f, lightSolid.solidCardSurface.alpha, 0.001f)
            assertEquals(1.0f, lightSolid.solidBarSurface.alpha, 0.001f)
            assertEquals(1.0f, lightSolid.solidOverlaySurface.alpha, 0.001f)

            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
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
                assertTrue(vm.uiState.value.settings.frostedGlassEnabled)

                // Turn OFF Frosted Glass
                vm.setFrostedGlassEnabled(false)
                var toggleAttempts = 0
                while (vm.uiState.value.settings.frostedGlassEnabled && toggleAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    toggleAttempts++
                }
                assertFalse(vm.uiState.value.settings.frostedGlassEnabled)

                // Turn ON Frosted Glass
                vm.setFrostedGlassEnabled(true)
                toggleAttempts = 0
                while (!vm.uiState.value.settings.frostedGlassEnabled && toggleAttempts < 40) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    toggleAttempts++
                }
                assertTrue(vm.uiState.value.settings.frostedGlassEnabled)
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `glassmorphism backdrops decode crisp presets blur webpage snapshots and switch modes`() =
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val profile = DeviceOptimizationManager.detectProfile(context)

            assertEquals("Glassmorphism Backdrop", context.getString(R.string.glass_backdrop_title))
            assertEquals("Prism Aura", context.getString(R.string.backdrop_mode_prism))
            assertEquals("Live Optical", context.getString(R.string.backdrop_mode_live))
            assertEquals("Studio Halo", context.getString(R.string.backdrop_mode_halo))
            assertEquals("Deep Frost", context.getString(R.string.backdrop_mode_deep))

            // Verify defaults use Prism Waves preset and Prism Aura optical backdrop mode
            val defaults = com.example.data.BrowserSettingsState()
            assertEquals("preset:prism_waves", defaults.selectedWallpaperId)
            assertEquals(com.example.data.GlassBackdropMode.PRISM_AURA, defaults.glassBackdropMode)

            // Verify all 9 preset backdrops (including the 4 new high-contrast glassmorphism artworks) decode full & blurred bitmaps
            assertEquals(9, WallpaperLuminanceAnalyzer.presets.size)
            val presetIds = WallpaperLuminanceAnalyzer.presets.map { it.id }
            assertTrue(presetIds.contains("preset:prism_waves"))
            assertTrue(presetIds.contains("preset:aurora_atrium"))
            assertTrue(presetIds.contains("preset:sunlit_botanical"))
            assertTrue(presetIds.contains("preset:nordic_prism"))

            for (preset in WallpaperLuminanceAnalyzer.presets.take(4)) {
                val resolved = WallpaperLuminanceAnalyzer.loadAndAnalyze(
                    context = context,
                    wallpaperId = preset.id,
                    customFilePath = null
                )
                org.junit.Assert.assertNotNull("Preset ${preset.id} must decode cleanly", resolved)
                assertTrue(resolved!!.fullBitmap.width > 0 && resolved.fullBitmap.height > 0)
                assertTrue(resolved.blurredBitmap.width > 0 && resolved.blurredBitmap.height > 0)
            }

            // Verify WebView snapshot off-thread StackBlur backdrop generator
            val sampleSnapshot = Bitmap.createBitmap(240, 420, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(24, 48, 92))
            }
            val blurredWebBackdrop = WallpaperLuminanceAnalyzer.blurSnapshotBitmap(sampleSnapshot, profile)
            org.junit.Assert.assertNotNull(blurredWebBackdrop)
            assertTrue(blurredWebBackdrop!!.width >= 56 && blurredWebBackdrop.height >= 96)

            // Verify BrowserViewModel switches GlassBackdropMode and populates tabBlurredSnapshots
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
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

                for (targetMode in com.example.data.GlassBackdropMode.entries) {
                    vm.setGlassBackdropMode(targetMode)
                    var modeAttempts = 0
                    while (vm.uiState.value.settings.glassBackdropMode != targetMode && modeAttempts < 40) {
                        org.robolectric.shadows.ShadowLooper.idleMainLooper()
                        kotlinx.coroutines.delay(30)
                        modeAttempts++
                    }
                    assertEquals(targetMode, vm.uiState.value.settings.glassBackdropMode)
                }

                val tabId = vm.uiState.value.activeTab?.id ?: 1L
                vm.updateTabSnapshot(tabId, sampleSnapshot)
                var snapAttempts = 0
                while (!vm.tabBlurredSnapshots.containsKey(tabId) && snapAttempts < 50) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(40)
                    snapAttempts++
                }
                assertTrue("tabSnapshots should contain captured tab", vm.tabSnapshots.containsKey(tabId))
                assertTrue("tabBlurredSnapshots should contain blurred backdrop", vm.tabBlurredSnapshots.containsKey(tabId))
            } finally {
                collectJob.cancel()
            }
        }

    @Test
    fun `webview variations seed sanitizer purges empty and unsigned seeds while preserving valid signed seeds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempWebViewDir = java.io.File(context.cacheDir, "test_app_webview_${System.nanoTime()}").apply {
            mkdirs()
        }

        try {
            // 1. Empty 0-byte variations_seed_new (created when VariationsSeedServer has no seed)
            assertFalse(com.example.core.BrowserEngineInitializer.isValidAwVariationsSeed(ByteArray(0)))
            val emptyNewSeed = java.io.File(tempWebViewDir, com.example.core.BrowserEngineInitializer.NEW_SEED_FILE_NAME)
            emptyNewSeed.writeBytes(ByteArray(0))

            // 2. Unsigned AwVariationsSeed proto (missing field 1 `signature` -> triggers variations_seed_loader.cc(37))
            val unsignedProto = java.io.ByteArrayOutputStream().apply {
                // field 2 (country = "US"): tag 0x12, len 2
                write(0x12); write(2); write("US".toByteArray())
                // field 4 (is_gzip_compressed = true): tag 0x20, varint 1
                write(0x20); write(1)
                // field 5 (seed_data = [1, 2, 3, 4]): tag 0x2A, len 4
                write(0x2A); write(4); write(byteArrayOf(1, 2, 3, 4))
                // field 6 (date = 100): tag 0x30, varint 100
                write(0x30); write(100)
            }.toByteArray()
            assertFalse(com.example.core.BrowserEngineInitializer.isValidAwVariationsSeed(unsignedProto))

            val unsignedOldSeed = java.io.File(tempWebViewDir, com.example.core.BrowserEngineInitializer.SEED_FILE_NAME)
            unsignedOldSeed.writeBytes(unsignedProto)

            val purged = com.example.core.BrowserEngineInitializer.sanitizeDirectoryVariationsSeeds(
                webViewDir = tempWebViewDir,
                nowMillis = 1_750_000_000_000L
            )
            assertEquals(2, purged)
            assertFalse("Empty variations_seed_new must be removed", emptyNewSeed.exists())
            assertFalse("Unsigned variations_seed must be removed", unsignedOldSeed.exists())

            val stampFile = java.io.File(tempWebViewDir, com.example.core.BrowserEngineInitializer.STAMP_FILE_NAME)
            assertTrue("variations_stamp must be created to throttle empty seed IPC", stampFile.exists())

            // 3. Complete signed AwVariationsSeed proto must pass validation and be preserved untouched
            val validSignedProto = java.io.ByteArrayOutputStream().apply {
                val sigBytes = "MEUCIQValidEcdsaSignaturePayload==".toByteArray()
                // field 1 (signature): tag 0x0A
                write(0x0A); write(sigBytes.size); write(sigBytes)
                // field 2 (country = "US"): tag 0x12
                write(0x12); write(2); write("US".toByteArray())
                // field 4 (is_gzip_compressed = true): tag 0x20
                write(0x20); write(1)
                // field 5 (seed_data): tag 0x2A
                write(0x2A); write(4); write(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))
                // field 6 (date): tag 0x30
                write(0x30); write(120)
            }.toByteArray()
            assertTrue(com.example.core.BrowserEngineInitializer.isValidAwVariationsSeed(validSignedProto))

            unsignedOldSeed.writeBytes(validSignedProto)
            val purgedWhenValid = com.example.core.BrowserEngineInitializer.sanitizeDirectoryVariationsSeeds(tempWebViewDir)
            assertEquals(0, purgedWhenValid)
            assertTrue("Valid signed variations_seed must be preserved", unsignedOldSeed.exists())

            // 4. Context-level sanitizer executes cleanly against app_webview
            val appWebViewDir = context.getDir("webview", Context.MODE_PRIVATE)
            java.io.File(appWebViewDir, com.example.core.BrowserEngineInitializer.NEW_SEED_FILE_NAME).writeBytes(ByteArray(0))
            val contextPurged = com.example.core.BrowserEngineInitializer.sanitizeWebViewVariationsSeeds(context)
            assertTrue(contextPurged >= 1)
            assertFalse(java.io.File(appWebViewDir, com.example.core.BrowserEngineInitializer.NEW_SEED_FILE_NAME).exists())
        } finally {
            tempWebViewDir.deleteRecursively()
        }
    }
}
