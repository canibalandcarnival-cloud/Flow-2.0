package com.example.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import org.json.JSONObject

const val DESKTOP_CHROME_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * Unified `@JavascriptInterface` bridge for Glass Browser's active WebView:
 * - Reports inline/fullscreen HTML5 `<video>` playback state
 * - Receives decoded `blob:` downloads via `FileReader.readAsDataURL`
 * - Streams live webpage DOM title, URL, highlighted selection, and visible text to Siri Intelligence
 */
@Keep
class WebBrowserJsBridge(
    private val mainHandler: Handler,
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onBlobBase64Received: (dataUrl: String, fileName: String?, mimeType: String?) -> Unit,
    private val onPageAiContextReceived: (WebPageAiContext) -> Unit
) {
    @Keep
    @JavascriptInterface
    fun onVideoState(playing: Boolean) {
        mainHandler.post {
            onPlaybackStateChanged(playing)
        }
    }

    @Keep
    @JavascriptInterface
    fun onBlobBase64(dataUrl: String?, fileName: String?, mimeType: String?) {
        val safeData = dataUrl?.trim().orEmpty()
        if (safeData.isEmpty()) return
        mainHandler.post {
            onBlobBase64Received(safeData, fileName, mimeType)
        }
    }

    @Keep
    @JavascriptInterface
    fun onDomContext(url: String?, title: String?, selection: String?, bodySnippet: String?) {
        val context = WebPageAiContext(
            url = url.orEmpty(),
            title = title.orEmpty(),
            selectedText = selection.orEmpty().take(1_600),
            bodySnippet = bodySnippet.orEmpty().take(11_000)
        )
        mainHandler.post {
            onPageAiContextReceived(context)
        }
    }
}

object WebViewBridgeScripts {

    /**
     * Injects passive HTML5 `<video>` playback tracking + passive `selectionchange` & DOM text
     * extraction for Siri Intelligence without blocking the browser's scroll/paint loop.
     */
    fun injectVideoAndAiContextObservers(webView: WebView) {
        val script = """
            (function() {
                try {
                    function publishDomContext() {
                        try {
                            if (!window.GlassVideoBridge || !window.GlassVideoBridge.onDomContext) return;
                            var sel = window.getSelection ? String(window.getSelection() || '') : '';
                            var rawBody = (document && document.body && document.body.innerText) ? document.body.innerText : '';
                            var compactBody = rawBody.replace(/\s+/g, ' ').trim().slice(0, 11000);
                            window.GlassVideoBridge.onDomContext(
                                String(location.href || ''),
                                String(document.title || ''),
                                sel.slice(0, 1600),
                                compactBody
                            );
                        } catch (ctxErr) {}
                    }

                    window.__glassPublishDomContext = publishDomContext;

                    if (window.__glassVideoHooked) {
                        if (window.__glassCheckVideoState) window.__glassCheckVideoState();
                        publishDomContext();
                        return;
                    }
                    window.__glassVideoHooked = true;
                    var lastReported = null;

                    function isVisiblePlayingVideo(v) {
                        if (!v || v.paused || v.ended || v.readyState < 2) return false;
                        var rect = v.getBoundingClientRect();
                        return rect.width >= 80 && rect.height >= 60;
                    }

                    function checkVideoState() {
                        try {
                            var fsEl = document.fullscreenElement || document.webkitFullscreenElement;
                            var playing = !!fsEl;
                            if (!playing) {
                                var videos = document.getElementsByTagName('video');
                                for (var i = 0; i < videos.length; i++) {
                                    if (isVisiblePlayingVideo(videos[i])) {
                                        playing = true;
                                        break;
                                    }
                                }
                            }
                            window.__glassVideoActivelyPlaying = playing;
                            if (document && document.body) {
                                var targetPad = playing ? '0px' : '88px';
                                if (document.body.style.paddingBottom !== targetPad) {
                                    document.body.style.paddingBottom = targetPad;
                                }
                            }
                            if (playing !== lastReported) {
                                lastReported = playing;
                                if (window.GlassVideoBridge && window.GlassVideoBridge.onVideoState) {
                                    window.GlassVideoBridge.onVideoState(playing);
                                }
                            }
                        } catch (err) {}
                    }

                    window.__glassCheckVideoState = checkVideoState;

                    var mediaEvents = [
                        'play', 'playing', 'pause', 'ended', 'emptied',
                        'fullscreenchange', 'webkitfullscreenchange',
                        'webkitbeginfullscreen', 'webkitendfullscreen'
                    ];
                    for (var i = 0; i < mediaEvents.length; i++) {
                        document.addEventListener(mediaEvents[i], checkVideoState, { capture: true, passive: true });
                    }

                    var selTimer = null;
                    document.addEventListener('selectionchange', function() {
                        if (selTimer) clearTimeout(selTimer);
                        selTimer = setTimeout(publishDomContext, 320);
                    }, { passive: true });

                    checkVideoState();
                    publishDomContext();
                } catch (outerErr) {}
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(script, null)
        } catch (_: Throwable) {}
    }

    /**
     * Converts a `blob:` download URL inside the page's origin into a Base64 Data URL and forwards
     * it to [WebBrowserJsBridge.onBlobBase64].
     */
    fun triggerBlobDownloadExtraction(
        webView: WebView,
        blobUrl: String,
        suggestedFileName: String?,
        fallbackMimeType: String?
    ) {
        val quotedUrl = JSONObject.quote(blobUrl)
        val quotedName = JSONObject.quote(suggestedFileName.orEmpty())
        val quotedMime = JSONObject.quote(fallbackMimeType.orEmpty())
        val js = """
            (function() {
                try {
                    fetch($quotedUrl)
                        .then(function(r) { return r.blob(); })
                        .then(function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                if (window.GlassVideoBridge && window.GlassVideoBridge.onBlobBase64) {
                                    window.GlassVideoBridge.onBlobBase64(
                                        String(reader.result || ''),
                                        $quotedName,
                                        blob.type || $quotedMime
                                    );
                                }
                            };
                            reader.readAsDataURL(blob);
                        })
                        .catch(function() {});
                } catch (e) {}
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(js, null)
        } catch (_: Throwable) {}
    }

    fun buildSslSummary(webView: WebView, currentUrl: String): String {
        val cert = try {
            webView.certificate
        } catch (_: Throwable) {
            null
        }
        return if (cert != null) {
            val subject = cert.issuedTo?.cName?.takeIf { it.isNotBlank() }
                ?: cert.issuedTo?.oName?.takeIf { it.isNotBlank() }
                ?: Uri.parse(currentUrl).host.orEmpty()
            val issuer = cert.issuedBy?.oName?.takeIf { it.isNotBlank() }
                ?: cert.issuedBy?.cName.orEmpty()
            if (issuer.isNotBlank()) "HTTPS Verified • $subject ($issuer)" else "HTTPS Verified • $subject"
        } else if (currentUrl.startsWith("https://", ignoreCase = true)) {
            "Encrypted HTTPS Connection"
        } else {
            "Unencrypted / Local Page"
        }
    }

    fun captureWebViewSnapshot(
        webView: WebView,
        scale: Float,
        preferRgb565: Boolean
    ): Bitmap? {
        return try {
            val w = webView.width
            val h = webView.height
            if (w <= 16 || h <= 16) return null
            val bmpW = (w * scale).toInt().coerceAtLeast(64)
            val bmpH = (h * scale).toInt().coerceAtLeast(84)
            val config = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            val bmp = Bitmap.createBitmap(bmpW, bmpH, config)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}
