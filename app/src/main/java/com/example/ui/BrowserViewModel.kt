package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.FeedbackManager
import com.example.core.ResolvedWallpaper
import com.example.core.WallpaperLuminanceAnalyzer
import com.example.data.BookmarkEntity
import com.example.data.BrowserDatabase
import com.example.data.BrowserPreferences
import com.example.data.BrowserRepository
import com.example.data.BrowserSettingsState
import com.example.data.BrowserTabEntity
import com.example.data.CustomWallpaperEntity
import com.example.data.FavoriteSiteEntity
import com.example.data.HistoryEntryEntity
import com.example.data.ReadingListItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TopHubSection {
    START_PAGE,
    BOOKMARKS,
    READING_LIST,
    HISTORY
}

data class TabGroupSummary(
    val groupName: String,
    val tabs: List<BrowserTabEntity>
)

data class BrowserUiState(
    val isReady: Boolean = false,
    val settings: BrowserSettingsState = BrowserSettingsState(),
    val resolvedWallpaper: ResolvedWallpaper? = null,
    val favorites: List<FavoriteSiteEntity> = emptyList(),
    val readingList: List<ReadingListItemEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntryEntity> = emptyList(),
    val allTabs: List<BrowserTabEntity> = emptyList(),
    val customWallpapers: List<CustomWallpaperEntity> = emptyList(),
    val activeSection: TopHubSection = TopHubSection.START_PAGE,
    val showAllFavorites: Boolean = false,
    val showAllReadingList: Boolean = false,
    val isTabSwitcherOpen: Boolean = false,
    val isAddressOverlayOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val activeTabGroupFilter: String? = null,
    val tabSearchQuery: String = "",
    val isTabSearchActive: Boolean = false,
    val isLoadingWebPage: Boolean = false,
    val webLoadProgress: Float = 0f,
    val canWebGoBack: Boolean = false,
    val canWebGoForward: Boolean = false,
    val statusToast: String? = null
) {
    val activeTab: BrowserTabEntity?
        get() = allTabs.firstOrNull { it.id == settings.activeTabId }
            ?: allTabs.firstOrNull { it.isPrivate == settings.isPrivateMode }
            ?: allTabs.firstOrNull()

    val isViewingWebPage: Boolean
        get() = !activeTab?.url.isNullOrBlank()

    val modeTabs: List<BrowserTabEntity>
        get() = allTabs.filter { it.isPrivate == settings.isPrivateMode }

    val standardTabCount: Int
        get() = allTabs.count { !it.isPrivate }

    val privateTabCount: Int
        get() = allTabs.count { it.isPrivate }

    /**
     * Favorites site label color strictly depends on the wallpaper luminance:
     * - Dark wallpaper -> Light (White) label
     * - Light wallpaper -> Dark (Black) label
     */
    val isWallpaperLight: Boolean
        get() = resolvedWallpaper?.isLightWallpaper ?: false
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BrowserDatabase.getInstance(application)
    private val preferences = BrowserPreferences(application)
    private val repository = BrowserRepository(database.browserDao(), preferences)
    val feedback = FeedbackManager(application)

    // In-memory live webpage thumbnail snapshots keyed by tabId
    val tabSnapshots = mutableStateMapOf<Long, ImageBitmap>()

    // Trigger counter for WebView actions (reload / back / forward)
    private val _webCommand = MutableStateFlow<WebCommand?>(null)
    val webCommand: StateFlow<WebCommand?> = _webCommand.asStateFlow()

    sealed interface WebCommand {
        data object GoBack : WebCommand
        data object GoForward : WebCommand
        data object Reload : WebCommand
        data class LoadUrl(val url: String, val nonce: Long = System.nanoTime()) : WebCommand
    }

    private data class TransientUiState(
        val activeSection: TopHubSection = TopHubSection.START_PAGE,
        val showAllFavorites: Boolean = false,
        val showAllReadingList: Boolean = false,
        val isTabSwitcherOpen: Boolean = false,
        val isAddressOverlayOpen: Boolean = false,
        val isSettingsSheetOpen: Boolean = false,
        val activeTabGroupFilter: String? = null,
        val tabSearchQuery: String = "",
        val isTabSearchActive: Boolean = false,
        val isLoadingWebPage: Boolean = false,
        val webLoadProgress: Float = 0f,
        val canWebGoBack: Boolean = false,
        val canWebGoForward: Boolean = false,
        val statusToast: String? = null
    )

    private val transientState = MutableStateFlow(TransientUiState())
    private val resolvedWallpaperFlow = MutableStateFlow<ResolvedWallpaper?>(null)
    private val seededFlow = MutableStateFlow(false)

    val uiState: StateFlow<BrowserUiState> = combine(
        repository.settings,
        repository.favorites,
        repository.readingList,
        repository.bookmarks,
        repository.history,
        repository.tabs,
        repository.customWallpapers,
        resolvedWallpaperFlow,
        transientState,
        seededFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as BrowserSettingsState
        @Suppress("UNCHECKED_CAST")
        val favorites = values[1] as List<FavoriteSiteEntity>
        @Suppress("UNCHECKED_CAST")
        val readingList = values[2] as List<ReadingListItemEntity>
        @Suppress("UNCHECKED_CAST")
        val bookmarks = values[3] as List<BookmarkEntity>
        @Suppress("UNCHECKED_CAST")
        val history = values[4] as List<HistoryEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val tabs = values[5] as List<BrowserTabEntity>
        @Suppress("UNCHECKED_CAST")
        val customWps = values[6] as List<CustomWallpaperEntity>
        val resolvedWp = values[7] as ResolvedWallpaper?
        val transient = values[8] as TransientUiState
        val isSeeded = values[9] as Boolean

        feedback.sfxEnabled = settings.sfxEnabled
        feedback.hapticsEnabled = settings.hapticsEnabled

        BrowserUiState(
            isReady = isSeeded,
            settings = settings,
            resolvedWallpaper = resolvedWp,
            favorites = favorites,
            readingList = readingList,
            bookmarks = bookmarks,
            history = history,
            allTabs = tabs,
            customWallpapers = customWps,
            activeSection = transient.activeSection,
            showAllFavorites = transient.showAllFavorites,
            showAllReadingList = transient.showAllReadingList,
            isTabSwitcherOpen = transient.isTabSwitcherOpen,
            isAddressOverlayOpen = transient.isAddressOverlayOpen,
            isSettingsSheetOpen = transient.isSettingsSheetOpen,
            activeTabGroupFilter = transient.activeTabGroupFilter,
            tabSearchQuery = transient.tabSearchQuery,
            isTabSearchActive = transient.isTabSearchActive,
            isLoadingWebPage = transient.isLoadingWebPage,
            webLoadProgress = transient.webLoadProgress,
            canWebGoBack = transient.canWebGoBack,
            canWebGoForward = transient.canWebGoForward,
            statusToast = transient.statusToast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            seededFlow.value = true
        }
        viewModelScope.launch {
            combine(
                repository.settings,
                repository.customWallpapers
            ) { settings, customList ->
                val wpId = settings.selectedWallpaperId
                val customPath = if (wpId.startsWith("custom:")) {
                    val cid = wpId.removePrefix("custom:").toLongOrNull()
                    customList.firstOrNull { it.id == cid }?.filePath
                } else {
                    null
                }
                wpId to customPath
            }.collect { (wpId, customPath) ->
                if (resolvedWallpaperFlow.value?.id != wpId || customPath != null) {
                    val loaded = WallpaperLuminanceAnalyzer.loadAndAnalyze(
                        context = getApplication(),
                        wallpaperId = wpId,
                        customFilePath = customPath
                    )
                    if (loaded != null) {
                        resolvedWallpaperFlow.value = loaded
                    }
                }
            }
        }
    }

    fun selectTopSection(section: TopHubSection) {
        feedback.onSegmentSwitch()
        transientState.update { it.copy(activeSection = section) }
    }

    fun toggleShowAllFavorites() {
        feedback.onGlassTap()
        transientState.update { it.copy(showAllFavorites = !it.showAllFavorites) }
    }

    fun toggleShowAllReadingList() {
        feedback.onGlassTap()
        transientState.update { it.copy(showAllReadingList = !it.showAllReadingList) }
    }

    fun openTabSwitcher() {
        feedback.onTabZoom(opening = true)
        transientState.update {
            it.copy(
                isTabSwitcherOpen = true,
                isAddressOverlayOpen = false,
                activeTabGroupFilter = null,
                isTabSearchActive = false,
                tabSearchQuery = ""
            )
        }
    }

    fun closeTabSwitcher() {
        feedback.onTabZoom(opening = false)
        transientState.update {
            it.copy(
                isTabSwitcherOpen = false,
                activeTabGroupFilter = null,
                isTabSearchActive = false,
                tabSearchQuery = ""
            )
        }
    }

    fun openAddressOverlay() {
        feedback.onGlassTap()
        transientState.update { it.copy(isAddressOverlayOpen = true) }
    }

    fun closeAddressOverlay() {
        transientState.update { it.copy(isAddressOverlayOpen = false) }
    }

    fun setSettingsSheetOpen(open: Boolean) {
        feedback.onGlassTap()
        transientState.update { it.copy(isSettingsSheetOpen = open) }
    }

    fun openUrlOrSearch(rawInput: String) {
        val normalized = BrowserRepository.normalizeUrlOrSearch(rawInput)
        if (normalized.isBlank()) return
        feedback.onGlassTap()
        transientState.update {
            it.copy(
                isAddressOverlayOpen = false,
                isTabSwitcherOpen = false
            )
        }
        viewModelScope.launch {
            val current = uiState.value.activeTab
            if (current == null) {
                repository.createNewTab(
                    isPrivate = uiState.value.settings.isPrivateMode,
                    initialUrl = normalized
                )
            } else {
                repository.updateTabNavigation(current.id, normalized, null)
                _webCommand.value = WebCommand.LoadUrl(normalized)
            }
        }
    }

    fun returnToStartPage() {
        feedback.onGlassTap()
        viewModelScope.launch {
            val current = uiState.value.activeTab ?: return@launch
            repository.updateTabNavigation(current.id, "", "Start Page")
            transientState.update {
                it.copy(
                    canWebGoBack = false,
                    canWebGoForward = false,
                    isLoadingWebPage = false,
                    webLoadProgress = 0f
                )
            }
        }
    }

    /**
     * Invoked when the phone's physical/gesture Home action dispatches CATEGORY_HOME to our OS shell.
     */
    fun onSystemHomeAction() {
        transientState.update {
            it.copy(
                isTabSwitcherOpen = false,
                isAddressOverlayOpen = false,
                isSettingsSheetOpen = false,
                activeSection = TopHubSection.START_PAGE
            )
        }
        val active = uiState.value.activeTab
        if (active != null && active.url.isNotBlank()) {
            returnToStartPage()
        }
    }

    fun onBackButtonClicked() {
        feedback.onGlassTap()
        val state = uiState.value
        when {
            state.isAddressOverlayOpen -> closeAddressOverlay()
            state.isSettingsSheetOpen -> setSettingsSheetOpen(false)
            state.isTabSwitcherOpen -> {
                if (state.activeTabGroupFilter != null) {
                    transientState.update { it.copy(activeTabGroupFilter = null) }
                } else {
                    closeTabSwitcher()
                }
            }
            state.isViewingWebPage -> {
                if (state.canWebGoBack) {
                    _webCommand.value = WebCommand.GoBack
                } else {
                    returnToStartPage()
                }
            }
            state.activeSection != TopHubSection.START_PAGE -> {
                transientState.update { it.copy(activeSection = TopHubSection.START_PAGE) }
            }
        }
    }

    fun consumeWebCommand() {
        _webCommand.value = null
    }

    fun reloadCurrentPage() {
        feedback.onGlassTap()
        _webCommand.value = WebCommand.Reload
    }

    fun onWebPageStarted(tabId: Long, url: String) {
        transientState.update { it.copy(isLoadingWebPage = true, webLoadProgress = 0.12f) }
        viewModelScope.launch {
            repository.updateTabNavigation(tabId, url, null)
        }
    }

    fun onWebProgressChanged(progress: Int) {
        val frac = (progress / 100f).coerceIn(0f, 1f)
        transientState.update {
            it.copy(
                webLoadProgress = frac,
                isLoadingWebPage = progress < 100
            )
        }
    }

    fun onWebPageFinished(
        tabId: Long,
        url: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        isPrivate: Boolean
    ) {
        transientState.update {
            it.copy(
                isLoadingWebPage = false,
                webLoadProgress = 1f,
                canWebGoBack = canGoBack,
                canWebGoForward = canGoForward
            )
        }
        viewModelScope.launch {
            repository.updateTabNavigation(tabId, url, title)
            if (!isPrivate && url.isNotBlank()) {
                repository.recordHistory(title.orEmpty(), url)
            }
        }
    }

    fun updateTabSnapshot(tabId: Long, bitmap: Bitmap) {
        tabSnapshots[tabId] = bitmap.asImageBitmap()
    }

    fun createNewTab(groupName: String? = null) {
        feedback.onTabZoom(opening = false)
        viewModelScope.launch {
            val isPrivate = uiState.value.settings.isPrivateMode
            repository.createNewTab(isPrivate = isPrivate, initialUrl = "", groupName = groupName)
            transientState.update {
                it.copy(
                    isTabSwitcherOpen = false,
                    activeTabGroupFilter = null,
                    activeSection = TopHubSection.START_PAGE
                )
            }
        }
    }

    fun selectTab(tab: BrowserTabEntity) {
        feedback.onTabZoom(opening = false)
        viewModelScope.launch {
            repository.selectTab(tab)
            transientState.update {
                it.copy(
                    isTabSwitcherOpen = false,
                    activeTabGroupFilter = null
                )
            }
        }
    }

    fun closeTab(tab: BrowserTabEntity) {
        feedback.onTabClosed()
        viewModelScope.launch {
            val state = uiState.value
            val remaining = state.allTabs.filter { it.id != tab.id && it.isPrivate == tab.isPrivate }
            tabSnapshots.remove(tab.id)
            repository.closeTab(tab.id)
            if (remaining.isEmpty()) {
                repository.createNewTab(isPrivate = tab.isPrivate, initialUrl = "")
            } else if (state.settings.activeTabId == tab.id) {
                repository.selectTab(remaining.first())
            }
        }
    }

    fun closeAllCurrentModeTabs() {
        feedback.onTabClosed()
        viewModelScope.launch {
            val isPrivate = uiState.value.settings.isPrivateMode
            repository.closeAllTabs(isPrivate)
            transientState.update { it.copy(activeTabGroupFilter = null) }
            showToast("Cleared open tabs")
        }
    }

    fun switchTabPrivacyMode(privateMode: Boolean) {
        if (uiState.value.settings.isPrivateMode == privateMode) return
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            val matching = uiState.value.allTabs.filter { it.isPrivate == privateMode }
            if (matching.isEmpty()) {
                repository.createNewTab(isPrivate = privateMode, initialUrl = "")
            } else {
                repository.setPrivateMode(privateMode, matching.first().id)
            }
            transientState.update { it.copy(activeTabGroupFilter = null) }
        }
    }

    fun setTabGroupFilter(groupName: String?) {
        feedback.onGlassTap()
        transientState.update { it.copy(activeTabGroupFilter = groupName) }
    }

    fun toggleTabSearch() {
        feedback.onGlassTap()
        transientState.update {
            val next = !it.isTabSearchActive
            it.copy(isTabSearchActive = next, tabSearchQuery = if (!next) "" else it.tabSearchQuery)
        }
    }

    fun updateTabSearchQuery(query: String) {
        transientState.update { it.copy(tabSearchQuery = query) }
    }

    fun createTabGroupFromCurrentOrNew(groupName: String) {
        val clean = groupName.trim()
        if (clean.isEmpty()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            val active = uiState.value.activeTab
            if (active != null && active.groupName == null) {
                repository.assignTabToGroup(active.id, clean)
            } else {
                repository.createNewTab(
                    isPrivate = uiState.value.settings.isPrivateMode,
                    initialUrl = "",
                    groupName = clean
                )
            }
            showToast("Tab group \"$clean\" updated")
        }
    }

    fun setDarkMode(dark: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            repository.setDarkMode(dark)
        }
    }

    fun selectWallpaper(wallpaperId: String) {
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.setSelectedWallpaper(wallpaperId)
        }
    }

    fun importCustomWallpaper(uri: Uri) {
        feedback.onGlassTap()
        viewModelScope.launch {
            val result = WallpaperLuminanceAnalyzer.importWallpaperFromUri(getApplication(), uri)
            if (result != null) {
                val (file, isLight) = result
                val count = uiState.value.customWallpapers.size + 1
                repository.addCustomWallpaper("Custom $count", file, isLight)
                showToast(
                    if (isLight) "Wallpaper imported • Dark favorite labels active"
                    else "Wallpaper imported • Light favorite labels active"
                )
            } else {
                showToast("Unable to import image")
            }
        }
    }

    fun deleteCustomWallpaper(wallpaper: CustomWallpaperEntity) {
        feedback.onTabClosed()
        viewModelScope.launch {
            repository.deleteCustomWallpaper(wallpaper, uiState.value.settings.selectedWallpaperId)
        }
    }

    fun setSfxEnabled(enabled: Boolean) {
        feedback.sfxEnabled = enabled
        if (enabled) feedback.onSegmentSwitch()
        viewModelScope.launch { repository.setSfxEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        feedback.hapticsEnabled = enabled
        if (enabled) feedback.onSegmentSwitch()
        viewModelScope.launch { repository.setHapticsEnabled(enabled) }
    }

    fun setHighRefreshRateEnabled(enabled: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch { repository.setHighRefreshRateEnabled(enabled) }
    }

    fun setForceGpuRendering(enabled: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            repository.setForceGpuRendering(enabled)
            showToast(if (enabled) "Force GPU Rendering enabled" else "Force GPU Rendering off")
        }
    }

    fun setDisableHardwareOverlays(enabled: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            repository.setDisableHardwareOverlays(enabled)
            showToast(if (enabled) "Hardware Overlays disabled (GPU compositing)" else "Hardware Overlays allowed")
        }
    }

    fun setImmersiveOsMode(enabled: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            repository.setImmersiveOsMode(enabled)
            showToast(if (enabled) "Immersive OS Mode active" else "Standard system bars shown")
        }
    }

    fun saveCurrentPageToFavorites() {
        val tab = uiState.value.activeTab ?: return
        if (tab.url.isBlank()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.addFavorite(tab.title, tab.url)
            showToast("Added to Favorites")
        }
    }

    fun saveCurrentPageToReadingList() {
        val tab = uiState.value.activeTab ?: return
        if (tab.url.isBlank()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.addReadingListItem(tab.title, tab.url)
            showToast("Saved to Reading List")
        }
    }

    fun saveCurrentPageToBookmarks() {
        val tab = uiState.value.activeTab ?: return
        if (tab.url.isBlank()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.addBookmark(tab.title, tab.url)
            showToast("Bookmarked")
        }
    }

    fun addQuickReadingListItem(title: String, url: String) {
        if (url.isBlank()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.addReadingListItem(title, url)
            showToast("Added to Reading List")
        }
    }

    fun addQuickBookmark(title: String, url: String) {
        if (url.isBlank()) return
        feedback.onGlassTap()
        viewModelScope.launch {
            repository.addBookmark(title, url)
            showToast("Bookmark saved")
        }
    }

    fun removeFavorite(id: Long) {
        feedback.onTabClosed()
        viewModelScope.launch { repository.removeFavorite(id) }
    }

    fun removeReadingListItem(id: Long) {
        feedback.onTabClosed()
        viewModelScope.launch { repository.removeReadingListItem(id) }
    }

    fun removeBookmark(id: Long) {
        feedback.onTabClosed()
        viewModelScope.launch { repository.removeBookmark(id) }
    }

    fun clearBrowsingHistory() {
        feedback.onTabClosed()
        viewModelScope.launch {
            repository.clearHistory()
            showToast("History cleared")
        }
    }

    private fun showToast(message: String) {
        viewModelScope.launch {
            transientState.update { it.copy(statusToast = message) }
            kotlinx.coroutines.delay(2200L)
            transientState.update {
                if (it.statusToast == message) it.copy(statusToast = null) else it
            }
        }
    }
}
