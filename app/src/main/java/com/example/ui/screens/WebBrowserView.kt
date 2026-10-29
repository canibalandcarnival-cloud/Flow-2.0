package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.R
import com.example.core.DESKTOP_CHROME_USER_AGENT
import com.example.core.DeviceOptimizationManager
import com.example.core.GpuRenderController
import com.example.core.WebBrowserJsBridge
import com.example.core.WebPageAiContext
import com.example.core.WebViewBridgeScripts
import com.example.ui.BrowserViewModel
import com.example.ui.components.LocalSpatialBackdrop
import com.example.ui.theme.LocalGlassStyle
import com.example.ui.theme.PureBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * Full-bleed live WebView surface with complete browser components:
 * - Navigation commands (Back, Forward, Stop/Reload, LoadUrl, Find-in-Page, Desktop Site, Reader Mode, Text Zoom)
 * - Real-time frosted-glass scroll delta & backdrop sampling (`LocalSpatialBackdrop.current.motionTracker`)
 * - `DownloadListener` (HTTP/HTTPS + `data:` + `blob:` base64 bridge)
 * - Zero-permission `onShowFileChooser` upload picker
 * - Frosted-glass JS `alert`/`confirm`/`prompt` modal dialogs & long-press `HitTestResult` link/image context sheet
 * - Live DOM + selection extraction for Siri iOS 26 Intelligence
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
    onSnapshotCaptured: (tabId: Long, bitmap: Bitmap) -> Unit,
    onLiveBackdropCaptured: (bitmap: Bitmap) -> Unit = {},
    onFindResultsUpdated: (activeOrdinal: Int, numberOfMatches: Int) -> Unit = { _, _ -> },
    onSslSummaryUpdated: (String) -> Unit = {},
    onPageAiContextUpdated: (WebPageAiContext) -> Unit = {},
    onDownloadRequested: (
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) -> Unit = { _, _, _, _, _ -> },
    onSaveBlobDownload: (dataUrl: String, fileName: String?, mimeType: String?) -> Unit = { _, _, _ -> },
    onOpenUrlInNewTab: (String) -> Unit = {},
    onAskSiriAboutUrl: (String) -> Unit = {},
    onCopyUrl: (String) -> Unit = {},
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val backdrop = LocalSpatialBackdrop.current
    val motionTracker = backdrop.motionTracker
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rubberY = remember { Animatable(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var defaultMobileUserAgent by remember { mutableStateOf("") }

    // Scroll tracking anchors so floating frosted glass moves synchronously with webpage scroll
    var lastBackdropScrollY by remember(tabId) { mutableIntStateOf(0) }
    var lastBackdropCaptureMs by remember(tabId) { mutableLongStateOf(0L) }

    // Track inline HTML5 <video> and WebChromeClient fullscreen video
    var isInlineVideoPlaying by remember(tabId) { mutableStateOf(false) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val effectiveVideoPlaying = isInlineVideoPlaying || (customVideoView != null)

    // Enhanced WebView UI states: File chooser, JS Dialogs, Long-press context menu
    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var hitContextTarget by remember(tabId) { mutableStateOf<WebHitContextTarget?>(null) }
    var activeJsDialog by remember(tabId) { mutableStateOf<WebJsDialogRequest?>(null) }
    var pendingJsCancel by remember { mutableStateOf<(() -> Unit)?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = pendingFileChooserCallback
        pendingFileChooserCallback = null
        callback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
    }

    val currentTabId by rememberUpdatedState(tabId)
    val currentPrivate by rememberUpdatedState(isPrivate)
    val currentGpuRaster by rememberUpdatedState(enableGpuRasterization)
    val currentDeviceProfile by rememberUpdatedState(deviceProfile)
    val onStartedLatest by rememberUpdatedState(onPageStarted)
    val onProgressLatest by rememberUpdatedState(onProgressChanged)
    val onFinishedLatest by rememberUpdatedState(onPageFinished)
    val onVideoPlaybackLatest by rememberUpdatedState(onVideoPlaybackChanged)
    val onSnapshotLatest by rememberUpdatedState(onSnapshotCaptured)
    val onLiveBackdropLatest by rememberUpdatedState(onLiveBackdropCaptured)
    val onFindResultsLatest by rememberUpdatedState(onFindResultsUpdated)
    val onSslSummaryLatest by rememberUpdatedState(onSslSummaryUpdated)
    val onPageAiContextLatest by rememberUpdatedState(onPageAiContextUpdated)
    val onDownloadLatest by rememberUpdatedState(onDownloadRequested)
    val onBlobDownloadLatest by rememberUpdatedState(onSaveBlobDownload)
    val onBounceLatest by rememberUpdatedState(onEdgeBounce)

    LaunchedEffect(currentTabId, effectiveVideoPlaying) {
        onVideoPlaybackLatest(currentTabId, effectiveVideoPlaying)
    }

    BackHandler(enabled = customVideoView != null || hitContextTarget != null || activeJsDialog != null) {
        when {
            activeJsDialog != null -> {
                pendingJsCancel?.invoke()
                pendingJsCancel = null
                activeJsDialog = null
            }
            hitContextTarget != null -> {
                hitContextTarget = null
            }
            customVideoView != null -> {
                try {
                    customVideoCallback?.onCustomViewHidden()
                } catch (_: Throwable) {}
                customVideoView = null
                customVideoCallback = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileChooserCallback?.onReceiveValue(null)
            pendingFileChooserCallback = null
            pendingJsCancel?.invoke()
            pendingJsCancel = null
        }
    }

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

    // Execute explicit navigation & WebView component commands from BrowserViewModel
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
            BrowserViewModel.WebCommand.StopLoading -> {
                wv.stopLoading()
            }
            is BrowserViewModel.WebCommand.LoadUrl -> {
                if (wv.url != cmd.url) {
                    isInlineVideoPlaying = false
                    wv.loadUrl(cmd.url)
                }
            }
            is BrowserViewModel.WebCommand.FindInPage -> {
                if (cmd.query.isBlank()) {
                    wv.clearMatches()
                    onFindResultsLatest(0, 0)
                } else {
                    wv.findAllAsync(cmd.query)
                }
            }
            is BrowserViewModel.WebCommand.FindNext -> {
                wv.findNext(cmd.forward)
            }
            BrowserViewModel.WebCommand.ClearFind -> {
                wv.clearMatches()
                onFindResultsLatest(0, 0)
            }
            is BrowserViewModel.WebCommand.SetDesktopMode -> {
                if (defaultMobileUserAgent.isBlank()) {
                    defaultMobileUserAgent = wv.settings.userAgentString.orEmpty()
                }
                wv.settings.userAgentString = if (cmd.enabled) {
                    DESKTOP_CHROME_USER_AGENT
                } else {
                    defaultMobileUserAgent.ifBlank { null }
                }
                wv.settings.useWideViewPort = true
                wv.settings.loadWithOverviewMode = true
                wv.reload()
            }
            is BrowserViewModel.WebCommand.SetReaderMode -> {
                GpuRenderController.toggleReaderModeOnPage(wv, cmd.enabled)
            }
            is BrowserViewModel.WebCommand.SetTextZoom -> {
                wv.settings.textZoom = cmd.zoomPercent
            }
        }
        onConsumeCommand()
    }

    LaunchedEffect(
        webViewRef,
        forceGpuRendering,
        enableGpuRasterization,
        disableHardwareOverlays
    ) {
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

    LaunchedEffect(tabId, url) {
        delay(850L)
        webViewRef?.let { wv ->
            WebViewBridgeScripts.captureWebViewSnapshot(
                webView = wv,
                scale = currentDeviceProfile.snapshotScale,
                preferRgb565 = currentDeviceProfile.preferRgb565Snapshots
            )?.let { bmp ->
                lastBackdropScrollY = wv.scrollY
                lastBackdropCaptureMs = SystemClock.uptimeMillis()
                motionTracker.updateWebScrollDelta(0f)
                onSnapshotLatest(tabId, bmp)
            }
        }
    }

    LaunchedEffect(isTabSwitcherOpen) {
        if (isTabSwitcherOpen) {
            webViewRef?.let { wv ->
                WebViewBridgeScripts.captureWebViewSnapshot(
                    webView = wv,
                    scale = currentDeviceProfile.snapshotScale,
                    preferRgb565 = currentDeviceProfile.preferRgb565Snapshots
                )?.let { bmp ->
                    onSnapshotLatest(currentTabId, bmp)
                }
            }
        }
    }

    DisposableEffect(isPrivate) {
        onDispose {
            try {
                if (isPrivate) {
                    CookieManager.getInstance().removeSessionCookies(null)
                } else {
                    CookieManager.getInstance().flush()
                }
            } catch (_: Throwable) {}
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
                        enableGpuRasterization = enableGpuRasterization
                    )
                    isVerticalScrollBarEnabled = true
                    overScrollMode = View.OVER_SCROLL_NEVER

                    try {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, !isPrivate)
                    } catch (_: Throwable) {}

                    settings.apply {
                        defaultMobileUserAgent = userAgentString.orEmpty()
                        javaScriptEnabled = true
                        domStorageEnabled = !isPrivate
                        databaseEnabled = !isPrivate
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                        standardFontFamily = "SF Pro Text"
                        sansSerifFontFamily = "SF Pro Text"
                    }

                    addJavascriptInterface(
                        WebBrowserJsBridge(
                            mainHandler = Handler(Looper.getMainLooper()),
                            onPlaybackStateChanged = { playing ->
                                isInlineVideoPlaying = playing
                            },
                            onBlobBase64Received = { dataUrl, fileName, mimeType ->
                                onBlobDownloadLatest(dataUrl, fileName, mimeType)
                            },
                            onPageAiContextReceived = { aiCtx ->
                                onPageAiContextLatest(aiCtx)
                            }
                        ),
                        "GlassVideoBridge"
                    )

                    // Live scroll listener so FrostedGlassBox dynamically shifts & refracts the webpage below!
                    setOnScrollChangeListener { v, _, scrollY, _, _ ->
                        val wv = v as? WebView ?: return@setOnScrollChangeListener
                        val deltaFromSnapshot = (scrollY - lastBackdropScrollY).toFloat()
                        motionTracker.updateWebScrollDelta(deltaFromSnapshot)

                        val now = SystemClock.uptimeMillis()
                        if (abs(scrollY - lastBackdropScrollY) >= 260 && now - lastBackdropCaptureMs >= 320L) {
                            lastBackdropCaptureMs = now
                            lastBackdropScrollY = scrollY
                            motionTracker.updateWebScrollDelta(0f)
                            WebViewBridgeScripts.captureWebViewSnapshot(
                                webView = wv,
                                scale = 0.24f,
                                preferRgb565 = true
                            )?.let { backdropBmp ->
                                onLiveBackdropLatest(backdropBmp)
                            }
                        }
                    }

                    setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                        onFindResultsLatest(activeMatchOrdinal, numberOfMatches)
                    }

                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                        if (downloadUrl.isNullOrBlank()) return@setDownloadListener
                        if (downloadUrl.startsWith("blob:", ignoreCase = true)) {
                            val guessed = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                            WebViewBridgeScripts.triggerBlobDownloadExtraction(
                                webView = this,
                                blobUrl = downloadUrl,
                                suggestedFileName = guessed,
                                fallbackMimeType = mimetype
                            )
                        } else {
                            onDownloadLatest(
                                downloadUrl,
                                userAgent,
                                contentDisposition,
                                mimetype,
                                contentLength
                            )
                        }
                    }

                    setOnLongClickListener { view ->
                        val hit = (view as? WebView)?.hitTestResult ?: return@setOnLongClickListener false
                        val extra = hit.extra?.takeIf { it.isNotBlank() } ?: return@setOnLongClickListener false
                        when (hit.type) {
                            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                hitContextTarget = WebHitContextTarget(
                                    targetUrl = extra,
                                    isImage = false
                                )
                                true
                            }
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                            WebView.HitTestResult.IMAGE_TYPE -> {
                                hitContextTarget = WebHitContextTarget(
                                    targetUrl = extra,
                                    imageUrl = extra,
                                    isImage = true
                                )
                                true
                            }
                            else -> false
                        }
                    }

                    // Rubber-band spring touch handler at WebView top & bottom limits
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
                                    scope.launch {
                                        rubberY.snapTo(next)
                                        motionTracker.notifyMoved()
                                    }
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
                                        ) {
                                            motionTracker.notifyMoved()
                                        }
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
                            WebViewBridgeScripts.injectVideoAndAiContextObservers(wv)
                            onSslSummaryLatest(WebViewBridgeScripts.buildSslSummary(wv, finalUrl))
                            onFinishedLatest(
                                currentTabId,
                                finalUrl,
                                wv.title,
                                wv.canGoBack(),
                                wv.canGoForward(),
                                currentPrivate
                            )
                            WebViewBridgeScripts.captureWebViewSnapshot(
                                webView = wv,
                                scale = currentDeviceProfile.snapshotScale,
                                preferRgb565 = currentDeviceProfile.preferRgb565Snapshots
                            )?.let { bmp ->
                                lastBackdropScrollY = wv.scrollY
                                lastBackdropCaptureMs = SystemClock.uptimeMillis()
                                motionTracker.updateWebScrollDelta(0f)
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
                                WebViewBridgeScripts.injectVideoAndAiContextObservers(view)
                            }
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (filePathCallback == null) return false
                            pendingFileChooserCallback?.onReceiveValue(null)
                            pendingFileChooserCallback = filePathCallback
                            val acceptTypes = fileChooserParams?.acceptTypes
                                ?.filter { it.isNotBlank() }
                                ?.toTypedArray()
                                ?.takeIf { it.isNotEmpty() }
                                ?: arrayOf("*/*")
                            return try {
                                fileChooserLauncher.launch(acceptTypes)
                                true
                            } catch (_: Throwable) {
                                pendingFileChooserCallback = null
                                false
                            }
                        }

                        override fun onJsAlert(
                            view: WebView?,
                            dialogUrl: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            if (result == null) return false
                            pendingJsCancel?.invoke()
                            val cancelFn = { result.cancel() }
                            pendingJsCancel = cancelFn
                            activeJsDialog = WebJsDialogRequest(
                                title = Uri.parse(dialogUrl.orEmpty()).host?.ifBlank { "Page Alert" } ?: "Page Alert",
                                message = message.orEmpty(),
                                isConfirm = false,
                                isPrompt = false,
                                onConfirm = {
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.confirm()
                                },
                                onCancel = {
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.cancel()
                                }
                            )
                            return true
                        }

                        override fun onJsConfirm(
                            view: WebView?,
                            dialogUrl: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            if (result == null) return false
                            pendingJsCancel?.invoke()
                            pendingJsCancel = { result.cancel() }
                            activeJsDialog = WebJsDialogRequest(
                                title = Uri.parse(dialogUrl.orEmpty()).host?.ifBlank { "Confirm Action" } ?: "Confirm Action",
                                message = message.orEmpty(),
                                isConfirm = true,
                                isPrompt = false,
                                onConfirm = {
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.confirm()
                                },
                                onCancel = {
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.cancel()
                                }
                            )
                            return true
                        }

                        override fun onJsPrompt(
                            view: WebView?,
                            dialogUrl: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult?
                        ): Boolean {
                            if (result == null) return false
                            pendingJsCancel?.invoke()
                            pendingJsCancel = { result.cancel() }
                            activeJsDialog = WebJsDialogRequest(
                                title = Uri.parse(dialogUrl.orEmpty()).host?.ifBlank { "Page Prompt" } ?: "Page Prompt",
                                message = message.orEmpty(),
                                isConfirm = true,
                                isPrompt = true,
                                defaultPromptValue = defaultValue.orEmpty(),
                                onConfirm = { entered ->
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.confirm(entered.orEmpty())
                                },
                                onCancel = {
                                    pendingJsCancel = null
                                    activeJsDialog = null
                                    result.cancel()
                                }
                            )
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
                    enableGpuRasterization = enableGpuRasterization
                )
                if (url.isNotBlank() && wv.url != url && wv.originalUrl != url) {
                    isInlineVideoPlaying = false
                    wv.loadUrl(url)
                }
            }
        )

        // Long-press link / image frosted context menu sheet
        hitContextTarget?.let { target ->
            WebContextMenuGlassSheet(
                target = target,
                onOpenInNewTab = onOpenUrlInNewTab,
                onDownloadUrl = { dlUrl ->
                    onDownloadLatest(dlUrl, null, null, null, -1L)
                },
                onAskSiriAboutUrl = onAskSiriAboutUrl,
                onCopyUrl = onCopyUrl,
                onDismiss = { hitContextTarget = null }
            )
        }

        // Frosted-glass JS alert / confirm / prompt modal
        activeJsDialog?.let { jsReq ->
            WebJsGlassDialog(request = jsReq)
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
