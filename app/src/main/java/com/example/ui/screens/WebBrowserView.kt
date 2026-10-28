package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.example.core.GpuRenderController
import com.example.ui.BrowserViewModel
import com.example.ui.theme.LocalGlassStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * Full-bleed live WebView surface (extending edge-to-edge to the bottom with no wallpaper behind or below),
 * supporting dynamic Force GPU Rendering, Disable Hardware Overlays, elastic rubber-band overscroll,
 * and automatic webpage snapshot capture.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebBrowserView(
    tabId: Long,
    url: String,
    isPrivate: Boolean,
    forceGpuRendering: Boolean,
    disableHardwareOverlays: Boolean,
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
    onSnapshotCaptured: (tabId: Long, bitmap: Bitmap) -> Unit,
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val scope = rememberCoroutineScope()
    val rubberY = remember { Animatable(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val currentTabId by rememberUpdatedState(tabId)
    val currentPrivate by rememberUpdatedState(isPrivate)
    val onStartedLatest by rememberUpdatedState(onPageStarted)
    val onProgressLatest by rememberUpdatedState(onProgressChanged)
    val onFinishedLatest by rememberUpdatedState(onPageFinished)
    val onSnapshotLatest by rememberUpdatedState(onSnapshotCaptured)
    val onBounceLatest by rememberUpdatedState(onEdgeBounce)

    // Execute explicit navigation / reload / back / forward commands from ViewModel
    LaunchedEffect(webCommand, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val cmd = webCommand ?: return@LaunchedEffect
        when (cmd) {
            BrowserViewModel.WebCommand.GoBack -> if (wv.canGoBack()) wv.goBack()
            BrowserViewModel.WebCommand.GoForward -> if (wv.canGoForward()) wv.goForward()
            BrowserViewModel.WebCommand.Reload -> wv.reload()
            is BrowserViewModel.WebCommand.LoadUrl -> {
                if (wv.url != cmd.url) {
                    wv.loadUrl(cmd.url)
                }
            }
        }
        onConsumeCommand()
    }

    // Re-apply GPU rendering & hardware overlay settings whenever toggled in Settings
    LaunchedEffect(webViewRef, forceGpuRendering, disableHardwareOverlays) {
        val wv = webViewRef ?: return@LaunchedEffect
        GpuRenderController.applyWebViewGpuPipeline(
            webView = wv,
            forceGpuRendering = forceGpuRendering,
            disableHardwareOverlays = disableHardwareOverlays
        )
    }

    // Capture crisp tab & backdrop thumbnail once page settles
    LaunchedEffect(tabId, url) {
        delay(850L)
        webViewRef?.let { wv ->
            captureWebViewThumbnail(wv)?.let { bmp ->
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
                    compositingStrategy = if (forceGpuRendering || disableHardwareOverlays) {
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
                        disableHardwareOverlays = disableHardwareOverlays
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
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                    }

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
                                    captureWebViewThumbnail(wv)?.let { bmp ->
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
                            if (!startedUrl.isNullOrBlank()) {
                                onStartedLatest(currentTabId, startedUrl)
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            val wv = view ?: return
                            val finalUrl = finishedUrl ?: wv.url.orEmpty()
                            // Ensure bottom webpage content can scroll comfortably above the floating glass bar
                            wv.evaluateJavascript(
                                "(function(){if(document&&document.body){document.body.style.paddingBottom='88px';}})();",
                                null
                            )
                            onFinishedLatest(
                                currentTabId,
                                finalUrl,
                                wv.title,
                                wv.canGoBack(),
                                wv.canGoForward(),
                                currentPrivate
                            )
                            captureWebViewThumbnail(wv)?.let { bmp ->
                                onSnapshotLatest(currentTabId, bmp)
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onProgressLatest(newProgress)
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
                    disableHardwareOverlays = disableHardwareOverlays
                )
                if (url.isNotBlank() && wv.url != url && wv.originalUrl != url) {
                    wv.loadUrl(url)
                }
            }
        )
    }
}

private fun captureWebViewThumbnail(webView: WebView): Bitmap? {
    return try {
        val w = webView.width
        val h = webView.height
        if (w <= 16 || h <= 16) return null
        val scale = 0.35f
        val bmpW = (w * scale).toInt().coerceAtLeast(64)
        val bmpH = (h * scale).toInt().coerceAtLeast(84)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        webView.draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}
