package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject

/**
 * Manages shared Chromium WebView Google Account authentication, cookie persistence,
 * standard mobile browser User-Agent sanitization (preventing `403 disallowed_useragent`),
 * and third-party federated OAuth session sharing across websites.
 */
object GoogleWebSessionManager {

    const val GOOGLE_SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?hl=en&continue=https%3A%2F%2Fmyaccount.google.com%2F"
    const val GOOGLE_MANAGE_ACCOUNT_URL = "https://myaccount.google.com/"

    private val AUTHENTICATED_COOKIE_NAMES = setOf(
        "SID",
        "HSID",
        "SSID",
        "SAPISID",
        "__Secure-1PSID",
        "__Secure-3PSID"
    )

    private val LOGIN_FLOW_PATH_PREFIXES = listOf(
        "/servicelogin",
        "/signin",
        "/v3/signin",
        "/accountchooser",
        "/checkcookie",
        "/interactivelogin",
        "/addsession",
        "/challenge"
    )

    data class GoogleProfileInfo(
        val email: String = "",
        val name: String = "",
        val avatarUrl: String = ""
    )

    /**
     * Strips embedded Android WebView markers (`; wv`, `Version/4.0`) while preserving
     * the device's genuine Chromium engine version so Google Accounts, Passkeys, 2FA,
     * and federated OAuth flows treat Glass Browser as a standard mobile browser.
     */
    fun sanitizeBrowserUserAgent(rawUserAgent: String?): String {
        val base = rawUserAgent?.trim().orEmpty()
        if (base.isEmpty()) {
            return "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        }
        return base
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace(Regex("\\bVersion/\\d+\\.\\d+\\s*"), "")
            .replace(Regex("\\bwv\\b\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    fun buildStandardBrowserUserAgent(context: Context): String {
        BrowserEngineInitializer.sanitizeWebViewVariationsSeeds(context)
        val raw = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }
        return sanitizeBrowserUserAgent(raw)
    }

    /**
     * Checks on-disk WebView profile storage without spinning up the Chromium provider so cold
     * Start Page launches do not unnecessarily initialize WebView when no cookies exist yet.
     */
    fun hasPersistedWebViewCookieStore(context: Context): Boolean {
        return runCatching {
            val webViewDir = context.getDir("webview", Context.MODE_PRIVATE)
            val defaultCookies = java.io.File(webViewDir, "Default/Cookies")
            val legacyCookies = java.io.File(webViewDir, "Cookies")
            (defaultCookies.isFile && defaultCookies.length() > 0L) ||
                (legacyCookies.isFile && legacyCookies.length() > 0L)
        }.getOrDefault(false)
    }

    /**
     * Configures a WebView instance to participate in the shared browser cookie jar and
     * OAuth popup flow when not in Private Mode, while keeping Private tabs isolated.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configureSharedSessionWebView(
        context: Context,
        webView: WebView,
        isPrivate: Boolean
    ) {
        BrowserEngineInitializer.sanitizeWebViewVariationsSeeds(context)
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, !isPrivate)
        } catch (_: Throwable) {}

        webView.settings.apply {
            userAgentString = buildStandardBrowserUserAgent(context)
            javaScriptEnabled = true
            domStorageEnabled = !isPrivate
            databaseEnabled = !isPrivate
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }
    }

    /**
     * Pure helper verifying whether a `Cookie` header string contains genuine post-login
     * Google session cookies (`SID`, `SAPISID`, `__Secure-1PSID`, `__Secure-3PSID`).
     */
    fun hasAuthenticatedGoogleCookie(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false
        val pairs = cookieHeader.split(';')
        var foundSidFamily = false
        for (rawPair in pairs) {
            val idx = rawPair.indexOf('=')
            if (idx <= 0) continue
            val key = rawPair.substring(0, idx).trim()
            val value = rawPair.substring(idx + 1).trim()
            if (key in AUTHENTICATED_COOKIE_NAMES && value.length >= 8 && !value.equals("deleted", ignoreCase = true)) {
                foundSidFamily = true
                break
            }
        }
        return foundSidFamily
    }

    /**
     * Checks live Chromium [CookieManager] state for an authenticated Google web session.
     */
    fun isGoogleSessionActive(): Boolean {
        return try {
            val cm = CookieManager.getInstance()
            val googleCookies = cm.getCookie("https://www.google.com")
            val accountsCookies = cm.getCookie("https://accounts.google.com")
            hasAuthenticatedGoogleCookie(googleCookies) || hasAuthenticatedGoogleCookie(accountsCookies)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true once the sign-in WebView has completed login challenges and landed on
     * an authenticated Google destination (`myaccount.google.com`, `www.google.com`, etc.).
     */
    fun isPostLoginDestinationUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.lowercase().orEmpty()
            if (!host.endsWith("google.com")) return false
            if (host == "accounts.google.com") {
                LOGIN_FLOW_PATH_PREFIXES.none { prefix -> path.startsWith(prefix) } &&
                    path.isNotBlank() &&
                    path != "/"
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isGoogleDomain(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = Uri.parse(url).host?.lowercase().orEmpty()
            host == "google.com" ||
                host.endsWith(".google.com") ||
                host == "youtube.com" ||
                host.endsWith(".youtube.com")
        } catch (_: Throwable) {
            false
        }
    }

    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {}
    }

    /**
     * Signs out of the browser's Google web session by clearing Chromium cookies and flushing disk state.
     */
    fun signOutGoogleWebSession(onCleared: () -> Unit = {}) {
        try {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies {
                try {
                    cm.flush()
                } catch (_: Throwable) {}
                onCleared()
            }
        } catch (_: Throwable) {
            onCleared()
        }
    }

    /**
     * Extracts `{ email, name, avatarUrl }` from an authenticated Google page DOM if visible.
     */
    fun extractProfileFromWebView(
        webView: WebView,
        onResult: (GoogleProfileInfo) -> Unit
    ) {
        val script = """
            (function() {
                try {
                    var email = '';
                    var name = '';
                    var avatar = '';
                    var emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/;

                    var dataEmailEl = document.querySelector('[data-email]');
                    if (dataEmailEl) {
                        email = dataEmailEl.getAttribute('data-email') || '';
                    }

                    if (!email) {
                        var ariaEls = document.querySelectorAll('[aria-label]');
                        for (var i = 0; i < ariaEls.length; i++) {
                            var lbl = ariaEls[i].getAttribute('aria-label') || '';
                            var m = lbl.match(emailRegex);
                            if (m) {
                                email = m[0];
                                break;
                            }
                        }
                    }

                    if (!email && document.body) {
                        var bodyText = (document.body.innerText || '').slice(0, 6000);
                        var bm = bodyText.match(emailRegex);
                        if (bm) email = bm[0];
                    }

                    var h1 = document.querySelector('h1');
                    if (h1 && h1.innerText) {
                        var t = h1.innerText.trim();
                        if (t.indexOf('Welcome,') === 0) {
                            name = t.replace('Welcome,', '').trim();
                        } else if (t.indexOf('Hi ') === 0) {
                            name = t.replace('Hi ', '').replace('!', '').trim();
                        }
                    }

                    var imgs = document.querySelectorAll('img[src*="googleusercontent.com"]');
                    for (var j = 0; j < imgs.length; j++) {
                        var src = imgs[j].getAttribute('src') || '';
                        if (src.indexOf('https://') === 0) {
                            avatar = src;
                            break;
                        }
                    }

                    return JSON.stringify({ email: email, name: name, avatar: avatar });
                } catch (e) {
                    return '{}';
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(script) { rawEval ->
                val parsed = parseEvaluatedProfileJson(rawEval)
                onResult(parsed)
            }
        } catch (_: Throwable) {
            onResult(GoogleProfileInfo())
        }
    }

    internal fun parseEvaluatedProfileJson(rawEval: String?): GoogleProfileInfo {
        if (rawEval.isNullOrBlank() || rawEval == "null") return GoogleProfileInfo()
        return try {
            val unwrapped = if (rawEval.startsWith("\"") && rawEval.endsWith("\"")) {
                JSONObject("{\"v\":$rawEval}").optString("v", "{}")
            } else {
                rawEval
            }
            val obj = JSONObject(unwrapped)
            GoogleProfileInfo(
                email = obj.optString("email", "").trim(),
                name = obj.optString("name", "").trim(),
                avatarUrl = obj.optString("avatar", "").trim()
            )
        } catch (_: Throwable) {
            GoogleProfileInfo()
        }
    }
}
