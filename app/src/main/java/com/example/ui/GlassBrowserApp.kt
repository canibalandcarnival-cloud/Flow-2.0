package com.example.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
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
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.DisplayRefreshRateManager
import com.example.core.GoogleAuthManager
import com.example.core.GpuRenderController
import com.example.core.OsShellManager
import com.example.core.SiriAiUiState
import com.example.core.SiteNavBarVariant
import com.example.data.BrowserTabEntity
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.GlassMotionTracker
import com.example.ui.components.LocalSpatialBackdrop
import com.example.ui.components.OverlappingTabsIcon
import com.example.ui.components.SpatialBackdropState
import com.example.ui.components.bouncyClickable
import com.example.ui.components.configureBlurLayer
import com.example.ui.screens.AccountAuthGlassSheet
import com.example.ui.screens.AddressSearchOverlay
import com.example.ui.screens.BottomBrowserGlassBar
import com.example.ui.screens.BrowserHubContent
import com.example.ui.screens.BrowserOpeningGlassAnimation
import com.example.ui.screens.FindInPageGlassBar
import com.example.ui.screens.NewTabGroupDialog
import com.example.ui.screens.SiriIos26AssistantOverlay
import com.example.ui.screens.SiriIos26PerimeterAura
import com.example.ui.screens.SiriLiquidOrbIcon
import com.example.ui.screens.SiteBookmarksQuickSheet
import com.example.ui.screens.SiteGlassNavigationBar
import com.example.ui.screens.SiteOpeningGlassVeil
import com.example.ui.screens.SitePortalTransitionContainer
import com.example.ui.screens.TabSwitcherOverlay
import com.example.ui.screens.TopSegmentedGlassBar
import com.example.ui.screens.WallpaperAndSettingsSheet
import com.example.ui.screens.WebBrowserView
import com.example.ui.screens.WebContextMenuGlassSheet
import com.example.ui.screens.WebPageGlassLoadingAnimation
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
    var isOpeningAnimationVisible by rememberSaveable { mutableStateOf(true) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var siteBarMeasuredHeightPx by remember { mutableIntStateOf(0) }
    val glassMotionTracker = remember { GlassMotionTracker() }
    val appScope = rememberCoroutineScope()

    // Silent startup Google Auto Sign-In check (single-option GetGoogleIdOption)
    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = CredentialManager.create(context),
            scope = appScope
        )
    }

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
        val useLightNavBarIcons = if (
            uiState.isViewingWebPage &&
            uiState.siteNavigation.variant == SiteNavBarVariant.YOUTUBE_SAFARI_ISLAND
        ) {
            false
        } else {
            !uiState.settings.isDarkMode
        }
        insetsController.isAppearanceLightStatusBars = useDarkStatusIcons
        insetsController.isAppearanceLightNavigationBars = useLightNavBarIcons
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
        uiState.activeLinkContextTarget != null ||
        uiState.isAccountSheetOpen ||
        uiState.isSiriOpen ||
        uiState.isSiteBookmarksSheetOpen ||
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
    val isLiveWebActive = activeTab != null && activeTab.url.isNotBlank()
    var retainedWebTab by remember { mutableStateOf<BrowserTabEntity?>(null) }
    if (isLiveWebActive && activeTab != null) {
        retainedWebTab = activeTab
    }
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

    val isSiteNavBarVisible = uiState.isViewingWebPage &&
        uiState.settings.replaceSiteNavBar &&
        !uiState.isTabSwitcherOpen &&
        !uiState.isAddressOverlayOpen &&
        !uiState.isSiriOpen &&
        !uiState.isFullscreenVideo

    val measuredSiteBarDp = if (siteBarMeasuredHeightPx > 0) {
        with(density) { siteBarMeasuredHeightPx.toDp() }
    } else {
        120.dp + navBottomInset
    }
    val targetBottomWebClearance = if (
        isSiteNavBarVisible && uiState.siteNavigation.docksBeneathSiteBottomBar
    ) {
        measuredSiteBarDp
    } else {
        0.dp
    }
    val animatedBottomWebClearance by animateDpAsState(
        targetValue = targetBottomWebClearance,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "bottomWebClearance"
    )

    GlassBrowserTheme(
        darkTheme = uiState.settings.isDarkMode
    ) {
        val style = LocalGlassStyle.current
        val activeDockShelfColor = when {
            uiState.siteNavigation.variant == SiteNavBarVariant.YOUTUBE_SAFARI_ISLAND -> Color(0xFF0B0B0D)
            uiState.siteNavigation.docksBeneathSiteBottomBar && uiState.settings.isDarkMode -> Color(0xFF0C0C0E)
            else -> style.glassBaseColor
        }

        CompositionLocalProvider(LocalSpatialBackdrop provides spatialBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.glassBaseColor)
                    .onSizeChanged { rootSize = it }
            ) {
                // =========================================================================
                // PLANE 1: Isolated Browser Content (WebView docked above Safari Island on YouTube; Full-Bleed elsewhere)
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
                    SitePortalTransitionContainer(
                        isViewingWebPage = isLiveWebActive,
                        modifier = Modifier.fillMaxSize(),
                        webContent = {
                            val targetTab = if (isLiveWebActive) activeTab else retainedWebTab
                            if (targetTab != null && targetTab.url.isNotBlank()) {
                                WebBrowserView(
                                    tabId = targetTab.id,
                                    url = targetTab.url,
                                    isPrivate = targetTab.isPrivate,
                                    forceGpuRendering = uiState.settings.forceGpuRendering,
                                    enableGpuRasterization = uiState.settings.enableGpuRasterization,
                                    disableHardwareOverlays = uiState.settings.disableHardwareOverlays,
                                    replaceSiteNavBar = uiState.settings.replaceSiteNavBar,
                                    bottomWebClearance = animatedBottomWebClearance,
                                    dockShelfColor = activeDockShelfColor,
                                    isTabSwitcherOpen = uiState.isTabSwitcherOpen,
                                    deviceProfile = uiState.deviceProfile,
                                    webCommand = webCommand,
                                    onConsumeCommand = viewModel::consumeWebCommand,
                                    onPageStarted = viewModel::onWebPageStarted,
                                    onProgressChanged = viewModel::onWebProgressChanged,
                                    onPageFinished = viewModel::onWebPageFinished,
                                    onVideoPlaybackChanged = viewModel::onWebVideoPlaybackChanged,
                                    onFullscreenVideoChanged = viewModel::onWebFullscreenVideoChanged,
                                    onSnapshotCaptured = viewModel::updateTabSnapshot,
                                    onLiveBackdropCaptured = viewModel::updateLiveWebBackdropOnly,
                                    onFindResultsUpdated = viewModel::onFindResultsUpdated,
                                    onSslSummaryUpdated = viewModel::updateSslCertificateSummary,
                                    onPageAiContextUpdated = viewModel::updatePageAiContext,
                                    onSiteNavigationJsonReceived = viewModel::onSiteNavigationJsonReceived,
                                    onDownloadRequested = viewModel::startWebOrDirectDownload,
                                    onSaveBlobDownload = viewModel::saveBlobBase64Download,
                                    onLinkContextTargetChanged = viewModel::setLinkContextTarget,
                                    onEdgeBounce = viewModel.feedback::onEdgeBounce,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        },
                        startPageContent = {
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
                                            painter = painterResource(id = R.drawable.wp_savanna_canopy),
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
                                    onOpenAccountSheet = { viewModel.setAccountSheetOpen(true) },
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
                    )
                }

                // =========================================================================
                // PLANE 2: Floating Spatial Frosted Glass Chrome, Motion Animations & Siri iOS 26
                // =========================================================================

                // Site Opening Liquid-Glass Portal Ripple Veil (non-blocking)
                if (uiState.isViewingWebPage && !uiState.isTabSwitcherOpen) {
                    SiteOpeningGlassVeil(
                        siteTransitionNonce = uiState.siteTransitionNonce,
                        siteBrand = uiState.siteNavigation.siteBrand.ifBlank {
                            uiState.siteNavigation.displayDomain
                        }
                    )
                }

                // Webpage Liquid-Glass Loading Animation (Top Shimmer Wave + Floating Orbital Prism Pill)
                WebPageGlassLoadingAnimation(
                    visible = uiState.isViewingWebPage &&
                        uiState.isLoadingWebPage &&
                        !uiState.isFullscreenVideo &&
                        !uiState.isTabSwitcherOpen &&
                        !uiState.isFindInPageOpen,
                    progress = uiState.webLoadProgress,
                    siteTitleOrDomain = uiState.siteNavigation.displayDomain.ifBlank {
                        uiState.siteNavigation.siteBrand
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

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

                // Custom Per-Site Bottom Navigation Bar (docks beneath YouTube's original bottom bar
                // and adapts into distinct per-site designs across Editorial, Developer, Social, and Universal sites)
                AnimatedVisibility(
                    visible = isSiteNavBarVisible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 340f)
                        ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 220)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { siteBarMeasuredHeightPx = it.height }
                ) {
                    SiteGlassNavigationBar(
                        navData = uiState.siteNavigation,
                        currentUrl = uiState.activeTab?.url.orEmpty(),
                        isLoading = uiState.isLoadingWebPage,
                        loadProgress = uiState.webLoadProgress,
                        canGoBack = uiState.canWebGoBack,
                        canGoForward = uiState.canWebGoForward,
                        isReaderMode = uiState.isReaderMode,
                        isDesktopMode = uiState.isDesktopMode,
                        isSiriActive = uiState.isSiriOpen || isSiriBusy,
                        onHomeClick = viewModel::onSiteHomeClicked,
                        onNavItemClick = viewModel::onSiteNavItemClicked,
                        onDomainClick = viewModel::openAddressOverlay,
                        onBackClick = viewModel::onBackButtonClicked,
                        onForwardClick = viewModel::goForwardWebPage,
                        onReloadOrStopClick = viewModel::stopOrReloadWebPage,
                        onShareClick = {
                            viewModel.sharePageUrl(
                                context = context,
                                title = uiState.activeTab?.title.orEmpty(),
                                url = uiState.activeTab?.url.orEmpty()
                            )
                        },
                        onBookmarksClick = { viewModel.setSiteBookmarksSheetOpen(true) },
                        onToggleReaderClick = { viewModel.toggleReaderMode(!uiState.isReaderMode) },
                        onOpenFindClick = viewModel::openFindInPage,
                        onToggleDesktopClick = { viewModel.toggleDesktopMode(!uiState.isDesktopMode) },
                        onSiriClick = viewModel::toggleSiriOverlay,
                        onWebToolsClick = { viewModel.setWebToolsSheetOpen(true) },
                        onTabsClick = viewModel::openTabSwitcher
                    )
                }

                // Floating Long-Press Link / Image Options Popover hovering directly ABOVE the navigation bar
                var cachedLinkTarget by remember { mutableStateOf(uiState.activeLinkContextTarget) }
                if (uiState.activeLinkContextTarget != null) {
                    cachedLinkTarget = uiState.activeLinkContextTarget
                }
                AnimatedVisibility(
                    visible = uiState.activeLinkContextTarget != null && !uiState.isTabSwitcherOpen,
                    enter = fadeIn(spring(stiffness = 440f)) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 380f)
                        ),
                    exit = fadeOut(spring(stiffness = 500f)) +
                        slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    cachedLinkTarget?.let { linkTarget ->
                        WebContextMenuGlassSheet(
                            target = linkTarget,
                            bottomClearance = if (isSiteNavBarVisible) {
                                measuredSiteBarDp + 8.dp
                            } else {
                                navBottomInset + 14.dp
                            },
                            onOpenInNewTab = viewModel::openUrlInNewTab,
                            onDownloadUrl = { targetUrl ->
                                viewModel.startWebOrDirectDownload(url = targetUrl)
                            },
                            onAskSiriAboutUrl = { targetUrl ->
                                viewModel.submitSiriPrompt(
                                    "Explain what this link or resource is in context of the page: $targetUrl"
                                )
                            },
                            onCopyUrl = { linkUrl ->
                                viewModel.copyUrlToClipboard(context, linkUrl)
                            },
                            onDismiss = { viewModel.setLinkContextTarget(null) }
                        )
                    }
                }

                // Minimal corner controls pill if the user manually turns off Custom Site Navigation Bar
                AnimatedVisibility(
                    visible = uiState.isViewingWebPage &&
                        !uiState.settings.replaceSiteNavBar &&
                        !uiState.isTabSwitcherOpen &&
                        !uiState.isSiriOpen &&
                        !uiState.isFullscreenVideo,
                    enter = fadeIn(spring(stiffness = 400f)) +
                        slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(50),
                        elevation = GlassElevation.FLOATING_BAR,
                        useLiveSceneBackdrop = true
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .minimumInteractiveComponentSize()
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Open Siri AI Assistant",
                                        onClick = viewModel::toggleSiriOverlay
                                    )
                            ) {
                                SiriLiquidOrbIcon(
                                    isThinking = uiState.isSiriOpen || isSiriBusy,
                                    size = 21.dp
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Open WebView tools and controls",
                                        onClick = { viewModel.setWebToolsSheetOpen(true) }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Open WebView tools and controls",
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Open tab switcher",
                                        onClick = viewModel::openTabSwitcher
                                    )
                            ) {
                                OverlappingTabsIcon(
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
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

                // Bottom Floating & Movable Browser Search Bar:
                // Removed when inside a website with a smooth downward slide-out animation,
                // and animates back up when returning to the Start Page.
                val isBottomBarVisible = !uiState.isViewingWebPage &&
                    !uiState.isTabSwitcherOpen &&
                    !uiState.isAddressOverlayOpen &&
                    !uiState.isSiriOpen

                AnimatedVisibility(
                    visible = isBottomBarVisible,
                    enter = fadeIn(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { fullHeight -> (fullHeight * 2.0f).toInt() },
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
                        ),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> (fullHeight * 2.2f).toInt() },
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 300, delayMillis = 80, easing = FastOutSlowInEasing)
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val canGoBack = uiState.activeSection != TopHubSection.START_PAGE
                    BottomBrowserGlassBar(
                        currentUrl = uiState.activeTab?.url.orEmpty(),
                        canNavigateBack = canGoBack,
                        isLoading = uiState.isLoadingWebPage,
                        loadProgress = uiState.webLoadProgress,
                        isPrivateMode = uiState.settings.isPrivateMode,
                        isSiriActive = uiState.isSiriOpen || isSiriBusy,
                        onBackClick = viewModel::onBackButtonClicked,
                        onAddressBarClick = viewModel::openAddressOverlay,
                        onWebToolsOrVoiceClick = viewModel::toggleSiriOverlay,
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

                // Omnibox URL / Search Frosted Overlay (slides downward off-screen when navigating into a site)
                AnimatedVisibility(
                    visible = uiState.isAddressOverlayOpen,
                    enter = fadeIn(spring(stiffness = 450f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f)
                        ),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 280, delayMillis = 60, easing = FastOutSlowInEasing)
                    )
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
                        replaceSiteNavBar = uiState.settings.replaceSiteNavBar,
                        textZoomPercent = uiState.webTextZoom,
                        activeDownloadsCount = uiState.activeDownloadsCount,
                        onGoForward = viewModel::goForwardWebPage,
                        onReloadOrStop = viewModel::stopOrReloadWebPage,
                        onToggleDesktopMode = viewModel::toggleDesktopMode,
                        onToggleReaderMode = viewModel::toggleReaderMode,
                        onToggleReplaceSiteNavBar = viewModel::setReplaceSiteNavBar,
                        onChangeTextZoom = viewModel::updateWebTextZoom,
                        onOpenFindInPage = viewModel::openFindInPage,
                        onOpenSiriAi = viewModel::toggleSiriOverlay,
                        onOpenDownloadsHub = viewModel::openDownloadsHub,
                        onReturnToStartPage = viewModel::returnToStartPage,
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

                // Quick Bookmarks & Reading Sheet opened from `📖` in YouTube / Per-Site Navigation Bars
                AnimatedVisibility(
                    visible = uiState.isSiteBookmarksSheetOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    SiteBookmarksQuickSheet(
                        bookmarks = uiState.bookmarks,
                        favorites = uiState.favorites,
                        onSaveBookmark = viewModel::saveCurrentPageToBookmarks,
                        onSaveToReadingList = viewModel::saveCurrentPageToReadingList,
                        onOpenUrl = viewModel::openUrlOrSearch,
                        onOpenAllBookmarksHub = viewModel::openBookmarksHub,
                        onDismiss = { viewModel.setSiteBookmarksSheetOpen(false) }
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
                        onToggleReplaceSiteNavBar = viewModel::setReplaceSiteNavBar,
                        onToggleHighRefresh = viewModel::setHighRefreshRateEnabled,
                        onToggleForceGpu = viewModel::setForceGpuRendering,
                        onToggleGpuRasterization = viewModel::setEnableGpuRasterization,
                        onToggleDisableHwOverlays = viewModel::setDisableHardwareOverlays,
                        onToggleSfx = viewModel::setSfxEnabled,
                        onToggleHaptics = viewModel::setHapticsEnabled,
                        onCloseAllTabs = viewModel::closeAllCurrentModeTabs,
                        onOpenAccountSheet = {
                            viewModel.setAccountSheetOpen(true)
                        },
                        onEdgeBounce = viewModel.feedback::onEdgeBounce,
                        onDismiss = { viewModel.setSettingsSheetOpen(false) }
                    )
                }

                // Google Account Login & Cloud Sync Frosted Sheet
                AnimatedVisibility(
                    visible = uiState.isAccountSheetOpen,
                    enter = fadeIn(spring(stiffness = 420f)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
                        ),
                    exit = fadeOut(spring(stiffness = 480f)) +
                        slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    AccountAuthGlassSheet(
                        signedInAccount = uiState.signedInAccount,
                        isSyncingCloud = uiState.isSyncingCloud,
                        cloudSyncStatus = uiState.cloudSyncStatus,
                        bookmarksCount = uiState.bookmarks.size,
                        favoritesCount = uiState.favorites.size,
                        readingListCount = uiState.readingList.size,
                        onAuthSucceeded = viewModel::syncAccountWithCloudNow,
                        onSyncNow = viewModel::syncAccountWithCloudNow,
                        onSignedOut = viewModel::onSignedOut,
                        onDismiss = { viewModel.setAccountSheetOpen(false) }
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

                // Cold-Start Liquid-Glass Browser Opening Animation
                BrowserOpeningGlassAnimation(
                    visible = isOpeningAnimationVisible,
                    onAnimationFinished = { isOpeningAnimationVisible = false }
                )
            }
        }
    }
}
