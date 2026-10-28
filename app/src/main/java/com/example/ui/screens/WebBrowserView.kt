package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.DeviceOptimizationManager
import com.example.core.GpuRenderController
import com.example.ui.BrowserViewModel
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
    onSnapshotCaptured: (tabId: Long, bitmap: Bitmap) -> Unit,
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rubberY = remember { Animatable(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Track both inline HTML5 <video> playback and WebChromeClient fullscreen video view
    var isInlineVideoPlaying by remember(tabId) { mutableStateOf(false) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val effectiveVideoPlaying = isInlineVideoPlaying || (customVideoView != null)

    val currentTabId by rememberUpdatedState(tabId)
    val currentPrivate by rememberUpdatedState(isPrivate)
    val currentGpuRaster by rememberUpdatedState(enableGpuRasterization)
    val currentDeviceProfile by rememberUpdatedState(deviceProfile)
    val onStartedLatest by rememberUpdatedState(onPageStarted)
    val onProgressLatest by rememberUpdatedState(onProgressChanged)
    val onFinishedLatest by rememberUpdatedState(onPageFinished)
    val onVideoPlaybackLatest by rememberUpdatedState(onVideoPlaybackChanged)
    val onSnapshotLatest by rememberUpdatedState(onSnapshotCaptured)
    val onBounceLatest by rememberUpdatedState(onEdgeBounce)

    // Notify ViewModel whenever inline or fullscreen video starts/stops so the bottom search bar slides down/up
    LaunchedEffect(currentTabId, effectiveVideoPlaying) {
        onVideoPlaybackLatest(currentTabId, effectiveVideoPlaying)
    }

    // Exit fullscreen video cleanly on system Back gesture without leaving the page
    BackHandler(enabled = customVideoView != null) {
        try {
            customVideoCallback?.onCustomViewHidden()
        } catch (_: Throwable) {}
        customVideoView = null
        customVideoCallback = null
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

    // Re-apply GPU rendering, GPU rasterization & hardware overlay settings live whenever toggled
    LaunchedEffect(webViewRef, forceGpuRendering, enableGpuRasterization, disableHardwareOverlays) {
        val wv = webViewRef ?: return@LaunchedEffect
        GpuRenderController.applyWebViewGpuPipeline(
            webView = wv,
            forceGpuRendering = forceGpuRendering,
            disableHardwareOverlays = disableHardwareOverlays,
            enableGpuRasterization = enableGpuRasterization
        )
        GpuRenderController.injectPageGpuRasterizationScript(
            webView = wv,
            enableGpuRasterization = enableGpuRasterization
        )
    }

    // Capture crisp tab & backdrop thumbnail once page settles
    LaunchedEffect(tabId, url) {
        delay(850L)
        webViewRef?.let { wv ->
            captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                onSnapshotLatest(tabId, bmp)
            }
        }
    }

    DisposableEffect(isPrivate) {
        onDispose {
            if (isPrivate) {
                try {
                    CookieManager.getInstance().removeSessionCookies(null)
                } catch (_: Throwable) {}
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
                    compositingStrategy = if (forceGpuRendering || disableHardwareOverlays || enableGpuRasterization) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                },
            factory = { ctx ->
                WebView(ctx).apply {
                    GpuRenderController.applyWebViewGpuPipeline(
                        webView = this,
                        forceGpuRendering = forceGpuRendering,
                        disableHardwareOverlays = disableHardwareOverlays,
                        enableGpuRasterization = enableGpuRasterization
                    )
                    isVerticalScrollBarEnabled = true
                    overScrollMode = View.OVER_SCROLL_NEVER

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = !isPrivate
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                    }

                    addJavascriptInterface(
                        WebVideoPlaybackBridge(Handler(Looper.getMainLooper())) { playing ->
                            isInlineVideoPlaying = playing
                        },
                        "GlassVideoBridge"
                    )

                    // Rubber-band spring touch handler at WebView top & bottom limits + snapshot refresh
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
                                val atTop = !wv.canScrollVertically(-1)
                                val atBottom = !wv.canScrollVertically(1)
                                val currentRubber = rubberY.value

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
                                // Refresh webpage snapshot for live frosted bottom bar & tab preview
                                scope.launch {
                                    delay(240L)
                                    captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                                        onSnapshotLatest(currentTabId, bmp)
                                    }
                                }
                            }
                        }
                        false
                    }

                    webViewClient = object : WebViewClient() {
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
                            captureWebViewThumbnail(wv, currentDeviceProfile)?.let { bmp ->
                                onSnapshotLatest(currentTabId, bmp)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onProgressLatest(newProgress)
                            if (view != null && (newProgress == 45 || newProgress == 80 || newProgress == 100)) {
                                injectVideoPlaybackObserver(view)
                            }
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
                GpuRenderController.applyWebViewGpuPipeline(
                    webView = wv,
                    forceGpuRendering = forceGpuRendering,
                    disableHardwareOverlays = disableHardwareOverlays,
                    enableGpuRasterization = enableGpuRasterization
                )
                if (url.isNotBlank() && wv.url != url && wv.originalUrl != url) {
                    isInlineVideoPlaying = false
                    wv.loadUrl(url)
                }
            }
        )

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
 * Installs a lightweight, zero-DOM-scan HTML5 video & fullscreen playback observer in the page.
 * Uses native `getElementsByTagName('video')` + Shadow DOM `event.composedPath()` capture events
 * to detect inline and fullscreen video playback immediately and notify [WebVideoPlaybackBridge].
 */
private fun injectVideoPlaybackObserver(webView: WebView) {
    val script = """
        (function() {
            try {
                if (window.__glassVideoHooked) {
                    if (window.__glassCheckVideos) window.__glassCheckVideos();
                    return;
                }
                window.__glassVideoHooked = true;
                var trackedVideos = new Set();
                var lastReported = null;

                function recordTarget(ev) {
                    try {
                        var path = ev && ev.composedPath ? ev.composedPath() : null;
                        var node = (path && path.length > 0) ? path[0] : (ev ? ev.target : null);
                        if (node && node.tagName === 'VIDEO') {
                            trackedVideos.add(node);
                        }
                    } catch (e) {}
                }

                function isVisiblePlayingVideo(v) {
                    if (!v || v.paused || v.ended || v.readyState < 2) return false;
                    var rect = v.getBoundingClientRect();
                    if (rect.width < 80 || rect.height < 60) return false;
                    var vw = window.innerWidth || document.documentElement.clientWidth || 360;
                    var vh = window.innerHeight || document.documentElement.clientHeight || 640;
                    return rect.bottom > 24 && rect.top < (vh - 24) && rect.right > 16 && rect.left < (vw - 16);
                }

                function checkVideos() {
                    try {
                        var fsEl = document.fullscreenElement || document.webkitFullscreenElement;
                        var playing = !!fsEl;

                        if (!playing) {
                            var domVideos = document.getElementsByTagName('video');
                            for (var i = 0; i < domVideos.length; i++) {
                                trackedVideos.add(domVideos[i]);
                            }
                            var frames = document.getElementsByTagName('iframe');
                            for (var f = 0; f < frames.length; f++) {
                                try {
                                    var fDoc = frames[f].contentDocument || (frames[f].contentWindow && frames[f].contentWindow.document);
                                    if (fDoc) {
                                        var fVids = fDoc.getElementsByTagName('video');
                                        for (var fv = 0; fv < fVids.length; fv++) {
                                            trackedVideos.add(fVids[fv]);
                                        }
                                    }
                                } catch (frameErr) {}
                            }

                            trackedVideos.forEach(function(v) {
                                if (!v.isConnected) {
                                    trackedVideos.delete(v);
                                } else if (isVisiblePlayingVideo(v)) {
                                    playing = true;
                                }
                            });
                        }

                        window.__glassVideoActivelyPlaying = playing;
                        if (document && document.body) {
                            document.body.style.paddingBottom = playing ? '0px' : '88px';
                        }

                        if (playing !== lastReported) {
                            lastReported = playing;
                            if (window.GlassVideoBridge && window.GlassVideoBridge.onVideoState) {
                                window.GlassVideoBridge.onVideoState(playing);
                            }
                        }
                    } catch (err) {}
                }

                window.__glassCheckVideos = checkVideos;

                var mediaEvents = [
                    'play', 'playing', 'pause', 'ended', 'emptied', 'abort',
                    'suspend', 'loadeddata', 'timeupdate',
                    'fullscreenchange', 'webkitfullscreenchange',
                    'webkitbeginfullscreen', 'webkitendfullscreen'
                ];
                for (var idx = 0; idx < mediaEvents.length; idx++) {
                    window.addEventListener(mediaEvents[idx], function(ev) {
                        recordTarget(ev);
                        checkVideos();
                    }, true);
                }
                window.addEventListener('scroll', checkVideos, { passive: true, capture: true });
                window.addEventListener('resize', checkVideos, { passive: true });
                setInterval(checkVideos, 650);
                checkVideos();
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
