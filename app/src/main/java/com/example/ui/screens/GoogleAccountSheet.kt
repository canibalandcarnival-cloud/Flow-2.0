package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.example.R
import com.example.core.GoogleWebSessionManager
import com.example.core.GpuRenderController
import com.example.data.BrowserRepository
import com.example.data.BrowserSettingsState
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.GlassModalBackdrop
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.LocalGlassStyle

/**
 * Monochrome Google "G" vector emblem obeying the strict Black/White browser chrome palette.
 */
@Composable
fun MonochromeGoogleGIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.19f
        val inset = stroke * 0.65f
        val arcSize = Size(w - inset * 2f, h - inset * 2f)

        // Crisp geometric "G" arc + horizontal crossbar
        drawArc(
            color = tint,
            startAngle = 42f,
            sweepAngle = 278f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.50f),
            end = Offset(w - inset * 0.72f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Butt
        )
        drawLine(
            color = tint,
            start = Offset(w - inset * 0.98f, h * 0.50f),
            end = Offset(w - inset * 0.98f, h * 0.73f),
            strokeWidth = stroke,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * Google Account section rendered inside Customize & Wallpaper settings:
 * - Signed out: "Sign in with Google" card with bouncy "Sign In" pill button.
 * - Signed in: Shows profile avatar/badge, name/email, "Account" button, and "Sign Out" button.
 */
@Composable
fun GoogleAccountSettingsSection(
    settings: BrowserSettingsState,
    onSignInClick: () -> Unit,
    onManageAccountClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_account_section")
    ) {
        Text(
            text = stringResource(R.string.google_account_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = style.contentPrimary
        )

        FrostedGlassBox(
            shape = RoundedCornerShape(22.dp),
            elevation = GlassElevation.CARD,
            useLiveSceneBackdrop = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!settings.isGoogleSignedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(style.activeSegmentFill)
                                .border(1.dp, style.rimTopLeft, CircleShape)
                        ) {
                            MonochromeGoogleGIcon(
                                tint = style.contentPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.google_sign_in_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentPrimary
                            )
                            Text(
                                text = stringResource(R.string.google_sign_in_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.contentSecondary
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(style.solidActionFill)
                            .testTag("google_sign_in_button")
                            .bouncyClickable(
                                pressedScale = 0.90f,
                                contentDescriptionText = stringResource(R.string.google_sign_in_title),
                                onClick = onSignInClick
                            )
                            .padding(horizontal = 18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.google_sign_in_button),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = style.solidActionContent
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(style.solidActionFill)
                                .border(1.dp, style.rimTopLeft, CircleShape)
                        ) {
                            if (settings.googleAccountAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = settings.googleAccountAvatarUrl,
                                    contentDescription = stringResource(R.string.google_account_section_title),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = settings.googleAccountName.firstOrNull()?.uppercaseChar()
                                    ?: settings.googleAccountEmail.firstOrNull()?.uppercaseChar()
                                if (initial != null) {
                                    Text(
                                        text = initial.toString(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = style.solidActionContent
                                    )
                                } else {
                                    MonochromeGoogleGIcon(
                                        tint = style.solidActionContent,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val primaryLabel = when {
                                settings.googleAccountName.isNotBlank() -> settings.googleAccountName
                                settings.googleAccountEmail.isNotBlank() -> settings.googleAccountEmail
                                else -> stringResource(R.string.google_signed_in_default_name)
                            }
                            val secondaryLabel = when {
                                settings.googleAccountEmail.isNotBlank() &&
                                    settings.googleAccountName.isNotBlank() -> settings.googleAccountEmail
                                else -> stringResource(R.string.google_signed_in_active_subtitle)
                            }
                            Text(
                                text = primaryLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = secondaryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = style.contentSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.activeSegmentFill)
                                .border(1.dp, style.rimTopLeft, RoundedCornerShape(50))
                                .testTag("google_manage_account_button")
                                .bouncyClickable(
                                    pressedScale = 0.92f,
                                    contentDescriptionText = stringResource(R.string.google_manage_account_button),
                                    onClick = onManageAccountClick
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.google_manage_account_button),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentPrimary
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.solidActionFill)
                                .testTag("google_sign_out_button")
                                .bouncyClickable(
                                    pressedScale = 0.92f,
                                    contentDescriptionText = stringResource(R.string.google_sign_out_button),
                                    onClick = onSignOutClick
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.google_sign_out_button),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = style.solidActionContent
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated in-browser Frosted Glass Google Sign-In Sheet backed by the shared Chromium
 * [android.webkit.CookieManager] so signing in once immediately authenticates Google Search,
 * YouTube, Gmail, Drive, and third-party websites inside Glass Browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleAccountSignInOverlay(
    onSignInCompleted: (email: String, name: String, avatarUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    val onSignInCompletedLatest by rememberUpdatedState(onSignInCompleted)
    val onDismissLatest by rememberUpdatedState(onDismiss)

    var authWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(GoogleWebSessionManager.GOOGLE_SIGN_IN_URL) }
    var loadProgress by remember { mutableFloatStateOf(0.15f) }
    var isSessionAuthenticated by remember { mutableStateOf(GoogleWebSessionManager.isGoogleSessionActive()) }
    var hasCompletedCallback by remember { mutableStateOf(false) }

    fun finishWithProfile(wv: WebView?) {
        if (hasCompletedCallback) return
        hasCompletedCallback = true
        GoogleWebSessionManager.flushCookies()
        if (wv != null) {
            GoogleWebSessionManager.extractProfileFromWebView(wv) { profile ->
                onSignInCompletedLatest(profile.email, profile.name, profile.avatarUrl)
            }
        } else {
            onSignInCompletedLatest("", "", "")
        }
    }

    BackHandler(enabled = true) {
        val popup = popupWebView
        val mainWv = authWebView
        when {
            popup != null -> {
                popup.destroy()
                popupWebView = null
            }
            mainWv != null && mainWv.canGoBack() -> mainWv.goBack()
            isSessionAuthenticated -> finishWithProfile(mainWv)
            else -> onDismissLatest()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GoogleWebSessionManager.flushCookies()
            popupWebView?.destroy()
            popupWebView = null
        }
    }

    GlassModalBackdrop(
        scrimAlpha = 0.38f,
        onDismiss = {
            if (isSessionAuthenticated) {
                finishWithProfile(authWebView)
            } else {
                onDismissLatest()
            }
        },
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.statusBarsPadding()
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(top = 10.dp)
                .navigationBarsPadding()
                .imePadding()
                .testTag("google_sign_in_sheet")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Frosted Sheet Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(style.activeSegmentFill)
                        ) {
                            MonochromeGoogleGIcon(
                                tint = style.contentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.google_auth_sheet_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = style.contentSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = BrowserRepository.extractDomain(currentUrl),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = style.contentSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSessionAuthenticated) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(style.solidActionFill)
                                    .testTag("google_sign_in_done_button")
                                    .bouncyClickable(
                                        pressedScale = 0.90f,
                                        contentDescriptionText = stringResource(R.string.google_auth_done_button),
                                        onClick = { finishWithProfile(authWebView) }
                                    )
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.google_auth_done_button),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = style.solidActionContent
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(style.activeSegmentFill)
                                .testTag("close_google_sign_in_button")
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    contentDescriptionText = stringResource(R.string.cd_close_google_sign_in),
                                    onClick = {
                                        if (isSessionAuthenticated) {
                                            finishWithProfile(authWebView)
                                        } else {
                                            onDismissLatest()
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_close_google_sign_in),
                                tint = style.contentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Monochrome hairline progress bar
                if (loadProgress in 0.01f..0.99f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadProgress)
                            .height(2.dp)
                            .background(style.contentPrimary)
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }

                // Shared CookieManager Sign-In WebView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(style.glassBaseColor)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("google_sign_in_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                GpuRenderController.applyWebViewGpuPipeline(
                                    webView = this,
                                    forceGpuRendering = true,
                                    disableHardwareOverlays = true,
                                    enableGpuRasterization = true,
                                    disableGpuVsync = true,
                                    disableFrameRateLimit = true
                                )
                                GoogleWebSessionManager.configureSharedSessionWebView(
                                    context = ctx,
                                    webView = this,
                                    isPrivate = false
                                )
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    standardFontFamily = "SF Pro Text"
                                    sansSerifFontFamily = "SF Pro Text"
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
                                                    mapOf("Access-Control-Allow-Origin" to "*"),
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
                                    ): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase()
                                        return scheme != null && scheme != "http" && scheme != "https"
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                        }
                                        isSessionAuthenticated = GoogleWebSessionManager.isGoogleSessionActive()
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val wv = view ?: return
                                        val finalUrl = url ?: wv.url.orEmpty()
                                        currentUrl = finalUrl
                                        GpuRenderController.injectPageSfProTypography(wv)
                                        GoogleWebSessionManager.flushCookies()

                                        val active = GoogleWebSessionManager.isGoogleSessionActive()
                                        isSessionAuthenticated = active
                                        if (active && GoogleWebSessionManager.isPostLoginDestinationUrl(finalUrl)) {
                                            finishWithProfile(wv)
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        loadProgress = (newProgress / 100f).coerceIn(0f, 1f)
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
                                                isPrivate = false
                                            )
                                            webViewClient = WebViewClient()
                                            webChromeClient = object : WebChromeClient() {
                                                override fun onCloseWindow(window: WebView?) {
                                                    window?.destroy()
                                                    if (popupWebView === window) popupWebView = null
                                                    GoogleWebSessionManager.flushCookies()
                                                    isSessionAuthenticated = GoogleWebSessionManager.isGoogleSessionActive()
                                                }
                                            }
                                        }
                                        popupWebView?.destroy()
                                        popupWebView = child
                                        transport.webView = child
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                }

                                authWebView = this
                                loadUrl(GoogleWebSessionManager.GOOGLE_SIGN_IN_URL)
                            }
                        },
                        update = { wv -> authWebView = wv }
                    )

                    val activePopup = popupWebView
                    if (activePopup != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { c -> FrameLayout(c) },
                            update = { container ->
                                if (activePopup.parent !== container) {
                                    (activePopup.parent as? ViewGroup)?.removeView(activePopup)
                                    container.removeAllViews()
                                    container.addView(
                                        activePopup,
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
        }
    }
}
