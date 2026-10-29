package com.example.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.DisplayRefreshRateManager
import com.example.core.GpuRenderController
import com.example.core.OsShellManager
import com.example.core.SiriAiUiState
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.GlassMotionTracker
import com.example.ui.components.LocalSpatialBackdrop
import com.example.ui.components.SpatialBackdropState
import com.example.ui.components.configureBlurLayer
import com.example.ui.screens.AddressSearchOverlay
import com.example.ui.screens.BottomBrowserGlassBar
import com.example.ui.screens.BrowserHubContent
import com.example.ui.screens.FindInPageGlassBar
import com.example.ui.screens.NewTabGroupDialog
import com.example.ui.screens.SiriIos26AssistantOverlay
import com.example.ui.screens.SiriIos26PerimeterAura
import com.example.ui.screens.TabSwitcherOverlay
import com.example.ui.screens.TopSegmentedGlassBar
import com.example.ui.screens.WallpaperAndSettingsSheet
import com.example.ui.screens.WebBrowserView
import com.example.ui.screens.WebToolsGlassSheet
import com.example.ui.theme.GlassBrowserTheme
import com.example.ui.theme.LocalGlassStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GlassBrowserApp(
    systemIntentFlow: StateFlow<OsShellManager.SystemShellIntentAction?> = remember {
        MutableStateFlow(null)
    },
    onConsumeSystemIntent: () -> Unit = {},
    viewModel: BrowserViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webCommand by viewModel.webCommand.collectAsStateWithLifecycle()
    val incomingSystemAction by systemIntentFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var displayCapability by remember {
        mutableStateOf(DisplayRefreshRateManager.DisplayCapability(120, 120, 360))
    }
    var osRoleStatus by remember {
        mutableStateOf(OsShellManager.queryRoleStatus(context))
    }
    var isNewGroupDialogOpen by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val glassMotionTracker = remember { GlassMotionTracker() }

    // Refresh OS Role status (ROLE_HOME / ROLE_BROWSER) and re-apply immersive OS bars on resume
    DisposableEffect(lifecycleOwner, context, uiState.settings.immersiveOsMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                osRoleStatus = OsShellManager.queryRoleStatus(context)
                val activity = context as? Activity
                if (activity != null) {
                    OsShellManager.applyImmersiveOsMode(
                        activity = activity,
                        immersiveEnabled = uiState.settings.immersiveOsMode
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle incoming OS Home button presses (CATEGORY_HOME) and external http/https/search intents
    LaunchedEffect(incomingSystemAction) {
        when (val action = incomingSystemAction) {
            OsShellManager.SystemShellIntentAction.GoHome -> {
                isNewGroupDialogOpen = false
                viewModel.onSystemHomeAction()
                onConsumeSystemIntent()
            }
            is OsShellManager.SystemShellIntentAction.OpenUrlOrQuery -> {
                isNewGroupDialogOpen = false
                viewModel.openUrlOrSearch(action.rawInput)
                onConsumeSystemIntent()
            }
            null -> Unit
        }
    }

    // Apply up-to-360Hz refresh rate & touch boost whenever High Refresh Rate setting changes
    LaunchedEffect(uiState.settings.highRefreshRateEnabled) {
        val activity = context as? Activity
        if (activity != null) {
            displayCapability = DisplayRefreshRateManager.applyHighRefreshRate(
                activity = activity,
                enabled = uiState.settings.highRefreshRateEnabled
            )
        }
    }

    // Apply Force GPU Rendering, Enable GPU Rasterization & Disable Hardware Overlays live to Activity Window
    LaunchedEffect(
        uiState.settings.forceGpuRendering,
        uiState.settings.enableGpuRasterization,
        uiState.settings.disableHardwareOverlays
    ) {
        val activity = context as? Activity
        if (activity != null) {
            GpuRenderController.applyWindowGpuPipeline(
                activity = activity,
                forceGpuRendering = uiState.settings.forceGpuRendering,
                disableHardwareOverlays = uiState.settings.disableHardwareOverlays,
                enableGpuRasterization = uiState.settings.enableGpuRasterization
            )
        }
    }

    // Apply Immersive Fullscreen OS Mode live
    LaunchedEffect(uiState.settings.immersiveOsMode) {
        val activity = context as? Activity
        if (activity != null) {
            OsShellManager.applyImmersiveOsMode(
                activity = activity,
                immersiveEnabled = uiState.settings.immersiveOsMode
            )
        }
    }

    // Update status bar icon legibility dynamically based on wallpaper luminance / active view
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        val useDarkStatusIcons = when {
            uiState.isTabSwitcherOpen -> !uiState.settings.isDarkMode
            uiState.isViewingWebPage -> !uiState.settings.isDarkMode
            else -> uiState.isWallpaperLight
        }
        insetsController.isAppearanceLightStatusBars = useDarkStatusIcons
        insetsController.isAppearanceLightNavigationBars = !uiState.settings.isDarkMode
    }

    val osRoleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        osRoleStatus = OsShellManager.queryRoleStatus(context)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomWallpaper(uri)
        }
    }

    val hasInternalBackTarget = isNewGroupDialogOpen ||
        uiState.isSiriOpen ||
        uiState.isWebToolsSheetOpen ||
        uiState.isFindInPageOpen ||
        uiState.isAddressOverlayOpen ||
        uiState.isSettingsSheetOpen ||
        uiState.isTabSwitcherOpen ||
        uiState.isViewingWebPage ||
        uiState.activeSection != TopHubSection.START_PAGE

    BackHandler(enabled = true) {
        when {
            isNewGroupDialogOpen -> isNewGroupDialogOpen = false
            hasInternalBackTarget -> viewModel.onBackButtonClicked()
            else -> viewModel.feedback.onGlassTap()
        }
    }

    // Hardware GraphicsLayers for multi-plane spatial frosted glass on the Start Page
    val wallpaperBlurredLayer = rememberGraphicsLayer()
    val sceneBlurredLayer = rememberGraphicsLayer()

    val cardBlurPx = with(density) { uiState.deviceProfile.cardBlurDp.dp.toPx() }
    val barBlurPx = with(density) { uiState.deviceProfile.barBlurDp.dp.toPx() }

    LaunchedEffect(
        wallpaperBlurredLayer,
        sceneBlurredLayer,
        cardBlurPx,
        barBlurPx
    ) {
        configureBlurLayer(wallpaperBlurredLayer, cardBlurPx)
        configureBlurLayer(sceneBlurredLayer, barBlurPx)
    }

    val activeTab = uiState.activeTab
    val webBlurredBackdrop = viewModel.liveWebBlurredBackdrop

    val spatialBackdrop = remember(
        wallpaperBlurredLayer,
        sceneBlurredLayer,
        uiState.isViewingWebPage,
        webBlurredBackdrop,
        uiState.resolvedWallpaper?.blurredBitmap,
        rootSize,
        glassMotionTracker
    ) {
        if (uiState.isViewingWebPage) {
            SpatialBackdropState(
                wallpaperBlurredLayer = null,
                sceneBlurredLayer = null,
                fallbackBlurredBitmap = webBlurredBackdrop ?: uiState.resolvedWallpaper?.blurredBitmap,
                isWebBackdrop = webBlurredBackdrop != null,
                rootSize = rootSize,
                motionTracker = glassMotionTracker
            )
        } else {
            SpatialBackdropState(
                wallpaperBlurredLayer = wallpaperBlurredLayer,
                sceneBlurredLayer = sceneBlurredLayer,
                fallbackBlurredBitmap = uiState.resolvedWallpaper?.blurredBitmap,
                isWebBackdrop = false,
                rootSize = rootSize,
                motionTracker = glassMotionTracker
            )
        }
    }

    val backgroundZoomScale by animateFloatAsState(
        targetValue = if (uiState.isTabSwitcherOpen) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 330f),
        label = "sceneZoomScale"
    )

    val statusTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isSiriBusy = uiState.siriAiState is SiriAiUiState.Loading ||
        uiState.siriAiState is SiriAiUiState.Streaming

    GlassBrowserTheme(
        darkTheme = uiState.settings.isDarkMode
    ) {
        val style = LocalGlassStyle.current

        CompositionLocalProvider(LocalSpatialBackdrop provides spatialBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.glassBaseColor)
                    .onSizeChanged { rootSize = it }
            ) {
                // =========================================================================
                // PLANE 1: Isolated Browser Content (Full-Bleed WebView on Sites; Untinted Wallpaper on Start Page)
                // =========================================================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (backgroundZoomScale != 1f) {
                                Modifier.graphicsLayer {
                                    scaleX = backgroundZoomScale
                                    scaleY = backgroundZoomScale
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (activeTab != null && activeTab.url.isNotBlank()) {
                        WebBrowserView(
                            tabId = activeTab.id,
                            url = activeTab.url,
                            isPrivate = activeTab.isPrivate,
                            forceGpuRendering = uiState.settings.forceGpuRendering,
                            enableGpuRasterization = uiState.settings.enableGpuRasterization,
                            disableHardwareOverlays = uiState.settings.disableHardwareOverlays,
                            isTabSwitcherOpen = uiState.isTabSwitcherOpen,
                            deviceProfile = uiState.deviceProfile,
                            webCommand = webCommand,
                            onConsumeCommand = viewModel::consumeWebCommand,
                            onPageStarted = viewModel::onWebPageStarted,
                            onProgressChanged = viewModel::onWebProgressChanged,
                            onPageFinished = viewModel::onWebPageFinished,
                            onVideoPlaybackChanged = viewModel::onWebVideoPlaybackChanged,
                            onSnapshotCaptured = viewModel::updateTabSnapshot,
                            onLiveBackdropCaptured = viewModel::updateLiveWebBackdropOnly,
                            onFindResultsUpdated = viewModel::onFindResultsUpdated,
                            onSslSummaryUpdated = viewModel::updateSslCertificateSummary,
                            onPageAiContextUpdated = viewModel::updatePageAiContext,
                            onDownloadRequested = viewModel::startWebOrDirectDownload,
                            onSaveBlobDownload = viewModel::saveBlobBase64Download,
                            onOpenUrlInNewTab = viewModel::openUrlInNewTab,
                            onAskSiriAboutUrl = { targetUrl ->
                                viewModel.submitSiriPrompt(
                                    "Explain what this link or resource is in context of the page: $targetUrl"
                                )
                            },
                            onCopyUrl = { linkUrl -> viewModel.copyUrlToClipboard(context, linkUrl) },
                            onEdgeBounce = viewModel.feedback::onEdgeBounce,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    drawContent()
                                    sceneBlurredLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer()
                                    .drawWithContent {
                                        drawContent()
                                        wallpaperBlurredLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                            ) {
                                val resolvedWp = uiState.resolvedWallpaper
                                if (resolvedWp != null) {
                                    Image(
                                        bitmap = resolvedWp.fullBitmap,
                                        contentDescription = null,
                                        colorFilter = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.wp_forest_canopy),
                                        contentDescription = null,
                                        colorFilter = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            BrowserHubContent(
                                uiState = uiState,
                                topContentPadding = statusTopInset + 74.dp,
                                bottomContentPadding = navBottomInset + 92.dp,
                                onOpenUrl = viewModel::openUrlOrSearch,
                                onToggleShowAllFavorites = viewModel::toggleShowAllFavorites,
                                onToggleShowAllReadingList = viewModel::toggleShowAllReadingList,
                                onOpenCustomizeSheet = { viewModel.setSettingsSheetOpen(true) },
                                onDeleteReadingItem = viewModel::removeReadingListItem,
                                onDeleteBookmark = viewModel::removeBookmark,
                                onClearHistory = viewModel::clearBrowsingHistory,
                                onStartDirectDownload = { directUrl ->
                                    viewModel.startWebOrDirectDownload(url = directUrl)
                                },
                                onOpenDownload = { item -> viewModel.openDownloadItem(context, item) },
                                onShareDownload = { item -> viewModel.shareDownloadItem(context, item) },
                                onRetryDownload = viewModel::retryDownloadItem,
                                onDeleteDownload = viewModel::deleteDownloadItem,
                                onClearCompletedDownloads = viewModel::clearCompletedDownloads,
                                onEdgeBounce = viewModel.feedback::onEdgeBounce
                            )
                        }
                    }
                }

                // =========================================================================
                // PLANE 2: Floating Spatial Frosted Glass Chrome, WebView Tools & Siri iOS 26
                // =========================================================================

                // Top 5-Segment Glass Pill (Start Page | Bookmarks | Reading List | History | Downloads)
                AnimatedVisibility(
                    visible = !uiState.isViewingWebPage && !uiState.isTabSwitcherOpen,
                    enter = fadeIn(spring(stiffness = 400f)) +
                        slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    TopSegmentedGlassBar(
                        activeSection = uiState.activeSection,
                        activeDownloadsCount = uiState.activeDownloadsCount,
                        onSelectSection = viewModel::selectTopSection
                    )
                }

                // Floating & Draggable Find-in-Page Frosted Bar when viewing a webpage
                AnimatedVisibility(
                    visible = uiState.isViewingWebPage &&
                        uiState.isFindInPageOpen &&
                        !uiState.isTabSwitcherOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    FindInPageGlassBar(
                        query = uiState.findQuery,
                        activeMatchOrdinal = uiState.findActiveMatch,
                        totalMatches = uiState.findTotalMatches,
                        onQueryChange = viewModel::updateFindQuery,
                        onFindPrev = { viewModel.findNextMatch(forward = false) },
                        onFindNext = { viewModel.findNextMatch(forward = true) },
                        onClose = viewModel::closeFindInPage
                    )
                }

                // Bottom Floating & Movable Browser Glass Bar (Back + Search/URL + Siri Orb + Tabs)
                val isBottomBarVisible = !uiState.isTabSwitcherOpen &&
                    !uiState.isAddressOverlayOpen &&
                    !uiState.isSiriOpen &&
                    !(uiState.isViewingWebPage && uiState.isVideoPlaying)

                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = fadeIn(animationSpec = spring(stiffness = 380f)) +
                        slideInVertically(
                            initialOffsetY = { fullHeight -> (fullHeight * 1.35f).toInt() },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f)
                        ),
                    exit = fadeOut(animationSpec = spring(stiffness = 360f)) +
                        slideOutVertically(
                            targetOffsetY = { fullHeight -> (fullHeight * 1.35f).toInt() },
                            animationSpec = spring(dampingRatio = 0.80f, stiffness = 300f)
                        ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val canGoBack = uiState.isViewingWebPage ||
                        uiState.activeSection != TopHubSection.START_PAGE
                    BottomBrowserGlassBar(
                        currentUrl = uiState.activeTab?.url.orEmpty(),
                        canNavigateBack = canGoBack,
                        isLoading = uiState.isLoadingWebPage,
                        loadProgress = uiState.webLoadProgress,
                        isPrivateMode = uiState.settings.isPrivateMode,
                        isSiriActive = uiState.isSiriOpen || isSiriBusy,
                        onBackClick = viewModel::onBackButtonClicked,
                        onAddressBarClick = viewModel::openAddressOverlay,
                        onWebToolsOrVoiceClick = {
                            if (uiState.isViewingWebPage) {
                                viewModel.setWebToolsSheetOpen(true)
                            } else {
                                viewModel.toggleSiriOverlay()
                            }
                        },
                        onSiriClick = viewModel::toggleSiriOverlay,
                        onTabsClick = viewModel::openTabSwitcher,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }

                // Full-Screen Frosted Glass Tab Switcher Overlay
                AnimatedVisibility(
                    visible = uiState.isTabSwitcherOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        scaleIn(
                            initialScale = 0.90f,
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 350f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = spring(stiffness = 440f)
                        )
                ) {
                    TabSwitcherOverlay(
                        uiState = uiState,
                        tabSnapshots = viewModel.tabSnapshots,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onNewTab = { viewModel.createNewTab(uiState.activeTabGroupFilter) },
                        onDone = viewModel::closeTabSwitcher,
                        onSwitchPrivacyMode = viewModel::switchTabPrivacyMode,
                        onToggleSearch = viewModel::toggleTabSearch,
                        onSearchQueryChange = viewModel::updateTabSearchQuery,
                        onSelectGroupFilter = viewModel::setTabGroupFilter,
                        onOpenNewGroupDialog = { isNewGroupDialogOpen = true },
                        onOpenSettings = { viewModel.setSettingsSheetOpen(true) },
                        onEdgeBounce = viewModel.feedback::onEdgeBounce
                    )
                }

                // Omnibox URL / Search Frosted Overlay
                AnimatedVisibility(
                    visible = uiState.isAddressOverlayOpen,
                    enter = fadeIn(spring(stiffness = 450f)) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f)
                        ),
                    exit = fadeOut(spring(stiffness = 500f)) +
                        slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    AddressSearchOverlay(
                        currentUrl = uiState.activeTab?.url.orEmpty(),
                        isViewingWebPage = uiState.isViewingWebPage,
                        onSubmitUrlOrSearch = viewModel::openUrlOrSearch,
                        onAddToFavorites = viewModel::saveCurrentPageToFavorites,
                        onAddToReadingList = viewModel::saveCurrentPageToReadingList,
                        onAddBookmark = viewModel::saveCurrentPageToBookmarks,
                        onReloadPage = viewModel::reloadCurrentPage,
                        onOpenSiriAi = viewModel::toggleSiriOverlay,
                        onOpenDownloads = viewModel::openDownloadsHub,
                        onOpenSettings = { viewModel.setSettingsSheetOpen(true) },
                        onDismiss = viewModel::closeAddressOverlay
                    )
                }

                // WebView Components & Page Controls Frosted Sheet
                AnimatedVisibility(
                    visible = uiState.isWebToolsSheetOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    WebToolsGlassSheet(
                        currentUrl = uiState.activeTab?.url.orEmpty(),
                        pageTitle = uiState.activeTab?.title.orEmpty(),
                        sslCertificateSummary = uiState.sslCertificateSummary,
                        canGoForward = uiState.canWebGoForward,
                        isLoading = uiState.isLoadingWebPage,
                        isDesktopMode = uiState.isDesktopMode,
                        isReaderMode = uiState.isReaderMode,
                        textZoomPercent = uiState.webTextZoom,
                        activeDownloadsCount = uiState.activeDownloadsCount,
                        onGoForward = viewModel::goForwardWebPage,
                        onReloadOrStop = viewModel::stopOrReloadWebPage,
                        onToggleDesktopMode = viewModel::toggleDesktopMode,
                        onToggleReaderMode = viewModel::toggleReaderMode,
                        onChangeTextZoom = viewModel::updateWebTextZoom,
                        onOpenFindInPage = viewModel::openFindInPage,
                        onOpenSiriAi = viewModel::toggleSiriOverlay,
                        onOpenDownloadsHub = viewModel::openDownloadsHub,
                        onCopyUrl = {
                            viewModel.copyUrlToClipboard(
                                context = context,
                                url = uiState.activeTab?.url.orEmpty()
                            )
                        },
                        onSharePage = {
                            viewModel.sharePageUrl(
                                context = context,
                                title = uiState.activeTab?.title.orEmpty(),
                                url = uiState.activeTab?.url.orEmpty()
                            )
                        },
                        onDismiss = { viewModel.setWebToolsSheetOpen(false) }
                    )
                }

                // Wallpaper Picker ('+' Import), OS Shell Roles & Monochrome Settings Sheet
                AnimatedVisibility(
                    visible = uiState.isSettingsSheetOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    WallpaperAndSettingsSheet(
                        uiState = uiState,
                        displayCapability = displayCapability,
                        osRoleStatus = osRoleStatus,
                        onSelectDarkMode = viewModel::setDarkMode,
                        onSelectWallpaper = viewModel::selectWallpaper,
                        onImportWallpaperClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onDeleteCustomWallpaper = viewModel::deleteCustomWallpaper,
                        onRequestHomeOsRole = {
                            viewModel.feedback.onGlassTap()
                            try {
                                if (osRoleStatus.isDefaultHomeShell) {
                                    osRoleRequestLauncher.launch(Intent(Settings.ACTION_HOME_SETTINGS))
                                } else {
                                    osRoleRequestLauncher.launch(
                                        OsShellManager.createRequestHomeRoleIntent(context)
                                    )
                                }
                            } catch (_: Throwable) {}
                        },
                        onRequestBrowserRole = {
                            viewModel.feedback.onGlassTap()
                            try {
                                if (osRoleStatus.isDefaultBrowser) {
                                    osRoleRequestLauncher.launch(
                                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    )
                                } else {
                                    osRoleRequestLauncher.launch(
                                        OsShellManager.createRequestBrowserRoleIntent(context)
                                    )
                                }
                            } catch (_: Throwable) {}
                        },
                        onToggleImmersiveOsMode = viewModel::setImmersiveOsMode,
                        onToggleHighRefresh = viewModel::setHighRefreshRateEnabled,
                        onToggleForceGpu = viewModel::setForceGpuRendering,
                        onToggleGpuRasterization = viewModel::setEnableGpuRasterization,
                        onToggleDisableHwOverlays = viewModel::setDisableHardwareOverlays,
                        onToggleSfx = viewModel::setSfxEnabled,
                        onToggleHaptics = viewModel::setHapticsEnabled,
                        onCloseAllTabs = viewModel::closeAllCurrentModeTabs,
                        onEdgeBounce = viewModel.feedback::onEdgeBounce,
                        onDismiss = { viewModel.setSettingsSheetOpen(false) }
                    )
                }

                // New Tab Group Frosted Dialog
                AnimatedVisibility(
                    visible = isNewGroupDialogOpen,
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(dampingRatio = 0.64f, stiffness = 380f)
                    ),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f)
                ) {
                    NewTabGroupDialog(
                        onConfirmGroup = viewModel::createTabGroupFromCurrentOrNew,
                        onDismiss = { isNewGroupDialogOpen = false }
                    )
                }

                // iOS 26 Siri Full-Viewport Perimeter Aura (Zero Pointer Interception)
                SiriIos26PerimeterAura(
                    visible = uiState.isSiriOpen || isSiriBusy,
                    isIntense = isSiriBusy
                )

                // iOS 26 Siri Movable Liquid-Glass Conversational Card & Type-to-Siri Pill
                AnimatedVisibility(
                    visible = uiState.isSiriOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { it / 2 }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SiriIos26AssistantOverlay(
                        aiState = uiState.siriAiState,
                        chatHistory = uiState.siriHistory,
                        isViewingWebPage = uiState.isViewingWebPage,
                        activePageTitle = uiState.activeTab?.title.orEmpty(),
                        onSubmitPrompt = { prompt -> viewModel.submitSiriPrompt(prompt) },
                        onSummarizePage = viewModel::summarizeCurrentPage,
                        onAnalyzeVisualScreen = viewModel::analyzeVisualScreen,
                        onKeyTakeaways = viewModel::extractKeyTakeaways,
                        onSmartDigest = viewModel::generateSmartDigest,
                        onTranslatePage = viewModel::translateCurrentPageSummary,
                        onOpenSuggestedUrl = { suggested ->
                            viewModel.closeSiriOverlay()
                            viewModel.openUrlOrSearch(suggested)
                        },
                        onCancelGeneration = viewModel::cancelSiriGeneration,
                        onClearConversation = viewModel::clearSiriConversation,
                        onDismiss = viewModel::closeSiriOverlay,
                        onVoiceUnavailableToast = viewModel::showToast
                    )
                }

                // Floating Monochrome Frosted Status Pill Toast
                AnimatedVisibility(
                    visible = uiState.statusToast != null,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f)
                    ),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 66.dp)
                ) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(50),
                        elevation = GlassElevation.FLOATING_BAR,
                        useLiveSceneBackdrop = true
                    ) {
                        Text(
                            text = uiState.statusToast.orEmpty(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = style.contentPrimary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
