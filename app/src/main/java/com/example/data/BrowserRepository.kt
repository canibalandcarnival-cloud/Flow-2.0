package com.example.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

class BrowserRepository(
    private val dao: BrowserDao,
    private val preferences: BrowserPreferences
) {
    val favorites: Flow<List<FavoriteSiteEntity>> = dao.observeFavorites()
    val readingList: Flow<List<ReadingListItemEntity>> = dao.observeReadingList()
    val bookmarks: Flow<List<BookmarkEntity>> = dao.observeBookmarks()
    val history: Flow<List<HistoryEntryEntity>> = dao.observeHistory()
    val tabs: Flow<List<BrowserTabEntity>> = dao.observeTabs()
    val customWallpapers: Flow<List<CustomWallpaperEntity>> = dao.observeCustomWallpapers()
    val settings: Flow<BrowserSettingsState> = preferences.settingsFlow

    suspend fun ensureSeeded(): Long {
        if (dao.countFavorites() == 0) {
            dao.insertFavorites(
                listOf(
                    FavoriteSiteEntity(
                        title = "Curious and Wondrous Travels",
                        url = "https://www.atlasobscura.com",
                        iconType = "GEOMETRIC_A",
                        monogram = "A",
                        bgStyle = "DARK_ESPRESSO",
                        sortOrder = 0
                    ),
                    FavoriteSiteEntity(
                        title = "Beti-Jai: an historical fronton",
                        url = "https://www.archdaily.com",
                        iconType = "CC_SLASH",
                        monogram = "CC/",
                        bgStyle = "SOFT_PRISM",
                        sortOrder = 1
                    ),
                    FavoriteSiteEntity(
                        title = "Five Design-Driven Portfolios",
                        url = "https://www.surfacemag.com",
                        iconType = "BOLD_S",
                        monogram = "S",
                        bgStyle = "PURE_BLACK",
                        sortOrder = 2
                    ),
                    FavoriteSiteEntity(
                        title = "Mush Studios | Home Decor",
                        url = "https://www.dezeen.com/interiors",
                        iconType = "MONO_M",
                        monogram = "M",
                        bgStyle = "SLATE_GRAY",
                        sortOrder = 3
                    ),
                    FavoriteSiteEntity(
                        title = "Rit Dye – Fabric Dye Studio",
                        url = "https://www.ritdye.com",
                        iconType = "DROPLET",
                        monogram = "R",
                        bgStyle = "BLUSH_LIGHT",
                        sortOrder = 4
                    ),
                    FavoriteSiteEntity(
                        title = "Pinterest",
                        url = "https://www.pinterest.com",
                        iconType = "PIN_P",
                        monogram = "P",
                        bgStyle = "PURE_WHITE",
                        sortOrder = 5
                    ),
                    FavoriteSiteEntity(
                        title = "KOBU | Creative Studio",
                        url = "https://www.awwwards.com",
                        iconType = "MONO_K",
                        monogram = "K",
                        bgStyle = "SILVER_GRAY",
                        sortOrder = 6
                    ),
                    FavoriteSiteEntity(
                        title = "Process / Era Ceramics",
                        url = "https://www.dezeen.com/design",
                        iconType = "EC_MARK",
                        monogram = "EC",
                        bgStyle = "PURE_BLACK",
                        sortOrder = 7
                    ),
                    FavoriteSiteEntity(
                        title = "Wikipedia Encyclopedia",
                        url = "https://en.m.wikipedia.org",
                        iconType = "MONOGRAM",
                        monogram = "W",
                        bgStyle = "PURE_WHITE",
                        sortOrder = 8
                    ),
                    FavoriteSiteEntity(
                        title = "GitHub Open Source",
                        url = "https://github.com",
                        iconType = "MONOGRAM",
                        monogram = "GH",
                        bgStyle = "PURE_BLACK",
                        sortOrder = 9
                    ),
                    FavoriteSiteEntity(
                        title = "Hacker News",
                        url = "https://news.ycombinator.com",
                        iconType = "MONOGRAM",
                        monogram = "Y",
                        bgStyle = "SLATE_GRAY",
                        sortOrder = 10
                    ),
                    FavoriteSiteEntity(
                        title = "Are.na Research",
                        url = "https://www.are.na",
                        iconType = "MONOGRAM",
                        monogram = "**",
                        bgStyle = "SILVER_GRAY",
                        sortOrder = 11
                    )
                )
            )
        }

        if (dao.countReadingList() == 0) {
            val now = System.currentTimeMillis()
            dao.insertReadingListItems(
                listOf(
                    ReadingListItemEntity(
                        title = "Process / Era Ceramics",
                        excerpt = "Ceramics and dinnerware for the modern sculptural table.",
                        domain = "eraceramics.com",
                        url = "https://www.dezeen.com/tag/ceramics/",
                        artworkStyle = "CERAMIC_CUBE",
                        addedAt = now
                    ),
                    ReadingListItemEntity(
                        title = "Wallflower | San Francisco Floral Design Studio – wallflower",
                        excerpt = "San Francisco's favorite floral studio, botanical works and vessels.",
                        domain = "wallflowersanfrancisco.com",
                        url = "https://www.architecturaldigest.com",
                        artworkStyle = "BOTANICAL_FLORAL",
                        addedAt = now - 60_000L
                    ),
                    ReadingListItemEntity(
                        title = "Jean-Michel Othoniel Explains the Hypnotic Potential of Flowing Glass",
                        excerpt = "The French artist recasts the Brooklyn Botanic Garden with luminous beads.",
                        domain = "surfacemag.com",
                        url = "https://www.surfacemag.com",
                        artworkStyle = "BOLD_S_MONO",
                        addedAt = now - 120_000L
                    ),
                    ReadingListItemEntity(
                        title = "Architecture of Light & Translucent Surfaces",
                        excerpt = "How frosted glass and spatial blur shape calm modern interfaces.",
                        domain = "archdaily.com",
                        url = "https://www.archdaily.com",
                        artworkStyle = "GLASS_ARCH",
                        addedAt = now - 180_000L
                    ),
                    ReadingListItemEntity(
                        title = "Swiss Grid Systems in Digital Editorial Typography",
                        excerpt = "Precision spacing, high-contrast monochrome hierarchy, and rhythm.",
                        domain = "awwwards.com",
                        url = "https://www.awwwards.com",
                        artworkStyle = "TYPO_GRID",
                        addedAt = now - 240_000L
                    )
                )
            )
        }

        if (dao.countBookmarks() == 0) {
            dao.insertBookmarks(
                listOf(
                    BookmarkEntity(
                        title = "Wikipedia — Free Encyclopedia",
                        url = "https://en.m.wikipedia.org",
                        folder = "Reference"
                    ),
                    BookmarkEntity(
                        title = "MDN Web Docs — Web Standards",
                        url = "https://developer.mozilla.org",
                        folder = "Development"
                    ),
                    BookmarkEntity(
                        title = "Dezeen — Architecture & Design Magazine",
                        url = "https://www.dezeen.com",
                        folder = "Design"
                    ),
                    BookmarkEntity(
                        title = "DuckDuckGo Privacy Search",
                        url = "https://duckduckgo.com",
                        folder = "Search"
                    )
                )
            )
        }

        if (dao.countTabs() == 0) {
            val now = System.currentTimeMillis()
            val startTabId = dao.insertTab(
                BrowserTabEntity(
                    title = "Start Page",
                    url = "",
                    isPrivate = false,
                    groupName = null,
                    previewHeadline = "Favorites & Reading List",
                    previewSubhead = "Start Page",
                    lastAccessedAt = now + 5000L
                )
            )
            dao.insertTabs(
                listOf(
                    BrowserTabEntity(
                        title = "Europe's first Developer Center to open in Berlin",
                        url = "https://developer.mozilla.org",
                        isPrivate = false,
                        groupName = null,
                        previewHeadline = "Europe's first Developer Center to open in Berlin",
                        previewSubhead = "The new state-of-the-art facility offers tools, workshops, and labs.",
                        lastAccessedAt = now + 4000L
                    ),
                    BrowserTabEntity(
                        title = "Minimalist Spatial Interfaces",
                        url = "https://www.dezeen.com",
                        isPrivate = false,
                        groupName = "Design",
                        previewHeadline = "Spatial Glass Design",
                        previewSubhead = "Architectural transparency in modern interfaces.",
                        lastAccessedAt = now + 3000L
                    ),
                    BrowserTabEntity(
                        title = "Surface Magazine — Studio Profiles",
                        url = "https://www.surfacemag.com",
                        isPrivate = false,
                        groupName = "Design",
                        previewHeadline = "Studio Profiles",
                        previewSubhead = "Contemporary industrial craft and materials.",
                        lastAccessedAt = now + 2500L
                    ),
                    BrowserTabEntity(
                        title = "ArchDaily — Glass Pavilions",
                        url = "https://www.archdaily.com",
                        isPrivate = false,
                        groupName = "Design",
                        previewHeadline = "Glass Pavilions",
                        previewSubhead = "Daylight refraction and structural minimalism.",
                        lastAccessedAt = now + 2000L
                    ),
                    BrowserTabEntity(
                        title = "Awwwards — Interactive Motion",
                        url = "https://www.awwwards.com",
                        isPrivate = false,
                        groupName = "Design",
                        previewHeadline = "Interactive Motion",
                        previewSubhead = "Fluid physics, momentum, and tactile feedback.",
                        lastAccessedAt = now + 1500L
                    )
                )
            )
            preferences.setActiveTab(startTabId, isPrivate = false)
            return startTabId
        }
        return -1L
    }

    suspend fun createNewTab(isPrivate: Boolean, initialUrl: String = "", groupName: String? = null): Long {
        val domain = extractDomain(initialUrl)
        val tab = BrowserTabEntity(
            title = if (initialUrl.isBlank()) "Start Page" else domain,
            url = initialUrl,
            isPrivate = isPrivate,
            groupName = groupName,
            previewHeadline = if (initialUrl.isBlank()) "Start Page" else domain,
            previewSubhead = if (initialUrl.isBlank()) "Favorites & Reading List" else initialUrl,
            lastAccessedAt = System.currentTimeMillis()
        )
        val newId = dao.insertTab(tab)
        preferences.setActiveTab(newId, isPrivate)
        return newId
    }

    suspend fun selectTab(tab: BrowserTabEntity) {
        dao.updateTab(tab.copy(lastAccessedAt = System.currentTimeMillis()))
        preferences.setActiveTab(tab.id, tab.isPrivate)
    }

    suspend fun updateTabNavigation(tabId: Long, url: String, title: String?) {
        val existing = dao.getTabById(tabId) ?: return
        val cleanTitle = when {
            url.isBlank() -> "Start Page"
            !title.isNullOrBlank() -> title.trim()
            else -> extractDomain(url)
        }
        dao.updateTab(
            existing.copy(
                url = url,
                title = cleanTitle,
                previewHeadline = cleanTitle,
                previewSubhead = if (url.isBlank()) "Favorites & Reading List" else extractDomain(url),
                lastAccessedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun assignTabToGroup(tabId: Long, groupName: String?) {
        val existing = dao.getTabById(tabId) ?: return
        dao.updateTab(existing.copy(groupName = groupName))
    }

    suspend fun closeTab(tabId: Long) {
        dao.deleteTabById(tabId)
    }

    suspend fun closeAllTabs(isPrivate: Boolean): Long {
        dao.deleteAllTabsByMode(isPrivate)
        return createNewTab(isPrivate = isPrivate, initialUrl = "")
    }

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank()) return
        dao.insertHistoryEntry(
            HistoryEntryEntity(
                title = title.ifBlank { extractDomain(url) },
                url = url,
                domain = extractDomain(url),
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = dao.clearAllHistory()

    suspend fun addFavorite(title: String, url: String) {
        val cleanTitle = title.ifBlank { extractDomain(url) }
        val monogram = cleanTitle.trim().take(2).uppercase().ifBlank { "W" }
        dao.insertFavorite(
            FavoriteSiteEntity(
                title = cleanTitle,
                url = normalizeUrlOrSearch(url),
                iconType = "MONOGRAM",
                monogram = monogram,
                bgStyle = "PURE_BLACK",
                sortOrder = 99
            )
        )
    }

    suspend fun removeFavorite(id: Long) = dao.deleteFavoriteById(id)

    suspend fun addReadingListItem(title: String, url: String) {
        val normalized = normalizeUrlOrSearch(url)
        val domain = extractDomain(normalized)
        dao.insertReadingListItem(
            ReadingListItemEntity(
                title = title.ifBlank { domain },
                excerpt = "Saved from $domain for distraction-free reading.",
                domain = domain,
                url = normalized,
                artworkStyle = "GLASS_ARCH",
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeReadingListItem(id: Long) = dao.deleteReadingListById(id)

    suspend fun addBookmark(title: String, url: String) {
        val normalized = normalizeUrlOrSearch(url)
        dao.insertBookmark(
            BookmarkEntity(
                title = title.ifBlank { extractDomain(normalized) },
                url = normalized
            )
        )
    }

    suspend fun removeBookmark(id: Long) = dao.deleteBookmarkById(id)

    suspend fun addCustomWallpaper(title: String, file: File, isLight: Boolean): String {
        val id = dao.insertCustomWallpaper(
            CustomWallpaperEntity(
                title = title,
                filePath = file.absolutePath,
                isLight = isLight
            )
        )
        val wpKey = "custom:$id"
        preferences.setSelectedWallpaper(wpKey)
        return wpKey
    }

    suspend fun deleteCustomWallpaper(entity: CustomWallpaperEntity, currentSelectedId: String) {
        try {
            File(entity.filePath).delete()
        } catch (_: Throwable) {}
        dao.deleteCustomWallpaperById(entity.id)
        if (currentSelectedId == "custom:${entity.id}") {
            preferences.setSelectedWallpaper("preset:forest")
        }
    }

    suspend fun setDarkMode(dark: Boolean) = preferences.setDarkMode(dark)
    suspend fun setSelectedWallpaper(id: String) = preferences.setSelectedWallpaper(id)
    suspend fun setSfxEnabled(enabled: Boolean) = preferences.setSfxEnabled(enabled)
    suspend fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)
    suspend fun setHighRefreshRateEnabled(enabled: Boolean) = preferences.setHighRefreshRateEnabled(enabled)
    suspend fun setPrivateMode(isPrivate: Boolean, fallbackTabId: Long) =
        preferences.setActiveTab(fallbackTabId, isPrivate)

    companion object {
        fun normalizeUrlOrSearch(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return trimmed
            }
            val looksLikeDomain = !trimmed.contains(' ') &&
                trimmed.contains('.') &&
                trimmed.substringAfterLast('.').length >= 2
            return if (looksLikeDomain) {
                "https://$trimmed"
            } else {
                "https://duckduckgo.com/?q=${Uri.encode(trimmed)}"
            }
        }

        fun extractDomain(url: String): String {
            if (url.isBlank()) return "Start Page"
            return try {
                val host = Uri.parse(url).host.orEmpty()
                host.removePrefix("www.").ifBlank { url }
            } catch (_: Throwable) {
                url
            }
        }
    }
}
