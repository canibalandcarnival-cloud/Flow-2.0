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
import com.example.core.SiteNavBarVariant
import com.example.core.SiteNavigationEngine
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
    fun `site navigation engine builds per-site bars parses extracted dom links and resolves site search`() {
        val placeholder = SiteNavigationEngine.buildInitialPlaceholderForUrl("https://www.dezeen.com/design/interiors")
        assertEquals("Dezeen", placeholder.siteBrand)
        assertEquals("DZ", placeholder.monogram)
        assertEquals("https://www.dezeen.com/", placeholder.homeUrl)
        assertTrue(placeholder.items.any { it.label == "Home" })
        assertTrue(placeholder.items.any { it.label == "Interiors" && it.isActive })

        val bridgePayload = """
            {
              "origin": "https://github.com",
              "pageUrl": "https://github.com/trending",
              "brand": "GitHub",
              "homeUrl": "https://github.com/",
              "searchTemplate": "https://github.com/search?q={query}",
              "hiddenCount": 2,
              "items": [
                { "label": "GitHub", "url": "https://github.com/", "isActive": false, "isAnchor": false },
                { "label": "Product", "url": "https://github.com/features", "isActive": false, "isAnchor": false },
                { "label": "Trending", "url": "https://github.com/trending", "isActive": true, "isAnchor": false },
                { "label": "Pricing", "url": "https://github.com/pricing", "isActive": false, "isAnchor": false }
              ]
            }
        """.trimIndent()

        val parsed = SiteNavigationEngine.parseBridgeJson(bridgePayload, "https://github.com/trending")
        assertEquals("GitHub", parsed.siteBrand)
        assertEquals("GH", parsed.monogram)
        assertEquals(2, parsed.hiddenNavCount)
        assertTrue(parsed.items.any { it.label == "Home" })
        assertTrue(parsed.items.any { it.label == "Trending" && it.isActive })
        assertFalse("Brand logo duplicate should be represented by Home pill", parsed.items.any { it.label == "GitHub" })

        val searchUrl = SiteNavigationEngine.resolveSiteSearchUrl("compose", parsed, "https://github.com/trending")
        assertEquals("https://github.com/search?q=compose", searchUrl)

        val fallbackSearch = SiteNavigationEngine.resolveSiteSearchUrl("ceramics", placeholder, "https://www.dezeen.com/design")
        assertTrue(fallbackSearch.startsWith("https://www.google.com/search?q="))
        assertTrue(fallbackSearch.contains("dezeen.com"))

        // Minimalist site with empty DOM nav items still receives a custom per-site navigation bar
        val sparseJson = """{"origin":"https://example.org","pageUrl":"https://example.org/docs","brand":"","homeUrl":"https://example.org/","items":[]}"""
        val sparseNav = SiteNavigationEngine.parseBridgeJson(sparseJson, "https://example.org/docs")
        assertEquals("Example", sparseNav.siteBrand)
        assertEquals(SiteNavBarVariant.UNIVERSAL_LIQUID_CAPSULE, sparseNav.variant)
        assertTrue(sparseNav.items.isNotEmpty())
        assertTrue(sparseNav.items.any { it.label == "Docs" })

        // Verify YouTube Safari Island variant preserves YouTube's original navbar & docks beneath it
        val ytNav = SiteNavigationEngine.buildInitialPlaceholderForUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(SiteNavBarVariant.YOUTUBE_SAFARI_ISLAND, ytNav.variant)
        assertEquals("youtube.com", ytNav.displayDomain)
        assertTrue(ytNav.preservesOriginalNavBar)
        assertTrue(ytNav.docksBeneathSiteBottomBar)

        // Verify distinct per-site custom navigation bar variants for other websites
        assertEquals(SiteNavBarVariant.EDITORIAL_READER_DECK, placeholder.variant)
        assertEquals("dezeen.com", placeholder.displayDomain)
        assertFalse(placeholder.preservesOriginalNavBar)

        assertEquals(SiteNavBarVariant.DEV_COMMAND_HUD, parsed.variant)
        assertEquals("github.com", parsed.displayDomain)
        assertFalse(parsed.preservesOriginalNavBar)

        val pinterestNav = SiteNavigationEngine.buildInitialPlaceholderForUrl("https://www.pinterest.com/ideas")
        assertEquals(SiteNavBarVariant.SOCIAL_FLOATING_ORBS, pinterestNav.variant)
        assertEquals("pinterest.com", pinterestNav.displayDomain)
        assertTrue(pinterestNav.docksBeneathSiteBottomBar)
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
                assertFalse("Inline video must not trigger fullscreen hide", vm.uiState.value.isFullscreenVideo)

                vm.onWebFullscreenVideoChanged(tabId = activeTabId, isFullscreen = true)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isFullscreenVideo)

                vm.onWebFullscreenVideoChanged(tabId = activeTabId, isFullscreen = false)
                vm.onWebVideoPlaybackChanged(tabId = activeTabId, isPlaying = false)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.isVideoPlaying)
                assertFalse(vm.uiState.value.isFullscreenVideo)

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

                // Verify searchbar hides immediately on entering a site (isViewingWebPage == true)
                // and returns when going back to Start Page (isViewingWebPage == false)
                assertFalse(vm.uiState.value.isViewingWebPage)
                assertTrue(vm.uiState.value.settings.replaceSiteNavBar)
                val targetTabId = vm.uiState.value.activeTab?.id ?: activeTabId
                vm.onWebPageStarted(targetTabId, "https://en.m.wikipedia.org/wiki/Glass")
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isViewingWebPage)
                assertEquals("Wikipedia", vm.uiState.value.siteNavigation.siteBrand)

                vm.onSiteNavigationJsonReceived(
                    targetTabId,
                    """{"origin":"https://en.m.wikipedia.org","pageUrl":"https://en.m.wikipedia.org/wiki/Glass","brand":"Wikipedia","homeUrl":"https://en.m.wikipedia.org/","hiddenCount":1,"items":[{"label":"Main page","url":"https://en.m.wikipedia.org/wiki/Main_Page","isActive":false,"isAnchor":false},{"label":"History","url":"https://en.m.wikipedia.org/wiki/Glass#History","isActive":false,"isAnchor":true}]}"""
                )
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val wikiItems = vm.uiState.value.siteNavigation.items
                assertTrue(wikiItems.any { it.label == "Main page" })
                val historyAnchor = wikiItems.first { it.label == "History" }
                vm.onSiteNavItemClicked(historyAnchor)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(30)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.siteNavigation.items.first { it.label == "History" }.isActive)

                vm.returnToStartPage()
                var homeAttempts = 0
                while (vm.uiState.value.isViewingWebPage && homeAttempts < 50) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    homeAttempts++
                }
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.isViewingWebPage)

                vm.setReplaceSiteNavBar(false)
                var offAttempts = 0
                while (vm.uiState.value.settings.replaceSiteNavBar && offAttempts < 50) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    offAttempts++
                }
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.settings.replaceSiteNavBar)

                vm.setReplaceSiteNavBar(true)
                var onAttempts = 0
                while (!vm.uiState.value.settings.replaceSiteNavBar && onAttempts < 50) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(30)
                    onAttempts++
                }
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.settings.replaceSiteNavBar)

                dao.clearCompletedDownloads()
            } finally {
                collectJob.cancel()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
            }
        }

    @Test
    fun `firebase app check debug token is persisted synchronously and evicts stale token cache on update`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token-ios26")
        }
        configureAppCheck(context, launchIntent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        val tokenCachePrefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertNotNull(debugPrefs)
        assertEquals(
            "test-debug-token-ios26",
            debugPrefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )

        // Simulate a stale cached JWT stored from a prior unregistered session
        tokenCachePrefs.edit()
            .putString("com.google.firebase.appcheck.APP_CHECK_TOKEN", "stale-invalid-jwt")
            .commit()
        assertEquals(
            "stale-invalid-jwt",
            tokenCachePrefs.getString("com.google.firebase.appcheck.APP_CHECK_TOKEN", null)
        )

        // Simulate warm singleTask relaunch (onNewIntent) delivering a refreshed debug token
        val refreshedIntent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "  refreshed-debug-token-v2  ")
        }
        configureAppCheck(context, refreshedIntent)

        assertEquals(
            "refreshed-debug-token-v2",
            debugPrefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )
        assertEquals(
            null,
            tokenCachePrefs.getString("com.google.firebase.appcheck.APP_CHECK_TOKEN", null)
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

    @Test
    fun `savanna canopy default wallpaper 12 presets hovering link options and account sheet transitions`() =
        kotlinx.coroutines.runBlocking {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()

            // 1. Default Start Page wallpaper is Savanna Canopy ("preset:savanna") and all 12 presets decode cleanly
            assertEquals("preset:savanna", com.example.data.BrowserSettingsState().selectedWallpaperId)
            assertEquals(12, WallpaperLuminanceAnalyzer.presets.size)
            assertEquals("preset:savanna", WallpaperLuminanceAnalyzer.presets.first().id)
            assertEquals("Savanna Canopy", WallpaperLuminanceAnalyzer.presets.first().title)

            for (preset in WallpaperLuminanceAnalyzer.presets) {
                val analyzed = WallpaperLuminanceAnalyzer.loadAndAnalyze(
                    context = app,
                    wallpaperId = preset.id,
                    customFilePath = null
                )
                assertNotNull("Preset ${preset.id} must decode non-null wallpaper", analyzed)
            }

            // 2. Verify ViewModel link options hover state & Google Account sheet state transitions
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

                assertEquals("preset:savanna", vm.uiState.value.settings.selectedWallpaperId)

                val sampleLinkTarget = com.example.ui.screens.WebHitContextTarget(
                    targetUrl = "https://www.dezeen.com/architecture",
                    imageUrl = null,
                    isImage = false
                )
                vm.setLinkContextTarget(sampleLinkTarget)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(25)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertEquals(sampleLinkTarget, vm.uiState.value.activeLinkContextTarget)

                // Back button dismisses hovering link options sheet first
                vm.onBackButtonClicked()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(25)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertEquals(null, vm.uiState.value.activeLinkContextTarget)

                // Account login sheet opens and closes via Back button
                vm.setAccountSheetOpen(true)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(25)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertTrue(vm.uiState.value.isAccountSheetOpen)

                vm.onBackButtonClicked()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(25)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertFalse(vm.uiState.value.isAccountSheetOpen)
            } finally {
                collectJob.cancel()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
            }
        }
}

