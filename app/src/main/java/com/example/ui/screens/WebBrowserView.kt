package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.Keep
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.GoogleWebSessionManager
import com.example.core.GpuRenderController
import com.example.data.BrowserRepository
import com.example.ui.BrowserViewModel
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.LocalGlassStyle
import com.example.ui.theme.PureBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

@Keep
private class WebVideoPlaybackBridge(
    private val mainHandler: Handler,
    private val onPlaybackStateChanged: (Boolean) -> Unit
) {
    @Keep
    @JavascriptInterface
    fun onVideoState(playing: Boolean) {
        mainHandler.post {
            onPlaybackStateChanged(playing)
        }
    }
}

/**
 * Full-bleed live WebView surface (extending edge-to-edge to the bottom with no wallpaper behind or below),
 * supporting dynamic Force GPU Rendering, Enable GPU Rasterization, Disable Hardware Overlays,
 * inline + fullscreen HTML5 video playback detection, lifecycle-aware CPU/GPU power management,
 * device-tuned snapshot capture, and elastic rubber-band overscroll.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebBrowserView(
    tabId: Long,
    url: String,
    isPrivate: Boolean,
    forceGpuRendering: Boolean,
    enableGpuRasterization: Boolean,
    disableHardwareOverlays: Boolean,
    disableGpuVsync: Boolean = true,
    disableFrameRateLimit: Boolean = true,
    isTabSwitcherOpen: Boolean = false,
    deviceProfile: DeviceOptimizationManager.DeviceProfile,
    webCommand: BrowserViewModel.WebCommand?,
    onConsumeCommand: () -> Unit,
    onPageStarted: (tabId: Long, url: String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onPageFinished: (
        tabId: Long,
        url: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isPrivate: Boolean
    ) -> Unit,
    onVideoPlaybackChanged: (tabId: Long, isPlaying: Boolean) -> Unit,
    onGoogleSessionSync: (isActive: Boolean, email: String, name: String, avatarUrl: String) -> Unit,
    onSnapshotCaptured: (tabId: Long, bitmap: Bitmap) -> Unit,
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rubberY = remember { Animatable(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Track inline HTML5 <video>, WebChromeClient fullscreen video, and OAuth/federated popup windows
    var isInlineVideoPlaying by remember(tabId) { mutableStateOf(false) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var oauthPopupWebView by remember { mutableStateOf<WebView?>(null) }
    var oauthPopupUrl by remember { mutableStateOf("") }
    val effectiveVideoPlaying = isInlineVideoPlaying || (customVideoView != null)

    val currentTabId by rememberUpdatedState(tabId)
    val currentPrivate by rememberUpdatedState(isPrivate)
    val currentGpuRaster by rememberUpdatedState(enableGpuRasterization)
    val currentDeviceProfile by rememberUpdatedState(deviceProfile)
    val onStartedLatest by rememberUpdatedState(onPageStarted)
    val onProgressLatest by rememberUpdatedState(onProgressChanged)
    val onFinishedLatest by rememberUpdatedState(onPageFinished)
    val onVideoPlaybackLatest by rememberUpdatedState(onVideoPlaybackChanged)
    val onGoogleSyncLatest by rememberUpdatedState(onGoogleSessionSync)
    val onSnapshotLatest by rememberUpdatedState(onSnapshotCaptured)
    val onBounceLatest by rememberUpdatedState(onEdgeBounce)

    // Notify ViewModel whenever inline or fullscreen video starts/stops so the bottom search bar slides down/up
    LaunchedEffect(currentTabId, effectiveVideoPlaying) {
        onVideoPlaybackLatest(currentTabId, effectiveVideoPlaying)
    }

    // Handle Back for fullscreen video or OAuth popup window cleanly without leaving the page
    BackHandler(enabled = customVideoView != null || oauthPopupWebView != null) {
        val popup = oauthPopupWebView
        when {
            customVideoView != null -> {
                try {
                    customVideoCallback?.onCustomViewHidden()
                } catch (_: Throwable) {}
                customVideoView = null
                customVideoCallback = null
            }
            popup != null -> {
                if (popup.canGoBack()) {
                    popup.goBack()
                } else {
                    popup.destroy()
                    oauthPopupWebView = null
                }
            }
        }
    }

    // Lifecycle-aware pause/resume so inactive/backgrounded sessions conserve CPU & battery on all devices
    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    wv.onResume()
                    wv.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isInlineVideoPlaying = false
                    wv.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onVideoPlaybackLatest(currentTabId, false)
        }
    }

    // Execute explicit navigation / reload / back / forward commands from ViewModel
    LaunchedEffect(webCommand, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val cmd = webCommand ?: return@LaunchedEffect
        when (cmd) {
            BrowserViewModel.WebCommand.GoBack -> if (wv.canGoBack()) wv.goBack()
            BrowserViewModel.WebCommand.GoForward -> if (wv.canGoForward()) wv.goForward()
            BrowserViewModel.WebCommand.Reload -> {
                isInlineVideoPlaying = false
                wv.reload()
            }
            is BrowserViewModel.WebCommand.LoadUrl -> {
                if (wv.url != cmd.url) {
                    isInlineVideoPlaying = false
                    wv.loadUrl(cmd.url)
                }
            }
        }
        onConsumeCommand()
    }

    // Re-apply GPU rendering, GPU rasterization, hardware overlay & VSync/frame-rate settings live whenever toggled
    LaunchedEffect(
        webViewRef,
        forceGpuRendering,
        enableGpuRasterization,
        disableHardwareOverlays,
        disableGpuVsync,
        disableFrameRateLimit
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        GpuRenderController.applyWebViewGpuPipeline(
            webView = wv,
            forceGpuRendering = forceGpuRendering,
            disableHardwareOverlays = disableHardwareOverlays,
            enableGpuRasterization = enableGpuRasterization,
            disableGpuVsync = disableGpuVsync,
            disableFrameRateLimit = disableFrameRateLimit
        )
        GpuRenderController.injectPageGpuRasterizationScript(
            webView = wv,
            enableGpuRasterization = enableGpuRasterization
        )
    }

    // Capture crisp tab thumbnail once page settles or when entering Tab Switcher (never during scroll flings)
    LaunchedEffect(tabId, url) {
        delay(850L)
        webViewRef?.let { wv ->
            captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                onSnapshotLatest(tabId, bmp)
            }
        }
    }

    LaunchedEffect(isTabSwitcherOpen) {
        if (isTabSwitcherOpen) {
            webViewRef?.let { wv ->
                captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                    onSnapshotLatest(currentTabId, bmp)
                }
            }
        }
    }

    DisposableEffect(isPrivate) {
        onDispose {
            oauthPopupWebView?.destroy()
            oauthPopupWebView = null
            if (isPrivate) {
                try {
                    CookieManager.getInstance().removeSessionCookies(null)
                } catch (_: Throwable) {}
            } else {
                GoogleWebSessionManager.flushCookies()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(style.glassBaseColor)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("active_webview")
                .graphicsLayer {
                    translationY = rubberY.value
                },
            factory = { ctx ->
                WebView(ctx).apply {
                    GpuRenderController.applyWebViewGpuPipeline(
                        webView = this,
                        forceGpuRendering = forceGpuRendering,
                        disableHardwareOverlays = disableHardwareOverlays,
                        enableGpuRasterization = enableGpuRasterization,
                        disableGpuVsync = disableGpuVsync,
                        disableFrameRateLimit = disableFrameRateLimit
                    )
                    isVerticalScrollBarEnabled = true
                    overScrollMode = View.OVER_SCROLL_NEVER

                    GoogleWebSessionManager.configureSharedSessionWebView(
                        context = ctx,
                        webView = this,
                        isPrivate = isPrivate
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = !isPrivate
                        databaseEnabled = !isPrivate
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        standardFontFamily = "SF Pro Text"
                        sansSerifFontFamily = "SF Pro Text"
                    }

                    addJavascriptInterface(
                        WebVideoPlaybackBridge(Handler(Looper.getMainLooper())) { playing ->
                            isInlineVideoPlaying = playing
                        },
                        "GlassVideoBridge"
                    )

                    // Zero-jank rubber-band spring touch handler at WebView top & bottom limits only
                    var lastTouchY = 0f
                    var isDraggingEdge = false
                    setOnTouchListener { view, event ->
                        val wv = view as WebView
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastTouchY = event.rawY
                                isDraggingEdge = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dy = event.rawY - lastTouchY
                                lastTouchY = event.rawY
                                val currentRubber = rubberY.value
                                if (!isDraggingEdge && currentRubber == 0f) {
                                    // Fast-path during normal in-page scrolling: let Chromium's compositor
                                    // scroll immediately without edge checks or coroutine allocations.
                                    if ((dy < 0f && wv.canScrollVertically(1)) ||
                                        (dy > 0f && wv.canScrollVertically(-1))
                                    ) {
                                        return@setOnTouchListener false
                                    }
                                }
                                val atTop = !wv.canScrollVertically(-1)
                                val atBottom = !wv.canScrollVertically(1)

                                if ((atTop && dy > 0f) || (atBottom && dy < 0f) || abs(currentRubber) > 1f) {
                                    isDraggingEdge = true
                                    val maxPull = 320f
                                    val norm = (abs(currentRubber) / maxPull).coerceIn(0f, 2f)
                                    val resistance = 0.52f / (1f + norm).pow(1.35f)
                                    val next = (currentRubber + dy * resistance).coerceIn(-maxPull, maxPull)
                                    scope.launch { rubberY.snapTo(next) }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (isDraggingEdge || abs(rubberY.value) > 0.5f) {
                                    if (abs(rubberY.value) > 42f) {
                                        onBounceLatest(rubberY.value * 9f)
                                    }
                                    scope.launch {
                                        rubberY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.60f,
                                                stiffness = 290f
                                            )
                                        )
                                    }
                                    isDraggingEdge = false
                                }
                            }
                        }
                        false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url
                            if (reqUrl != null && reqUrl.host == "glassbrowser.font.local") {
                                val fontResId = when (reqUrl.path) {
                                    "/sf-pro-300.ttf" -> R.font.inter_300
                                    "/sf-pro-400.ttf" -> R.font.inter_400
                                    "/sf-pro-500.ttf" -> R.font.inter_500
                                    "/sf-pro-600.ttf" -> R.font.inter_600
                                    "/sf-pro-700.ttf" -> R.font.inter_700
                                    "/sf-pro-800.ttf" -> R.font.inter_800
                                    "/sf-pro-900.ttf" -> R.font.inter_900
                                    else -> R.font.inter_400
                                }
                                return try {
                                    WebResourceResponse(
                                        "font/ttf",
                                        null,
                                        200,
                                        "OK",
                                        mapOf(
                                            "Access-Control-Allow-Origin" to "*",
                                            "Cache-Control" to "public, max-age=31536000, immutable"
                                        ),
                                        ctx.resources.openRawResource(fontResId)
                                    )
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, startedUrl, favicon)
                            isInlineVideoPlaying = false
                            if (!startedUrl.isNullOrBlank()) {
                                onStartedLatest(currentTabId, startedUrl)
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            val wv = view ?: return
                            val finalUrl = finishedUrl ?: wv.url.orEmpty()
                            GpuRenderController.injectPageGpuRasterizationScript(
                                webView = wv,
                                enableGpuRasterization = currentGpuRaster
                            )
                            injectVideoPlaybackObserver(wv)
                            onFinishedLatest(
                                currentTabId,
                                finalUrl,
                                wv.title,
                                wv.canGoBack(),
                                wv.canGoForward(),
                                currentPrivate
                            )
                            if (!currentPrivate) {
                                GoogleWebSessionManager.flushCookies()
                                if (GoogleWebSessionManager.isGoogleDomain(finalUrl)) {
                                    val active = GoogleWebSessionManager.isGoogleSessionActive()
                                    if (active) {
                                        GoogleWebSessionManager.extractProfileFromWebView(wv) { profile ->
                                            onGoogleSyncLatest(true, profile.email, profile.name, profile.avatarUrl)
                                        }
                                    } else {
                                        onGoogleSyncLatest(false, "", "", "")
                                    }
                                }
                            }
                            captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                                onSnapshotLatest(currentTabId, bmp)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onProgressLatest(newProgress)
                            if (view != null && (newProgress == 35 || newProgress == 65 || newProgress == 100)) {
                                GpuRenderController.injectPageSfProTypography(view)
                                injectVideoPlaybackObserver(view)
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            val child = WebView(ctx).apply {
                                GoogleWebSessionManager.configureSharedSessionWebView(
                                    context = ctx,
                                    webView = this,
                                    isPrivate = currentPrivate
                                )
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        req: WebResourceRequest?
                                    ): Boolean {
                                        val s = req?.url?.scheme?.lowercase()
                                        return s != null && s != "http" && s != "https"
                                    }

                                    override fun onPageStarted(
                                        v: WebView?,
                                        startedUrl: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(v, startedUrl, favicon)
                                        oauthPopupUrl = startedUrl.orEmpty()
                                    }

                                    override fun onPageFinished(v: WebView?, finishedUrl: String?) {
                                        super.onPageFinished(v, finishedUrl)
                                        oauthPopupUrl = finishedUrl ?: v?.url.orEmpty()
                                        if (!currentPrivate) {
                                            GoogleWebSessionManager.flushCookies()
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        if (!currentPrivate) {
                                            GoogleWebSessionManager.flushCookies()
                                        }
                                        window?.destroy()
                                        if (oauthPopupWebView === window) {
                                            oauthPopupWebView = null
                                        }
                                    }
                                }
                            }
                            oauthPopupWebView?.destroy()
                            oauthPopupWebView = child
                            transport.webView = child
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            if (customVideoView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            customVideoView = view
                            customVideoCallback = callback
                        }

                        override fun onHideCustomView() {
                            try {
                                customVideoCallback?.onCustomViewHidden()
                            } catch (_: Throwable) {}
                            customVideoView = null
                            customVideoCallback = null
                        }
                    }

                    webViewRef = this
                    if (url.isNotBlank()) {
                        loadUrl(url)
                    }
                }
            },
            update = { wv ->
                webViewRef = wv
                try {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !isPrivate)
                } catch (_: Throwable) {}
                GpuRenderController.applyWebViewGpuPipeline(
                    webView = wv,
                    forceGpuRendering = forceGpuRendering,
                    disableHardwareOverlays = disableHardwareOverlays,
                    enableGpuRasterization = enableGpuRasterization,
                    disableGpuVsync = disableGpuVsync,
                    disableFrameRateLimit = disableFrameRateLimit
                )
                if (url.isNotBlank() && wv.url != url && wv.originalUrl != url) {
                    isInlineVideoPlaying = false
                    wv.loadUrl(url)
                }
            }
        )

        // OAuth / Federated Sign-In Popup Window Surface (e.g. "Continue with Google" on websites)
        val activeOAuthPopup = oauthPopupWebView
        if (activeOAuthPopup != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.glassBaseColor)
                    .statusBarsPadding()
                    .testTag("oauth_popup_container")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = style.contentSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = BrowserRepository.extractDomain(oauthPopupUrl).ifBlank { "Sign In" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = style.contentPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(style.activeSegmentFill)
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = "Close sign-in popup",
                                onClick = {
                                    activeOAuthPopup.destroy()
                                    oauthPopupWebView = null
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close sign-in popup",
                            tint = style.contentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> FrameLayout(c) },
                    update = { container ->
                        if (activeOAuthPopup.parent !== container) {
                            (activeOAuthPopup.parent as? ViewGroup)?.removeView(activeOAuthPopup)
                            container.removeAllViews()
                            container.addView(
                                activeOAuthPopup,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }
        }

        // Fullscreen HTML5 Video surface when a webpage player enters fullscreen
        val activeFullscreenVideo = customVideoView
        if (activeFullscreenVideo != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureBlack)
                    .testTag("fullscreen_video_container"),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { container ->
                    if (activeFullscreenVideo.parent !== container) {
                        (activeFullscreenVideo.parent as? ViewGroup)?.removeView(activeFullscreenVideo)
                        container.removeAllViews()
                        container.addView(
                            activeFullscreenVideo,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                }
            )
        }
    }
}

/**
 * Installs a passive, VSync-bound (`requestAnimationFrame`) HTML5 video & fullscreen observer.
 * - Uses `{ passive: true, capture: true }` on scroll, wheel, touch, and media events so the
 *   browser compositor scrolls immediately without waiting on JS handlers.
 * - Fast-exits scroll/wheel in O(1) whenever no video is actively playing.
 * - Eliminates `setInterval` timers and `timeupdate` flooding in favor of `requestAnimationFrame`.
 */
private fun injectVideoPlaybackObserver(webView: WebView) {
    val script = """
        (function() {
            try {
                if (window.__glassVideoHooked) {
                    if (window.__glassScheduleVideoCheck) window.__glassScheduleVideoCheck(true);
                    return;
                }
                window.__glassVideoHooked = true;
                var trackedVideos = new Set();
                var playingVideos = new Set();
                var lastReported = null;
                var lastPadding = null;
                var rafPending = 0;

                function extractTargetVideo(ev) {
                    try {
                        var path = ev && ev.composedPath ? ev.composedPath() : null;
                        var node = (path && path.length > 0) ? path[0] : (ev ? ev.target : null);
                        if (node && node.tagName === 'VIDEO') {
                            trackedVideos.add(node);
                            return node;
                        }
                    } catch (e) {}
                    return null;
                }

                function isVisiblePlayingVideo(v) {
                    if (!v || v.paused || v.ended || v.readyState < 2) return false;
                    var rect = v.getBoundingClientRect();
                    if (rect.width < 80 || rect.height < 60) return false;
                    var vw = window.innerWidth || document.documentElement.clientWidth || 360;
                    var vh = window.innerHeight || document.documentElement.clientHeight || 640;
                    return rect.bottom > 24 && rect.top < (vh - 24) && rect.right > 16 && rect.left < (vw - 16);
                }

                function runCheck(scanDom) {
                    rafPending = 0;
                    try {
                        var fsEl = document.fullscreenElement || document.webkitFullscreenElement;
                        var playing = !!fsEl;

                        if (!playing) {
                            if (scanDom) {
                                var domVideos = document.getElementsByTagName('video');
                                for (var i = 0; i < domVideos.length; i++) {
                                    var dv = domVideos[i];
                                    trackedVideos.add(dv);
                                    if (!dv.paused && !dv.ended) playingVideos.add(dv);
                                }
                            }

                            trackedVideos.forEach(function(v) {
                                if (!v.isConnected) {
                                    trackedVideos.delete(v);
                                    playingVideos.delete(v);
                                } else if (!v.paused && !v.ended) {
                                    playingVideos.add(v);
                                    if (isVisiblePlayingVideo(v)) {
                                        playing = true;
                                    }
                                } else {
                                    playingVideos.delete(v);
                                }
                            });
                        }

                        window.__glassVideoActivelyPlaying = playing;
                        var targetPadding = playing ? '0px' : '88px';
                        if (document && document.body && lastPadding !== targetPadding) {
                            lastPadding = targetPadding;
                            document.body.style.paddingBottom = targetPadding;
                        }

                        if (playing !== lastReported) {
                            lastReported = playing;
                            if (window.GlassVideoBridge && window.GlassVideoBridge.onVideoState) {
                                window.GlassVideoBridge.onVideoState(playing);
                            }
                        }
                    } catch (err) {}
                }

                var pendingDomScan = false;
                function scheduleCheck(scanDom) {
                    if (scanDom) pendingDomScan = true;
                    if (rafPending) return;
                    var raf = window.requestAnimationFrame;
                    if (raf) {
                        rafPending = raf(function() {
                            var doScan = pendingDomScan;
                            pendingDomScan = false;
                            runCheck(doScan);
                        });
                    } else {
                        var doScanNow = pendingDomScan;
                        pendingDomScan = false;
                        runCheck(doScanNow);
                    }
                }

                window.__glassScheduleVideoCheck = scheduleCheck;

                var passiveCapture = { passive: true, capture: true };
                var playEvents = ['play', 'playing', 'loadeddata'];
                for (var p = 0; p < playEvents.length; p++) {
                    window.addEventListener(playEvents[p], function(ev) {
                        var v = extractTargetVideo(ev);
                        if (v && !v.paused && !v.ended) playingVideos.add(v);
                        scheduleCheck(v === null);
                    }, passiveCapture);
                }

                var stopEvents = ['pause', 'ended', 'emptied', 'abort', 'suspend'];
                for (var s = 0; s < stopEvents.length; s++) {
                    window.addEventListener(stopEvents[s], function(ev) {
                        var v = extractTargetVideo(ev);
                        if (v) playingVideos.delete(v);
                        scheduleCheck(false);
                    }, passiveCapture);
                }

                var fsEvents = [
                    'fullscreenchange', 'webkitfullscreenchange',
                    'webkitbeginfullscreen', 'webkitendfullscreen'
                ];
                for (var f = 0; f < fsEvents.length; f++) {
                    window.addEventListener(fsEvents[f], function(ev) {
                        extractTargetVideo(ev);
                        scheduleCheck(true);
                    }, passiveCapture);
                }

                function onPassiveScrollOrWheel() {
                    if (playingVideos.size === 0 &&
                        !document.fullscreenElement &&
                        !document.webkitFullscreenElement) {
                        return;
                    }
                    scheduleCheck(false);
                }

                window.addEventListener('scroll', onPassiveScrollOrWheel, passiveCapture);
                window.addEventListener('wheel', onPassiveScrollOrWheel, passiveCapture);
                window.addEventListener('touchstart', onPassiveScrollOrWheel, passiveCapture);
                window.addEventListener('touchmove', onPassiveScrollOrWheel, passiveCapture);
                window.addEventListener('resize', function() {
                    if (playingVideos.size > 0 || document.fullscreenElement || document.webkitFullscreenElement) {
                        scheduleCheck(false);
                    }
                }, { passive: true });

                scheduleCheck(true);
            } catch (outerErr) {}
        })();
    """.trimIndent()
    try {
        webView.evaluateJavascript(script, null)
    } catch (_: Throwable) {}
}

private fun captureWebViewThumbnail(
    webView: WebView,
    deviceProfile: DeviceOptimizationManager.DeviceProfile
): Bitmap? {
    return try {
        val w = webView.width
        val h = webView.height
        if (w <= 16 || h <= 16) return null
        val scale = deviceProfile.snapshotScale
        val bmpW = (w * scale).toInt().coerceAtLeast(64)
        val bmpH = (h * scale).toInt().coerceAtLeast(84)
        val config = if (deviceProfile.preferRgb565Snapshots) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        val bmp = Bitmap.createBitmap(bmpW, bmpH, config)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        webView.draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}
