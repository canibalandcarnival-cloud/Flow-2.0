package com.example.core

import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.util.Locale

@Immutable
data class SiteNavItem(
    val label: String,
    val url: String,
    val isActive: Boolean = false,
    val isAnchor: Boolean = false
)

enum class SiteNavBarVariant {
    YOUTUBE_SAFARI_ISLAND,
    EDITORIAL_READER_DECK,
    DEV_COMMAND_HUD,
    SOCIAL_FLOATING_ORBS,
    UNIVERSAL_LIQUID_CAPSULE
}

@Immutable
data class SiteNavigationBarData(
    val siteOrigin: String = "",
    val siteBrand: String = "",
    val displayDomain: String = "",
    val monogram: String = "W",
    val homeUrl: String = "",
    val searchTemplate: String? = null,
    val hiddenNavCount: Int = 0,
    val variant: SiteNavBarVariant = SiteNavBarVariant.UNIVERSAL_LIQUID_CAPSULE,
    val preservesOriginalNavBar: Boolean = false,
    val docksBeneathSiteBottomBar: Boolean = false,
    val items: List<SiteNavItem> = emptyList()
)

/**
 * Dynamically extracts every visited website's navigation structure (`<nav>`, top `<header>`,
 * `[role="navigation"]`, `[role="banner"]`, fixed/sticky top bars, search forms, and section fallbacks),
 * suppresses the site's original HTML navigation bar, and powers Glass Browser's native per-site
 * frosted-glass navigation bar.
 */
object SiteNavigationEngine {

    private val KNOWN_BRAND_MAP = mapOf(
        "wikipedia.org" to ("Wikipedia" to "W"),
        "github.com" to ("GitHub" to "GH"),
        "news.ycombinator.com" to ("Hacker News" to "Y"),
        "developer.mozilla.org" to ("MDN Docs" to "M"),
        "mozilla.org" to ("Mozilla" to "MZ"),
        "dezeen.com" to ("Dezeen" to "DZ"),
        "archdaily.com" to ("ArchDaily" to "AD"),
        "surfacemag.com" to ("Surface" to "S"),
        "awwwards.com" to ("Awwwards" to "W."),
        "atlasobscura.com" to ("Atlas Obscura" to "AO"),
        "pinterest.com" to ("Pinterest" to "P"),
        "are.na" to ("Are.na" to "**"),
        "google.com" to ("Google" to "G"),
        "youtube.com" to ("YouTube" to "YT"),
        "youtu.be" to ("YouTube" to "YT"),
        "vimeo.com" to ("Vimeo" to "VM"),
        "twitch.tv" to ("Twitch" to "TW"),
        "reddit.com" to ("Reddit" to "R"),
        "nytimes.com" to ("NYTimes" to "NY"),
        "theverge.com" to ("The Verge" to "V"),
        "apple.com" to ("Apple" to "A")
    )

    private val YOUTUBE_ISLAND_DOMAINS = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "vimeo.com",
        "twitch.tv"
    )

    private val EDITORIAL_READER_DOMAINS = listOf(
        "wikipedia.org",
        "dezeen.com",
        "archdaily.com",
        "surfacemag.com",
        "atlasobscura.com",
        "architecturaldigest.com",
        "nytimes.com",
        "theverge.com",
        "medium.com",
        "substack.com",
        "bbc.com",
        "newyorker.com",
        "wired.com"
    )

    private val DEV_HUD_DOMAINS = listOf(
        "github.com",
        "developer.mozilla.org",
        "mozilla.org",
        "news.ycombinator.com",
        "stackoverflow.com",
        "gitlab.com",
        "kotlinlang.org",
        "developer.android.com",
        "npmjs.com",
        "crates.io"
    )

    private val SOCIAL_ORBS_DOMAINS = listOf(
        "pinterest.com",
        "are.na",
        "awwwards.com",
        "reddit.com",
        "instagram.com",
        "x.com",
        "twitter.com",
        "tiktok.com",
        "threads.net"
    )

    private val PRESERVE_ORIGINAL_BOTTOM_NAV_DOMAINS = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "vimeo.com",
        "twitch.tv",
        "pinterest.com",
        "reddit.com",
        "instagram.com",
        "x.com",
        "twitter.com",
        "tiktok.com",
        "threads.net"
    )

    fun resolveVariantForHost(rawHost: String): SiteNavBarVariant {
        val cleanHost = rawHost.lowercase(Locale.US).trim().removePrefix("www.")
        if (cleanHost.isBlank()) return SiteNavBarVariant.UNIVERSAL_LIQUID_CAPSULE
        return when {
            matchesAnyDomain(cleanHost, YOUTUBE_ISLAND_DOMAINS) -> SiteNavBarVariant.YOUTUBE_SAFARI_ISLAND
            matchesAnyDomain(cleanHost, EDITORIAL_READER_DOMAINS) -> SiteNavBarVariant.EDITORIAL_READER_DECK
            matchesAnyDomain(cleanHost, DEV_HUD_DOMAINS) -> SiteNavBarVariant.DEV_COMMAND_HUD
            matchesAnyDomain(cleanHost, SOCIAL_ORBS_DOMAINS) -> SiteNavBarVariant.SOCIAL_FLOATING_ORBS
            else -> SiteNavBarVariant.UNIVERSAL_LIQUID_CAPSULE
        }
    }

    fun shouldPreserveOriginalNavBar(rawHost: String): Boolean {
        val cleanHost = rawHost.lowercase(Locale.US).trim().removePrefix("www.")
        if (cleanHost.isBlank()) return false
        return matchesAnyDomain(cleanHost, PRESERVE_ORIGINAL_BOTTOM_NAV_DOMAINS)
    }

    fun formatDisplayDomain(rawHost: String): String {
        val lower = rawHost.lowercase(Locale.US).trim()
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")
            .removePrefix("en.m.")
            .removePrefix("en.")
        return lower.ifBlank { rawHost.trim() }
    }

    private fun matchesAnyDomain(cleanHost: String, suffixes: List<String>): Boolean {
        return suffixes.any { suffix ->
            cleanHost == suffix || cleanHost.endsWith(".$suffix")
        }
    }

    /**
     * Builds an immediate, zero-flicker per-site navigation bar model from [rawUrl] as soon as
     * navigation starts, before the remote DOM finishes streaming.
     */
    fun buildInitialPlaceholderForUrl(rawUrl: String): SiteNavigationBarData {
        if (rawUrl.isBlank()) return SiteNavigationBarData()
        val parsed = try {
            Uri.parse(rawUrl)
        } catch (_: Throwable) {
            return SiteNavigationBarData()
        }
        val scheme = parsed.scheme?.lowercase(Locale.US).orEmpty()
        val host = parsed.host?.lowercase(Locale.US)?.removePrefix("www.").orEmpty()
        if (host.isBlank() || (scheme != "http" && scheme != "https")) {
            return SiteNavigationBarData(
                siteBrand = rawUrl.take(20),
                monogram = "W",
                homeUrl = rawUrl,
                items = listOf(SiteNavItem(label = "Overview", url = rawUrl, isActive = true))
            )
        }

        val origin = "$scheme://${parsed.host}"
        val (brand, monogram) = deriveBrandAndMonogram(host, explicitBrand = null)
        val variant = resolveVariantForHost(host)
        val preservesOriginal = shouldPreserveOriginalNavBar(host)
        val displayDomain = formatDisplayDomain(parsed.host.orEmpty())
        val segments = parsed.pathSegments.orEmpty().filter { it.isNotBlank() }

        val builtItems = mutableListOf<SiteNavItem>()
        val isAtRoot = segments.isEmpty() && parsed.query.isNullOrBlank()
        builtItems.add(
            SiteNavItem(
                label = "Home",
                url = "$origin/",
                isActive = isAtRoot
            )
        )

        var cumulativePath = ""
        segments.take(3).forEachIndexed { idx, seg ->
            cumulativePath += "/$seg"
            val cleanSegLabel = humanizeSegment(seg)
            if (cleanSegLabel.isNotBlank() &&
                builtItems.none { it.label.equals(cleanSegLabel, ignoreCase = true) }
            ) {
                val isLast = idx == segments.take(3).lastIndex
                builtItems.add(
                    SiteNavItem(
                        label = cleanSegLabel,
                        url = if (isLast && !parsed.query.isNullOrBlank()) rawUrl else "$origin$cumulativePath",
                        isActive = isLast
                    )
                )
            }
        }

        return SiteNavigationBarData(
            siteOrigin = origin,
            siteBrand = brand,
            displayDomain = displayDomain,
            monogram = monogram,
            homeUrl = "$origin/",
            searchTemplate = null,
            hiddenNavCount = 0,
            variant = variant,
            preservesOriginalNavBar = preservesOriginal,
            docksBeneathSiteBottomBar = preservesOriginal,
            items = builtItems
        )
    }

    /**
     * Parses the JSON payload emitted by the injected DOM navigation extractor and enriches it
     * with clean brand naming, active-route matching, and guaranteed per-site fallback pills.
     */
    fun parseBridgeJson(payloadJson: String, fallbackUrl: String): SiteNavigationBarData {
        if (payloadJson.isBlank()) return buildInitialPlaceholderForUrl(fallbackUrl)
        return try {
            val root = JSONObject(payloadJson)
            val pageUrl = root.optString("pageUrl").ifBlank { fallbackUrl }
            val parsedPage = Uri.parse(pageUrl)
            val scheme = parsedPage.scheme?.lowercase(Locale.US).orEmpty()
            val rawHost = parsedPage.host.orEmpty()
            val cleanHost = rawHost.lowercase(Locale.US).removePrefix("www.")
            val origin = root.optString("origin").takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            } ?: if (rawHost.isNotBlank() && (scheme == "http" || scheme == "https")) {
                "$scheme://$rawHost"
            } else {
                ""
            }

            val rawBrand = root.optString("brand").trim()
            val (brand, monogram) = deriveBrandAndMonogram(cleanHost, rawBrand)
            val homeUrl = root.optString("homeUrl")
                .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?: if (origin.isNotBlank()) "$origin/" else pageUrl

            val searchTemplate = root.optString("searchTemplate")
                .trim()
                .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

            val hiddenCount = root.optInt("hiddenCount", 0)
            val normalizedPageUrl = normalizeComparableUrl(pageUrl)

            val extracted = mutableListOf<SiteNavItem>()
            val seenUrls = HashSet<String>()
            val seenLabels = HashSet<String>()

            val itemsArray = root.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.optJSONObject(i) ?: continue
                    val rawLabel = sanitizeNavLabel(obj.optString("label"))
                    val itemUrl = obj.optString("url").trim()
                    val isAnchor = obj.optBoolean("isAnchor", itemUrl.contains('#') && isSameDocumentAnchor(pageUrl, itemUrl))
                    if (rawLabel.length < 2 || itemUrl.isBlank()) continue
                    if (!itemUrl.startsWith("http://", true) &&
                        !itemUrl.startsWith("https://", true) &&
                        !itemUrl.startsWith("#")
                    ) {
                        continue
                    }

                    val normUrl = normalizeComparableUrl(itemUrl)
                    val labelKey = rawLabel.lowercase(Locale.US)
                    if (labelKey == brand.lowercase(Locale.US) && normUrl == normalizeComparableUrl(homeUrl)) {
                        // Brand logo link is already represented by the dedicated Home Brand Pill on the left
                        continue
                    }
                    if (!seenUrls.add(normUrl) || !seenLabels.add(labelKey)) continue

                    val isActive = obj.optBoolean("isActive", false) ||
                        (normUrl.isNotBlank() && normUrl == normalizedPageUrl)

                    extracted.add(
                        SiteNavItem(
                            label = rawLabel,
                            url = itemUrl,
                            isActive = isActive,
                            isAnchor = isAnchor
                        )
                    )
                    if (extracted.size >= 14) break
                }
            }

            // Guarantee EVERY site gets a rich, functional navigation bar even if the page had minimal HTML
            if (extracted.isEmpty()) {
                val placeholder = buildInitialPlaceholderForUrl(pageUrl)
                extracted.addAll(placeholder.items)
            } else {
                val hasHomeLike = extracted.any {
                    it.label.equals("Home", true) ||
                        it.label.equals("Overview", true) ||
                        it.label.equals("Main page", true) ||
                        normalizeComparableUrl(it.url) == normalizeComparableUrl(homeUrl)
                }
                if (!hasHomeLike && homeUrl.isNotBlank()) {
                    val isRootActive = extracted.none { it.isActive } &&
                        normalizeComparableUrl(pageUrl) == normalizeComparableUrl(homeUrl)
                    extracted.add(
                        0,
                        SiteNavItem(
                            label = "Home",
                            url = homeUrl,
                            isActive = isRootActive,
                            isAnchor = false
                        )
                    )
                }
            }

            val variant = resolveVariantForHost(cleanHost)
            val preservesOriginal = shouldPreserveOriginalNavBar(cleanHost)
            val displayDomain = formatDisplayDomain(rawHost.ifBlank { cleanHost })

            SiteNavigationBarData(
                siteOrigin = origin,
                siteBrand = brand,
                displayDomain = displayDomain,
                monogram = monogram,
                homeUrl = homeUrl,
                searchTemplate = searchTemplate,
                hiddenNavCount = if (preservesOriginal) 0 else hiddenCount,
                variant = variant,
                preservesOriginalNavBar = preservesOriginal,
                docksBeneathSiteBottomBar = preservesOriginal,
                items = extracted.take(14)
            )
        } catch (_: Throwable) {
            buildInitialPlaceholderForUrl(fallbackUrl)
        }
    }

    /**
     * Resolves an in-site search query to either the site's native search endpoint (extracted from
     * the site's own `<form>` in its original navbar) or a clean `site:domain` Google query.
     */
    fun resolveSiteSearchUrl(
        query: String,
        navData: SiteNavigationBarData,
        currentUrl: String
    ): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""
        val encoded = Uri.encode(trimmed)
        val template = navData.searchTemplate
        if (!template.isNullOrBlank()) {
            return if (template.contains("{query}")) {
                template.replace("{query}", encoded)
            } else if (template.contains("?")) {
                "$template&q=$encoded"
            } else {
                "$template?q=$encoded"
            }
        }
        val host = try {
            Uri.parse(currentUrl.ifBlank { navData.homeUrl }).host.orEmpty().removePrefix("www.")
        } catch (_: Throwable) {
            ""
        }
        return if (host.isNotBlank()) {
            "https://www.google.com/search?q=${Uri.encode("site:$host $trimmed")}"
        } else {
            "https://www.google.com/search?q=$encoded"
        }
    }

    /**
     * Injects the DOM navigation extractor + original website navbar suppressor into [webView].
     * When [replaceOriginalNav] is `true`, the website's own HTML navigation bars/headers are
     * cleanly hidden (`[data-glass-hidden-nav="1"]`), top spacing is normalized so our floating
     * `SiteGlassNavigationBar` never overlaps hero content, and SPA route mutations stay synced.
     * When `false`, the original website navigation bars are immediately restored.
     */
    fun injectSiteNavigationReplacement(webView: WebView, replaceOriginalNav: Boolean) {
        val replaceFlag = if (replaceOriginalNav) "true" else "false"
        val script = """
            (function() {
                try {
                    window.__glassReplaceSiteNav = $replaceFlag;

                    function hostPreservesOriginalNav(hostname) {
                        var h = String(hostname || '').toLowerCase().replace(/^www\./, '');
                        var keep = [
                            'youtube.com', 'youtu.be', 'youtube-nocookie.com',
                            'vimeo.com', 'twitch.tv', 'pinterest.com', 'reddit.com',
                            'instagram.com', 'x.com', 'twitter.com', 'tiktok.com', 'threads.net'
                        ];
                        for (var i = 0; i < keep.length; i++) {
                            var s = keep[i];
                            if (h === s || (h.length > s.length && h.slice(-(s.length + 1)) === '.' + s)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    function ensureNavKillerStyle(enabled) {
                        var existing = document.getElementById('glass-site-nav-killer');
                        if (!enabled) {
                            if (existing && existing.parentNode) {
                                existing.parentNode.removeChild(existing);
                            }
                            var hiddenEls = document.querySelectorAll('[data-glass-hidden-nav="1"]');
                            for (var h = 0; h < hiddenEls.length; h++) {
                                hiddenEls[h].removeAttribute('data-glass-hidden-nav');
                                hiddenEls[h].removeAttribute('data-glass-spacer-only');
                            }
                            if (document.body && document.body.getAttribute('data-glass-top-padded') === '1') {
                                document.body.style.removeProperty('padding-top');
                                document.body.style.removeProperty('padding-bottom');
                                document.body.style.removeProperty('margin-top');
                                document.body.removeAttribute('data-glass-top-padded');
                            }
                            var adjusted = document.querySelectorAll('[data-glass-spacer-adjusted="1"]');
                            for (var k = 0; k < adjusted.length; k++) {
                                adjusted[k].style.removeProperty('padding-top');
                                adjusted[k].style.removeProperty('margin-top');
                                adjusted[k].removeAttribute('data-glass-spacer-adjusted');
                            }
                            return;
                        }
                        if (!existing) {
                            var st = document.createElement('style');
                            st.id = 'glass-site-nav-killer';
                            st.textContent =
                                '[data-glass-hidden-nav="1"] {' +
                                '  display: none !important;' +
                                '  visibility: hidden !important;' +
                                '  opacity: 0 !important;' +
                                '  height: 0px !important;' +
                                '  min-height: 0px !important;' +
                                '  max-height: 0px !important;' +
                                '  margin-top: 0px !important;' +
                                '  margin-bottom: 0px !important;' +
                                '  padding-top: 0px !important;' +
                                '  padding-bottom: 0px !important;' +
                                '  border: 0 !important;' +
                                '  overflow: hidden !important;' +
                                '  pointer-events: none !important;' +
                                '}' +
                                'html { scroll-padding-top: 20px !important; scroll-padding-bottom: 132px !important; }';
                            (document.head || document.documentElement).appendChild(st);
                        }
                    }

                    function containsMainContent(el) {
                        if (!el || el === document.body || el === document.documentElement) return true;
                        var tag = (el.tagName || '').toUpperCase();
                        if (tag === 'MAIN' || tag === 'ARTICLE' || tag === 'SECTION' && el.querySelectorAll('p').length >= 3) {
                            return true;
                        }
                        var role = (el.getAttribute('role') || '').toLowerCase();
                        if (role === 'main' || role === 'article') return true;
                        if (el.querySelector('main, article, [role="main"], #main, #content, #main-content, #mw-content-text, #bodyContent, #rcnt, #search, video')) {
                            return true;
                        }
                        var paragraphs = el.getElementsByTagName('p');
                        if (paragraphs.length >= 4) {
                            var pLen = 0;
                            for (var i = 0; i < Math.min(paragraphs.length, 6); i++) {
                                pLen += (paragraphs[i].textContent || '').trim().length;
                            }
                            if (pLen > 380) return true;
                        }
                        return false;
                    }

                    function collectSiteNavContainers() {
                        var found = [];
                        function addUnique(node) {
                            if (!node || node.nodeType !== 1) return;
                            for (var i = 0; i < found.length; i++) {
                                if (found[i] === node || found[i].contains(node)) return;
                            }
                            found.push(node);
                        }

                        // Include any previously identified navbars even while hidden via display:none
                        var prevHidden = document.querySelectorAll('[data-glass-hidden-nav="1"]');
                        for (var p = 0; p < prevHidden.length; p++) {
                            if (prevHidden[p].getAttribute('data-glass-spacer-only') !== '1') {
                                addUnique(prevHidden[p]);
                            }
                        }

                        var scrollY = window.scrollY || window.pageYOffset || 0;
                        var vw = Math.max(window.innerWidth || 360, 320);
                        var vh = Math.max(window.innerHeight || 640, 480);

                        // 1. Classic table-based topbars (e.g., Hacker News .pagetop)
                        var pageTops = document.querySelectorAll('span.pagetop');
                        for (var pt = 0; pt < pageTops.length; pt++) {
                            var tr = pageTops[pt].closest('tr');
                            if (tr && !containsMainContent(tr)) {
                                addUnique(tr);
                            }
                        }

                        // 2. Semantic <nav> and [role="navigation"] near top or fixed/sticky
                        var navNodes = document.querySelectorAll('nav, [role="navigation"]');
                        for (var n = 0; n < navNodes.length; n++) {
                            var navEl = navNodes[n];
                            if (navEl.closest('footer, [role="contentinfo"]')) continue;
                            if (containsMainContent(navEl)) continue;
                            var csNav = window.getComputedStyle(navEl);
                            var rectNav = navEl.getBoundingClientRect();
                            var docTopNav = rectNav.top + scrollY;
                            var isFixedOrSticky = csNav.position === 'fixed' || csNav.position === 'sticky';
                            if (isFixedOrSticky || (docTopNav < 360 && rectNav.height <= 260)) {
                                addUnique(navEl);
                            }
                        }

                        // 3. Site-level <header>, [role="banner"], and explicit navbar classes/IDs
                        var headerSelectors = [
                            'header',
                            '[role="banner"]',
                            '#header', '#site-header', '#masthead', '#navbar', '#top-bar', '#topbar', '#global-nav',
                            '.site-header', '.global-header', '.main-header', '.navbar', '.nav-bar',
                            '.top-nav', '.topnav', '.topbar', '.header-bar', '.AppHeader',
                            '.vector-header-container', '.header-container', '.minerva-header'
                        ].join(',');
                        var hdrNodes = document.querySelectorAll(headerSelectors);
                        for (var h = 0; h < hdrNodes.length; h++) {
                            var hdr = hdrNodes[h];
                            if (hdr.closest('article, main, [role="main"], footer, [role="contentinfo"]')) continue;
                            if (containsMainContent(hdr)) continue;
                            var csHdr = window.getComputedStyle(hdr);
                            var rectHdr = hdr.getBoundingClientRect();
                            var docTopHdr = rectHdr.top + scrollY;
                            var fixedHdr = csHdr.position === 'fixed' || csHdr.position === 'sticky';
                            if (!fixedHdr && docTopHdr > 260) continue;
                            if (rectHdr.height > 260 && rectHdr.height > vh * 0.38) continue;

                            // Protect standalone article headline <header><h1>Long Headline</h1></header> without nav links
                            var h1 = hdr.querySelector('h1');
                            var linksInHdr = hdr.querySelectorAll('a[href]');
                            if (h1 && (h1.textContent || '').trim().length > 44 && linksInHdr.length < 2 && !hdr.querySelector('nav')) {
                                continue;
                            }
                            addUnique(hdr);
                        }

                        // 4. Modern React/Next/Tailwind fixed or sticky top wrapper divs without semantic tags
                        var rootContainers = [document.body];
                        var appRoots = document.querySelectorAll('#__next, #root, #app');
                        for (var r = 0; r < appRoots.length; r++) rootContainers.push(appRoots[r]);
                        for (var rc = 0; rc < rootContainers.length; rc++) {
                            var parentNode = rootContainers[rc];
                            if (!parentNode || !parentNode.children) continue;
                            for (var c = 0; c < Math.min(parentNode.children.length, 6); c++) {
                                var child = parentNode.children[c];
                                if (!child || child.id === 'glass-site-nav-killer') continue;
                                var csChild = window.getComputedStyle(child);
                                if (csChild.position !== 'fixed' && csChild.position !== 'sticky') continue;
                                var rChild = child.getBoundingClientRect();
                                if (rChild.top <= 48 &&
                                    rChild.width >= vw * 0.65 &&
                                    rChild.height >= 28 &&
                                    rChild.height <= 145 &&
                                    !containsMainContent(child) &&
                                    child.querySelectorAll('a[href], button').length >= 2
                                ) {
                                    addUnique(child);
                                }
                            }
                        }

                        return found;
                    }

                    function extractBrand(navContainers) {
                        var metaSite = document.querySelector('meta[property="og:site_name"], meta[name="application-name"]');
                        if (metaSite) {
                            var mc = (metaSite.getAttribute('content') || '').trim();
                            if (mc.length >= 2 && mc.length <= 24) return mc;
                        }
                        for (var i = 0; i < navContainers.length; i++) {
                            var brandEl = navContainers[i].querySelector('.hnname a, a[class*="brand"], a[class*="logo"], a[id*="logo"], header a[href="/"]');
                            if (brandEl) {
                                var bt = (brandEl.textContent || '').replace(/\s+/g, ' ').trim();
                                if (!bt) {
                                    var img = brandEl.querySelector('img[alt], svg[aria-label]');
                                    if (img) bt = (img.getAttribute('alt') || img.getAttribute('aria-label') || '').trim();
                                }
                                bt = bt.replace(/\s*(logo|home|homepage)$/i, '').trim();
                                if (bt.length >= 2 && bt.length <= 22) return bt;
                            }
                        }
                        var rawTitle = (document.title || '').trim();
                        if (rawTitle) {
                            var parts = rawTitle.split(/\s+[\|\-–—•:]\s+/);
                            if (parts.length > 1) {
                                var tail = parts[parts.length - 1].trim();
                                if (tail.length >= 2 && tail.length <= 22) return tail;
                            }
                        }
                        return '';
                    }

                    function extractHomeUrl(navContainers) {
                        var origin = (location.origin && location.origin !== 'null') ? location.origin : '';
                        for (var i = 0; i < navContainers.length; i++) {
                            var logoLink = navContainers[i].querySelector('.hnname a, a[class*="brand"][href], a[class*="logo"][href], a[rel="home"][href]');
                            if (logoLink) {
                                try {
                                    var resolved = new URL(logoLink.getAttribute('href'), location.href).href;
                                    if (resolved.indexOf('http') === 0) return resolved;
                                } catch (e) {}
                            }
                        }
                        return origin ? (origin + '/') : String(location.href || '');
                    }

                    function extractSearchTemplate(navContainers) {
                        var scopes = navContainers.slice();
                        scopes.push(document);
                        for (var s = 0; s < scopes.length; s++) {
                            var forms = scopes[s].querySelectorAll ? scopes[s].querySelectorAll('form') : [];
                            for (var f = 0; f < Math.min(forms.length, 6); f++) {
                                var form = forms[f];
                                var inp = form.querySelector('input[type="search"], input[name="q"], input[name="s"], input[name="search"], input[name="query"]');
                                if (!inp) continue;
                                var paramName = inp.getAttribute('name') || 'q';
                                var rawAction = form.getAttribute('action') || location.pathname || '/';
                                try {
                                    var actionUrl = new URL(rawAction, location.href);
                                    var sep = actionUrl.search ? '&' : '?';
                                    return actionUrl.href + sep + encodeURIComponent(paramName) + '={query}';
                                } catch (e) {}
                            }
                            if (s < navContainers.length) {
                                var sLink = scopes[s].querySelector('a[href*="/search"]');
                                if (sLink) {
                                    try {
                                        var su = new URL(sLink.getAttribute('href'), location.href).href;
                                        if (su.indexOf('http') === 0) return su + (su.indexOf('?') >= 0 ? '&q={query}' : '?q={query}');
                                    } catch (e2) {}
                                }
                            }
                        }
                        return '';
                    }

                    function cleanAnchorLabel(a) {
                        var txt = (a.textContent || '').replace(/\s+/g, ' ').trim();
                        if (!txt || txt.length > 30) {
                            var aria = (a.getAttribute('aria-label') || a.getAttribute('title') || '').replace(/\s+/g, ' ').trim();
                            if (aria && aria.length <= 28) txt = aria;
                        }
                        if (!txt) {
                            var img = a.querySelector('img[alt]');
                            if (img) txt = (img.getAttribute('alt') || '').replace(/\s+/g, ' ').trim();
                        }
                        txt = txt.replace(/^[\u2190-\u21FF\u25B6\u25BC\u2022\u00BB\u203A]+\s*/, '').trim();
                        return txt;
                    }

                    function extractNavItems(navContainers) {
                        var items = [];
                        var seenHref = {};
                        var seenLabel = {};
                        var skipRegex = /^(skip to|jump to|accessibility|cookie|privacy policy|terms of|advertise)/i;
                        var currentClean = String(location.href || '').replace(/\/$/, '');

                        function pushCandidate(label, rawHref, forceAnchor) {
                            if (!label || label.length < 2 || label.length > 26) return;
                            if (skipRegex.test(label)) return;
                            if (!rawHref) return;
                            var trimmedHref = rawHref.trim();
                            if (!trimmedHref || trimmedHref === '#' ||
                                trimmedHref.indexOf('javascript:') === 0 ||
                                trimmedHref.indexOf('mailto:') === 0 ||
                                trimmedHref.indexOf('tel:') === 0
                            ) return;

                            var fullUrl = '';
                            var isAnchor = !!forceAnchor;
                            try {
                                var u = new URL(trimmedHref, location.href);
                                if (u.protocol !== 'http:' && u.protocol !== 'https:') return;
                                if (u.origin === location.origin && u.pathname === location.pathname && u.hash && u.hash.length > 1) {
                                    if (/^#(content|main|main-content|top|skip)/i.test(u.hash)) return;
                                    isAnchor = true;
                                }
                                fullUrl = u.href;
                            } catch (err) {
                                return;
                            }

                            var normHref = fullUrl.replace(/\/$/, '');
                            var normLabel = label.toLowerCase();
                            if (seenHref[normHref] || seenLabel[normLabel]) return;
                            seenHref[normHref] = true;
                            seenLabel[normLabel] = true;

                            var active = (normHref === currentClean) ||
                                (aHasCurrentState && aHasCurrentState === fullUrl);

                            items.push({
                                label: label,
                                url: fullUrl,
                                isActive: !!active,
                                isAnchor: isAnchor
                            });
                        }

                        var aHasCurrentState = null;
                        for (var i = 0; i < navContainers.length; i++) {
                            var anchors = navContainers[i].querySelectorAll('a[href]');
                            for (var j = 0; j < anchors.length; j++) {
                                var a = anchors[j];
                                var label = cleanAnchorLabel(a);
                                var href = a.getAttribute('href');
                                var ariaCurrent = a.getAttribute('aria-current');
                                var cls = (a.className && typeof a.className === 'string') ? a.className : '';
                                if ((ariaCurrent && ariaCurrent !== 'false') || /\b(active|selected|current|is-active)\b/i.test(cls)) {
                                    try { aHasCurrentState = new URL(href, location.href).href; } catch (e) {}
                                }
                                pushCandidate(label, href, false);
                                if (items.length >= 14) break;
                            }
                            if (items.length >= 14) break;
                        }

                        // Fallback 1: If site had < 2 navbar links, build section anchors from page headings (h2, h3)
                        if (items.length < 2) {
                            var headings = document.querySelectorAll('main h2, article h2, #content h2, body h2, main h3');
                            for (var hIdx = 0; hIdx < headings.length && items.length < 10; hIdx++) {
                                var hd = headings[hIdx];
                                var hdText = (hd.textContent || '').replace(/\[edit\]/gi, '').replace(/\s+/g, ' ').trim();
                                if (hdText.length < 3 || hdText.length > 34) continue;
                                if (!hd.id) {
                                    hd.id = 'glass-sec-' + hIdx;
                                }
                                var shortLabel = hdText.length > 22 ? (hdText.slice(0, 20).trim() + '…') : hdText;
                                pushCandidate(shortLabel, '#' + hd.id, true);
                            }
                        }

                        // Fallback 2: Top concise internal links on the page so EVERY site has its own rich bar
                        if (items.length < 2) {
                            var allLinks = document.querySelectorAll('a[href]');
                            for (var lIdx = 0; lIdx < Math.min(allLinks.length, 45) && items.length < 10; lIdx++) {
                                var lnk = allLinks[lIdx];
                                if (lnk.closest('footer, [role="contentinfo"]')) continue;
                                var lbl = cleanAnchorLabel(lnk);
                                if (lbl.length >= 3 && lbl.length <= 20) {
                                    pushCandidate(lbl, lnk.getAttribute('href'), false);
                                }
                            }
                        }

                        return items;
                    }

                    function applyReplacementAndPublish() {
                        var navContainers = collectSiteNavContainers();
                        var brand = extractBrand(navContainers);
                        var homeUrl = extractHomeUrl(navContainers);
                        var searchTemplate = extractSearchTemplate(navContainers);
                        var items = extractNavItems(navContainers);

                        var preserveHostNav = hostPreservesOriginalNav(location.hostname);
                        var shouldReplace = !!window.__glassReplaceSiteNav && !preserveHostNav;
                        ensureNavKillerStyle(shouldReplace);

                        var hiddenCount = 0;
                        if (shouldReplace) {
                            for (var i = 0; i < navContainers.length; i++) {
                                var el = navContainers[i];
                                el.setAttribute('data-glass-hidden-nav', '1');
                                hiddenCount++;

                                // Hide empty fixed-header spacer sibling if present immediately after navbar
                                var nextSib = el.nextElementSibling;
                                if (nextSib && !containsMainContent(nextSib)) {
                                    var sibTxt = (nextSib.textContent || '').trim();
                                    var sibRect = nextSib.getBoundingClientRect();
                                    if (sibTxt === '' &&
                                        !nextSib.querySelector('img, svg, canvas, video, iframe, input') &&
                                        sibRect.height >= 28 && sibRect.height <= 140
                                    ) {
                                        nextSib.setAttribute('data-glass-hidden-nav', '1');
                                        nextSib.setAttribute('data-glass-spacer-only', '1');
                                    }
                                }
                            }

                            // Normalize leftover top offset on root wrapper and apply clean bottom clearance
                            // so our bottom-docked custom per-site navigation bar never blocks page footers
                            if (document.body) {
                                document.body.style.setProperty('padding-top', '0px', 'important');
                                document.body.style.setProperty('padding-bottom', '132px', 'important');
                                document.body.style.setProperty('margin-top', '0px', 'important');
                                document.body.setAttribute('data-glass-top-padded', '1');

                                var firstWrap = document.querySelector('main, #__next > div:first-child, #root > div:first-child, #app > div:first-child');
                                if (firstWrap && firstWrap !== document.body && firstWrap.getAttribute('data-glass-hidden-nav') !== '1') {
                                    var csWrap = window.getComputedStyle(firstWrap);
                                    var pt = parseFloat(csWrap.paddingTop || '0');
                                    var mt = parseFloat(csWrap.marginTop || '0');
                                    if (pt >= 46 && pt <= 160) {
                                        firstWrap.style.setProperty('padding-top', '8px', 'important');
                                        firstWrap.setAttribute('data-glass-spacer-adjusted', '1');
                                    }
                                    if (mt >= 46 && mt <= 160) {
                                        firstWrap.style.setProperty('margin-top', '0px', 'important');
                                        firstWrap.setAttribute('data-glass-spacer-adjusted', '1');
                                    }
                                }
                            }
                        }

                        var payload = JSON.stringify({
                            origin: (location.origin && location.origin !== 'null') ? location.origin : '',
                            pageUrl: String(location.href || ''),
                            brand: brand,
                            homeUrl: homeUrl,
                            searchTemplate: searchTemplate,
                            hiddenCount: hiddenCount,
                            items: items
                        });

                        if (payload !== window.__glassLastSiteNavPayload) {
                            window.__glassLastSiteNavPayload = payload;
                            if (window.GlassVideoBridge && window.GlassVideoBridge.onSiteNavigation) {
                                window.GlassVideoBridge.onSiteNavigation(payload);
                            }
                        }
                    }

                    window.__glassApplySiteNav = applyReplacementAndPublish;
                    applyReplacementAndPublish();

                    if (!window.__glassSiteNavObserverInstalled) {
                        window.__glassSiteNavObserverInstalled = true;
                        var mutTimer = null;
                        function scheduleNavSync() {
                            if (mutTimer) clearTimeout(mutTimer);
                            mutTimer = setTimeout(function() {
                                if (window.__glassApplySiteNav) window.__glassApplySiteNav();
                            }, 280);
                        }
                        if (window.MutationObserver && document.documentElement) {
                            var obs = new MutationObserver(scheduleNavSync);
                            obs.observe(document.documentElement, { childList: true, subtree: true });
                        }
                        window.addEventListener('popstate', scheduleNavSync, { passive: true });
                        window.addEventListener('hashchange', scheduleNavSync, { passive: true });
                    }
                } catch (navErr) {}
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(script, null)
        } catch (_: Throwable) {}
    }

    /**
     * Navigates or smoothly scrolls to [item] inside [webView].
     * If [item] points to an in-page `#anchor` on the current document, smoothly scrolls the
     * target section right below our floating Glass Site Navigation Bar; otherwise loads the URL.
     */
    fun navigateOrScrollItem(webView: WebView, item: SiteNavItem) {
        val currentUrl = webView.url.orEmpty()
        if (item.isAnchor || isSameDocumentAnchor(currentUrl, item.url)) {
            val hash = item.url.substringAfter('#', "").trim()
            if (hash.isNotEmpty()) {
                val quotedHash = JSONObject.quote(hash)
                val scrollJs = """
                    (function() {
                        try {
                            var id = decodeURIComponent($quotedHash);
                            var target = document.getElementById(id) || document.getElementsByName(id)[0];
                            if (target) {
                                var rect = target.getBoundingClientRect();
                                var destY = Math.max(0, (window.scrollY || window.pageYOffset || 0) + rect.top - 24);
                                window.scrollTo({ top: destY, behavior: 'smooth' });
                                if (history && history.replaceState) {
                                    history.replaceState(null, '', '#' + $quotedHash);
                                }
                                if (window.__glassApplySiteNav) window.__glassApplySiteNav();
                                return;
                            }
                            location.hash = $quotedHash;
                        } catch (e) {}
                    })();
                """.trimIndent()
                try {
                    webView.evaluateJavascript(scrollJs, null)
                    return
                } catch (_: Throwable) {}
            }
        }
        webView.loadUrl(item.url)
    }

    private fun deriveBrandAndMonogram(cleanHost: String, explicitBrand: String?): Pair<String, String> {
        val matchedKnown = KNOWN_BRAND_MAP.entries.firstOrNull { (domainSuffix, _) ->
            cleanHost == domainSuffix || cleanHost.endsWith(".$domainSuffix")
        }?.value

        if (!explicitBrand.isNullOrBlank() && explicitBrand.length in 2..22) {
            val mono = matchedKnown?.second ?: buildMonogram(explicitBrand)
            return explicitBrand to mono
        }
        if (matchedKnown != null) return matchedKnown

        val parts = cleanHost
            .removePrefix("m.")
            .removePrefix("en.m.")
            .removePrefix("en.")
            .split('.')
            .filter { it.isNotBlank() }

        val core = when {
            parts.size >= 3 && parts[parts.size - 2].length <= 3 -> parts[parts.size - 3]
            parts.size >= 2 -> parts[parts.size - 2]
            parts.isNotEmpty() -> parts.first()
            else -> "Site"
        }
        val prettyBrand = core
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .ifBlank { "Website" }

        return prettyBrand to buildMonogram(prettyBrand)
    }

    private fun buildMonogram(brand: String): String {
        val words = brand.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.US)
            brand.length >= 2 -> brand.take(2).uppercase(Locale.US)
            brand.isNotEmpty() -> brand.take(1).uppercase(Locale.US)
            else -> "W"
        }
    }

    private fun humanizeSegment(rawSegment: String): String {
        val decoded = try {
            Uri.decode(rawSegment)
        } catch (_: Throwable) {
            rawSegment
        }
        val stripped = decoded
            .substringBeforeLast(".html")
            .substringBeforeLast(".htm")
            .substringBeforeLast(".php")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        if (stripped.isBlank() || stripped.all { it.isDigit() }) return ""
        val titled = stripped.split(Regex("\\s+"))
            .take(3)
            .joinToString(" ") { w ->
                w.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
        return if (titled.length > 22) titled.take(20).trim() + "…" else titled
    }

    private fun sanitizeNavLabel(raw: String): String {
        val compact = raw.replace(Regex("\\s+"), " ").trim()
        return if (compact.length > 24) compact.take(22).trim() + "…" else compact
    }

    private fun normalizeComparableUrl(url: String): String {
        return url.trim()
            .removeSuffix("/")
            .lowercase(Locale.US)
    }

    private fun isSameDocumentAnchor(currentUrl: String, candidateUrl: String): Boolean {
        if (candidateUrl.startsWith("#")) return candidateUrl.length > 1
        if (!candidateUrl.contains('#') || currentUrl.isBlank()) return false
        val baseCurrent = currentUrl.substringBefore('#').removeSuffix("/")
        val baseCandidate = candidateUrl.substringBefore('#').removeSuffix("/")
        return baseCurrent.equals(baseCandidate, ignoreCase = true)
    }
}
