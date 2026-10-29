package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.BrowserAiService
import com.example.core.DeviceOptimizationManager
import com.example.core.DownloadEngine
import com.example.core.FeedbackManager
import com.example.core.ResolvedWallpaper
import com.example.core.SiriAiUiState
import com.example.core.SiriChatTurn
import com.example.core.SiteNavItem
import com.example.core.SiteNavigationBarData
import com.example.core.SiteNavigationEngine
import com.example.core.WallpaperLuminanceAnalyzer
import com.example.core.WebPageAiContext
import com.example.data.BookmarkEntity
import com.example.data.BrowserDatabase
import com.example.data.BrowserPreferences
import com.example.data.BrowserRepository
import com.example.data.BrowserSettingsState
import com.example.data.BrowserTabEntity
import com.example.data.CustomWallpaperEntity
import com.example.data.DownloadItemEntity
import com.example.data.FavoriteSiteEntity
import com.example.data.HistoryEntryEntity
import com.example.data.ReadingListItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TopHubSection {
    START_PAGE,
    BOOKMARKS,
    READING_LIST,
    HISTORY,
    DOWNLOADS
}

data class TabGroupSummary(
    val groupName: String,
    val tabs: List<BrowserTabEntity>
)

data class BrowserUiState(
    val isReady: Boolean = false,
    val settings: BrowserSettingsState = BrowserSettingsState(),
    val deviceProfile: DeviceOptimizationManager.DeviceProfile = DeviceOptimizationManager.DeviceProfile(),
    val resolvedWallpaper: ResolvedWallpaper? = null,
    val favorites: List<FavoriteSiteEntity> = emptyList(),
    val readingList: List<ReadingListItemEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntryEntity> = emptyList(),
    val allTabs: List<BrowserTabEntity> = emptyList(),
    val customWallpapers: List<CustomWallpaperEntity> = emptyList(),
    val downloads: List<DownloadItemEntity> = emptyList(),
    val activeSection: TopHubSection = TopHubSection.START_PAGE,
    val showAllFavorites: Boolean = false,
    val showAllReadingList: Boolean = false,
    val isTabSwitcherOpen: Boolean = false,
    val isAddressOverlayOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isSiriOpen: Boolean = false,
    val siriAiState: SiriAiUiState = SiriAiUiState.Idle,
    val siriHistory: List<SiriChatTurn> = emptyList(),
    val isWebToolsSheetOpen: Boolean = false,
    val isFindInPageOpen: Boolean = false,
    val findQuery: String = "",
    val findActiveMatch: Int = 0,
    val findTotalMatches: Int = 0,
    val isDesktopMode: Boolean = false,
    val isReaderMode: Boolean = false,
    val webTextZoom: Int = 100,
    val sslCertificateSummary: String = "",
    val activeTabGroupFilter: String? = null,
    val tabSearchQuery: String = "",
    val isTabSearchActive: Boolean = false,
    val isLoadingWebPage: Boolean = false,
    val isVideoPlaying: Boolean = false,
    val isFullscreenVideo: Boolean = false,
    val isSiteBookmarksSheetOpen: Boolean = false,
    val webLoadProgress: Float = 0f,
    val canWebGoBack: Boolean = false,
    val canWebGoForward: Boolean = false,
    val siteNavigation: SiteNavigationBarData = SiteNavigationBarData(),
    val statusToast: String? = null
) {
    val activeTab: BrowserTabEntity?
        get() = allTabs.firstOrNull { it.id == settings.activeTabId }
            ?: allTabs.firstOrNull { it.isPrivate == settings.isPrivateMode }
            ?: allTabs.firstOrNull()

    val isViewingWebPage: Boolean
        get() = !activeTab?.url.isNullOrBlank() || siteNavigation.siteBrand.isNotBlank()

    val modeTabs: List<BrowserTabEntity>
        get() = allTabs.filter { it.isPrivate == settings.isPrivateMode }

    val standardTabCount: Int
        get() = allTabs.count { !it.isPrivate }

    val privateTabCount: Int
        get() = allTabs.count { it.isPrivate }

    val activeDownloadsCount: Int
        get() = downloads.count { it.status == "RUNNING" || it.status == "PAUSED" }

    val isWallpaperLight: Boolean
        get() = resolvedWallpaper?.isLightWallpaper ?: false
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BrowserDatabase.getInstance(application)
    private val preferences = BrowserPreferences(application)
    private val repository = BrowserRepository(database.browserDao(), preferences)
    val deviceProfile: DeviceOptimizationManager.DeviceProfile =
        DeviceOptimizationManager.detectProfile(application)
    val feedback = FeedbackManager(application)
    val downloadEngine = DownloadEngine(application.applicationContext, repository, viewModelScope)
    private val aiService = BrowserAiService()
    private var activeAiJob: Job? = null

    // In-memory live webpage thumbnail snapshots keyed by tabId, bounded by device RAM tier
    val tabSnapshots = mutableStateMapOf<Long, ImageBitmap>()
    private val snapshotLruOrder = LinkedHashSet<Long>()

    // Live downscaled & StackBlurred WebView backdrop so floating glass refracts webpages while scrolling/moving
    var liveWebBlurredBackdrop by mutableStateOf<ImageBitmap?>(null)
        private set

    // Live webpage context & latest bitmap for Siri Intelligence & multimodal screen vision
    private var latestPageAiContext: WebPageAiContext = WebPageAiContext()
    private var latestLivePageBitmap: Bitmap? = null

    private val _webCommand = MutableStateFlow<WebCommand?>(null)
    val webCommand: StateFlow<WebCommand?> = _webCommand.asStateFlow()

    sealed interface WebCommand {
        data object GoBack : WebCommand
        data object GoForward : WebCommand
        data object Reload : WebCommand
        data object StopLoading : WebCommand
        data class LoadUrl(val url: String, val nonce: Long = System.nanoTime()) : WebCommand
        data class FindInPage(val query: String, val nonce: Long = System.nanoTime()) : WebCommand
        data class FindNext(val forward: Boolean, val nonce: Long = System.nanoTime()) : WebCommand
        data object ClearFind : WebCommand
        data class SetDesktopMode(val enabled: Boolean, val nonce: Long = System.nanoTime()) : WebCommand
        data class SetReaderMode(val enabled: Boolean, val nonce: Long = System.nanoTime()) : WebCommand
        data class SetTextZoom(val zoomPercent: Int, val nonce: Long = System.nanoTime()) : WebCommand
        data class SetReplaceSiteNavBar(val enabled: Boolean, val nonce: Long = System.nanoTime()) : WebCommand
        data class NavigateSiteItem(val item: SiteNavItem, val nonce: Long = System.nanoTime()) : WebCommand
    }

    private data class TransientUiState(
        val activeSection: TopHubSection = TopHubSection.START_PAGE,
        val showAllFavorites: Boolean = false,
        val showAllReadingList: Boolean = false,
        val isTabSwitcherOpen: Boolean = false,
        val isAddressOverlayOpen: Boolean = false,
        val isSettingsSheetOpen: Boolean = false,
        val isSiriOpen: Boolean = false,
        val siriAiState: SiriAiUiState = SiriAiUiState.Idle,
        val siriHistory: List<SiriChatTurn> = emptyList(),
        val isWebToolsSheetOpen: Boolean = false,
        val isFindInPageOpen: Boolean = false,
        val findQuery: String = "",
        val findActiveMatch: Int = 0,
        val findTotalMatches: Int = 0,
        val isDesktopMode: Boolean = false,
        val isReaderMode: Boolean = false,
        val webTextZoom: Int = 100,
        val sslCertificateSummary: String = "",
        val activeTabGroupFilter: String? = null,
        val tabSearchQuery: String = "",
        val isTabSearchActive: Boolean = false,
        val isLoadingWebPage: Boolean = false,
        val isVideoPlaying: Boolean = false,
        val isFullscreenVideo: Boolean = false,
        val isSiteBookmarksSheetOpen: Boolean = false,
        val webLoadProgress: Float = 0f,
        val canWebGoBack: Boolean = false,
        val canWebGoForward: Boolean = false,
        val siteNavByTab: Map<Long, SiteNavigationBarData> = emptyMap(),
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
        repository.downloads,
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
        @Suppress("UNCHECKED_CAST")
        val downloads = values[7] as List<DownloadItemEntity>
        val resolvedWp = values[8] as ResolvedWallpaper?
        val transient = values[9] as TransientUiState
        val isSeeded = values[10] as Boolean

        feedback.sfxEnabled = settings.sfxEnabled
        feedback.hapticsEnabled = settings.hapticsEnabled

        val currentActiveTab = tabs.firstOrNull { it.id == settings.activeTabId }
            ?: tabs.firstOrNull { it.isPrivate == settings.isPrivateMode }
            ?: tabs.firstOrNull()
        val activeUrl = currentActiveTab?.url.orEmpty()
        val cachedForActive = currentActiveTab?.let { transient.siteNavByTab[it.id] }
        val resolvedSiteNav = when {
            currentActiveTab == null -> SiteNavigationBarData()
            activeUrl.isNotBlank() -> {
                val activeHost = BrowserRepository.extractDomain(activeUrl)
                val cachedHost = BrowserRepository.extractDomain(cachedForActive?.homeUrl.orEmpty())
                if (cachedForActive != null &&
                    cachedForActive.items.isNotEmpty() &&
                    cachedHost.equals(activeHost, ignoreCase = true)
                ) {
                    cachedForActive
                } else {
                    SiteNavigationEngine.buildInitialPlaceholderForUrl(activeUrl)
                }
            }
            cachedForActive != null && cachedForActive.siteBrand.isNotBlank() -> cachedForActive
            else -> SiteNavigationBarData()
        }

        BrowserUiState(
            isReady = isSeeded,
            settings = settings,
            deviceProfile = deviceProfile,
            resolvedWallpaper = resolvedWp,
            favorites = favorites,
            readingList = readingList,
            bookmarks = bookmarks,
            history = history,
            allTabs = tabs,
            customWallpapers = customWps,
            downloads = downloads,
            activeSection = transient.activeSection,
            showAllFavorites = transient.showAllFavorites,
            showAllReadingList = transient.showAllReadingList,
            isTabSwitcherOpen = transient.isTabSwitcherOpen,
            isAddressOverlayOpen = transient.isAddressOverlayOpen,
            isSettingsSheetOpen = transient.isSettingsSheetOpen,
            isSiriOpen = transient.isSiriOpen,
            siriAiState = transient.siriAiState,
            siriHistory = transient.siriHistory,
            isWebToolsSheetOpen = transient.isWebToolsSheetOpen,
            isFindInPageOpen = transient.isFindInPageOpen,
            findQuery = transient.findQuery,
            findActiveMatch = transient.findActiveMatch,
            findTotalMatches = transient.findTotalMatches,
            isDesktopMode = transient.isDesktopMode,
            isReaderMode = transient.isReaderMode,
            webTextZoom = transient.webTextZoom,
            sslCertificateSummary = transient.sslCertificateSummary,
            activeTabGroupFilter = transient.activeTabGroupFilter,
            tabSearchQuery = transient.tabSearchQuery,
            isTabSearchActive = transient.isTabSearchActive,
            isLoadingWebPage = transient.isLoadingWebPage,
            isVideoPlaying = transient.isVideoPlaying,
            isFullscreenVideo = transient.isFullscreenVideo,
            isSiteBookmarksSheetOpen = transient.isSiteBookmarksSheetOpen,
            webLoadProgress = transient.webLoadProgress,
            canWebGoBack = transient.canWebGoBack,
            canWebGoForward = transient.canWebGoForward,
            siteNavigation = resolvedSiteNav,
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
            downloadEngine.ensureProgressPollerRunning()
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

    fun openDownloadsHub() {
        feedback.onSegmentSwitch()
        transientState.update {
            it.copy(
                activeSection = TopHubSection.DOWNLOADS,
                isWebToolsSheetOpen = false,
                isAddressOverlayOpen = false,
                isTabSwitcherOpen = false
            )
        }
        val active = uiState.value.activeTab
        if (active != null && active.url.isNotBlank()) {
            viewModelScope.launch {
                repository.updateTabNavigation(active.id, "", "Start Page")
                transientState.update { st ->
                    st.copy(
                        canWebGoBack = false,
                        canWebGoForward = false,
                        isLoadingWebPage = false,
                        isVideoPlaying = false,
                        isFullscreenVideo = false,
                        isSiteBookmarksSheetOpen = false,
                        isFindInPageOpen = false,
                        webLoadProgress = 0f,
                        siteNavByTab = st.siteNavByTab - active.id
                    )
                }
            }
        }
    }

    fun openBookmarksHub() {
        feedback.onSegmentSwitch()
        transientState.update {
            it.copy(
                activeSection = TopHubSection.BOOKMARKS,
                isSiteBookmarksSheetOpen = false,
                isWebToolsSheetOpen = false,
                isAddressOverlayOpen = false,
                isTabSwitcherOpen = false
            )
        }
        val active = uiState.value.activeTab
        if (active != null && active.url.isNotBlank()) {
            viewModelScope.launch {
                repository.updateTabNavigation(active.id, "", "Start Page")
                transientState.update { st ->
                    st.copy(
                        canWebGoBack = false,
                        canWebGoForward = false,
                        isLoadingWebPage = false,
                        isVideoPlaying = false,
                        isFullscreenVideo = false,
                        isSiteBookmarksSheetOpen = false,
                        isFindInPageOpen = false,
                        webLoadProgress = 0f,
                        siteNavByTab = st.siteNavByTab - active.id
                    )
                }
            }
        }
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
                isWebToolsSheetOpen = false,
                isSiriOpen = false,
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
        transientState.update {
            it.copy(
                isAddressOverlayOpen = true,
                isWebToolsSheetOpen = false,
                isSiriOpen = false
            )
        }
    }

    fun closeAddressOverlay() {
        transientState.update { it.copy(isAddressOverlayOpen = false) }
    }

    fun setSettingsSheetOpen(open: Boolean) {
        feedback.onGlassTap()
        transientState.update {
            it.copy(
                isSettingsSheetOpen = open,
                isWebToolsSheetOpen = false
            )
        }
    }

    fun setWebToolsSheetOpen(open: Boolean) {
        feedback.onGlassTap()
        transientState.update {
            it.copy(
                isWebToolsSheetOpen = open,
                isSiteBookmarksSheetOpen = false,
                isAddressOverlayOpen = false,
                isSiriOpen = false
            )
        }
    }

    fun setSiteBookmarksSheetOpen(open: Boolean) {
        feedback.onGlassTap()
        transientState.update {
            it.copy(
                isSiteBookmarksSheetOpen = open,
                isWebToolsSheetOpen = false,
                isAddressOverlayOpen = false,
                isSiriOpen = false
            )
        }
    }

    // =========================================================================
    // Siri iOS 26 AI Assistant Actions
    // =========================================================================

    fun toggleSiriOverlay() {
        feedback.onGlassTap()
        transientState.update {
            val next = !it.isSiriOpen
            it.copy(
                isSiriOpen = next,
                isAddressOverlayOpen = false,
                isWebToolsSheetOpen = false,
                isSettingsSheetOpen = false
            )
        }
    }

    fun closeSiriOverlay() {
        transientState.update { it.copy(isSiriOpen = false) }
    }

    fun cancelSiriGeneration() {
        activeAiJob?.cancel()
        activeAiJob = null
        transientState.update { it.copy(siriAiState = SiriAiUiState.Idle) }
    }

    fun clearSiriConversation() {
        activeAiJob?.cancel()
        activeAiJob = null
        transientState.update {
            it.copy(
                siriAiState = SiriAiUiState.Idle,
                siriHistory = emptyList()
            )
        }
    }

    fun submitSiriPrompt(rawPrompt: String, includeVisualScreen: Boolean = false) {
        val clean = rawPrompt.trim()
        if (clean.isEmpty()) return
        feedback.onGlassTap()
        activeAiJob?.cancel()

        transientState.update { it.copy(isSiriOpen = true) }

        val state = uiState.value
        val pageCtx = if (state.isViewingWebPage) {
            latestPageAiContext.copy(
                url = latestPageAiContext.url.ifBlank { state.activeTab?.url.orEmpty() },
                title = latestPageAiContext.title.ifBlank { state.activeTab?.title.orEmpty() }
            )
        } else {
            null
        }
        val visionBmp = if (includeVisualScreen) latestLivePageBitmap else null
        val historySnapshot = state.siriHistory

        activeAiJob = viewModelScope.launch {
            aiService.streamAssistantResponse(
                userPrompt = clean,
                pageContext = pageCtx,
                screenBitmap = visionBmp,
                recentTurns = historySnapshot
            ).collect { emitted ->
                transientState.update { curr ->
                    val updatedHistory = if (emitted is SiriAiUiState.Success) {
                        (curr.siriHistory + SiriChatTurn(
                            userPrompt = clean,
                            modelReply = emitted.text,
                            usedScreenVision = includeVisualScreen
                        )).takeLast(12)
                    } else {
                        curr.siriHistory
                    }
                    curr.copy(
                        siriAiState = emitted,
                        siriHistory = updatedHistory
                    )
                }
            }
        }
    }

    fun summarizeCurrentPage() {
        submitSiriPrompt(
            "Provide a clear, high-signal executive summary of this webpage with key highlights."
        )
    }

    fun analyzeVisualScreen(prompt: String) {
        submitSiriPrompt(rawPrompt = prompt, includeVisualScreen = true)
    }

    fun extractKeyTakeaways() {
        submitSiriPrompt(
            "List the 5 most important takeaways, facts, or action items from this webpage."
        )
    }

    fun translateCurrentPageSummary() {
        submitSiriPrompt(
            "Translate the main content and key sections of this webpage into clear English (or Spanish if already in English)."
        )
    }

    fun generateSmartDigest() {
        feedback.onGlassTap()
        activeAiJob?.cancel()
        val state = uiState.value
        val pageCtx = if (state.isViewingWebPage) {
            latestPageAiContext.copy(
                url = latestPageAiContext.url.ifBlank { state.activeTab?.url.orEmpty() },
                title = latestPageAiContext.title.ifBlank { state.activeTab?.title.orEmpty() }
            )
        } else {
            null
        }
        val fallbackTopic = state.activeTab?.title.orEmpty()

        transientState.update {
            it.copy(
                isSiriOpen = true,
                siriAiState = SiriAiUiState.Loading(activePrompt = "Smart Digest")
            )
        }

        activeAiJob = viewModelScope.launch {
            val result = aiService.generateStructuredDigest(pageCtx, fallbackTopic)
            transientState.update { it.copy(siriAiState = result) }
        }
    }

    fun updatePageAiContext(context: WebPageAiContext) {
        latestPageAiContext = context
    }

    // =========================================================================
    // Downloads Tab & Engine Actions
    // =========================================================================

    fun startWebOrDirectDownload(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L
    ) {
        val normalized = if (url.startsWith("http://", true) ||
            url.startsWith("https://", true) ||
            url.startsWith("data:", true)
        ) {
            url.trim()
        } else {
            "https://${url.trim()}"
        }
        feedback.onGlassTap()
        downloadEngine.enqueueWebDownload(
            url = normalized,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            contentLength = contentLength,
            onStatusToast = ::showToast
        )
    }

    fun saveBlobBase64Download(dataUrl: String, fileName: String?, mimeType: String?) {
        viewModelScope.launch {
            val saved = downloadEngine.saveBase64BlobDownload(dataUrl, fileName, mimeType)
            if (saved != null) {
                showToast("Downloaded $saved")
            } else {
                showToast("Unable to save blob download")
            }
        }
    }

    fun openDownloadItem(context: Context, item: DownloadItemEntity) {
        feedback.onGlassTap()
        if (!downloadEngine.openDownload(context, item)) {
            showToast("No installed viewer found for ${item.fileName}")
        }
    }

    fun shareDownloadItem(context: Context, item: DownloadItemEntity) {
        feedback.onGlassTap()
        if (!downloadEngine.shareDownload(context, item)) {
            showToast("Unable to share ${item.fileName}")
        }
    }

    fun retryDownloadItem(item: DownloadItemEntity) {
        feedback.onGlassTap()
        viewModelScope.launch {
            downloadEngine.removeDownload(item)
            startWebOrDirectDownload(url = item.url, mimeType = item.mimeType)
        }
    }

    fun deleteDownloadItem(item: DownloadItemEntity) {
        feedback.onTabClosed()
        viewModelScope.launch {
            downloadEngine.removeDownload(item)
        }
    }

    fun clearCompletedDownloads() {
        feedback.onTabClosed()
        viewModelScope.launch {
            repository.clearCompletedDownloads()
            showToast("Cleared completed downloads")
        }
    }

    // =========================================================================
    // Enhanced WebView Components & Navigation Actions
    // =========================================================================

    fun openUrlOrSearch(rawInput: String) {
        val normalized = BrowserRepository.normalizeUrlOrSearch(rawInput)
        if (normalized.isBlank()) return
        feedback.onGlassTap()
        val current = uiState.value.activeTab
        val placeholderNav = SiteNavigationEngine.buildInitialPlaceholderForUrl(normalized)
        transientState.update { st ->
            st.copy(
                isAddressOverlayOpen = false,
                isTabSwitcherOpen = false,
                isWebToolsSheetOpen = false,
                isSiteBookmarksSheetOpen = false,
                isVideoPlaying = false,
                isFullscreenVideo = false,
                isReaderMode = false,
                siteNavByTab = if (current != null) {
                    st.siteNavByTab + (current.id to placeholderNav)
                } else {
                    st.siteNavByTab
                }
            )
        }
        viewModelScope.launch {
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

    fun openUrlInNewTab(rawUrl: String) {
        val normalized = BrowserRepository.normalizeUrlOrSearch(rawUrl)
        if (normalized.isBlank()) return
        feedback.onTabZoom(opening = false)
        viewModelScope.launch {
            repository.createNewTab(
                isPrivate = uiState.value.settings.isPrivateMode,
                initialUrl = normalized
            )
            showToast("Opened in new tab")
        }
    }

    fun goForwardWebPage() {
        feedback.onGlassTap()
        _webCommand.value = WebCommand.GoForward
    }

    fun stopOrReloadWebPage() {
        feedback.onGlassTap()
        if (uiState.value.isLoadingWebPage) {
            _webCommand.value = WebCommand.StopLoading
            transientState.update { it.copy(isLoadingWebPage = false, webLoadProgress = 0f) }
        } else {
            _webCommand.value = WebCommand.Reload
        }
    }

    fun openFindInPage() {
        feedback.onGlassTap()
        transientState.update {
            it.copy(
                isFindInPageOpen = true,
                isWebToolsSheetOpen = false,
                findActiveMatch = 0,
                findTotalMatches = 0
            )
        }
    }

    fun closeFindInPage() {
        transientState.update {
            it.copy(
                isFindInPageOpen = false,
                findQuery = "",
                findActiveMatch = 0,
                findTotalMatches = 0
            )
        }
        _webCommand.value = WebCommand.ClearFind
    }

    fun updateFindQuery(query: String) {
        transientState.update { it.copy(findQuery = query) }
        _webCommand.value = WebCommand.FindInPage(query)
    }

    fun findNextMatch(forward: Boolean) {
        feedback.onGlassTap()
        _webCommand.value = WebCommand.FindNext(forward)
    }

    fun onFindResultsUpdated(activeOrdinal: Int, numberOfMatches: Int) {
        transientState.update {
            it.copy(
                findActiveMatch = activeOrdinal,
                findTotalMatches = numberOfMatches
            )
        }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        feedback.onSegmentSwitch()
        transientState.update { it.copy(isDesktopMode = enabled) }
        _webCommand.value = WebCommand.SetDesktopMode(enabled)
        showToast(if (enabled) "Desktop Site enabled" else "Mobile Site active")
    }

    fun toggleReaderMode(enabled: Boolean) {
        feedback.onSegmentSwitch()
        transientState.update { it.copy(isReaderMode = enabled) }
        _webCommand.value = WebCommand.SetReaderMode(enabled)
        showToast(if (enabled) "Reader View enabled" else "Standard Web View restored")
    }

    fun updateWebTextZoom(percent: Int) {
        val clamped = percent.coerceIn(70, 190)
        feedback.onGlassTap()
        transientState.update { it.copy(webTextZoom = clamped) }
        _webCommand.value = WebCommand.SetTextZoom(clamped)
    }

    fun updateSslCertificateSummary(summary: String) {
        transientState.update { it.copy(sslCertificateSummary = summary) }
    }

    fun onSiteNavigationJsonReceived(tabId: Long, payloadJson: String) {
        val fallbackUrl = uiState.value.allTabs.firstOrNull { it.id == tabId }?.url
            ?: uiState.value.activeTab?.url.orEmpty()
        val parsed = SiteNavigationEngine.parseBridgeJson(payloadJson, fallbackUrl)
        transientState.update { st ->
            st.copy(siteNavByTab = st.siteNavByTab + (tabId to parsed))
        }
    }

    fun onSiteHomeClicked() {
        val state = uiState.value
        val targetHome = state.siteNavigation.homeUrl.ifBlank {
            val activeUrl = state.activeTab?.url.orEmpty()
            SiteNavigationEngine.buildInitialPlaceholderForUrl(activeUrl).homeUrl
        }
        if (targetHome.isNotBlank()) {
            openUrlOrSearch(targetHome)
        }
    }

    fun onSiteNavItemClicked(item: SiteNavItem) {
        if (item.url.isBlank()) return
        feedback.onSegmentSwitch()
        val activeTab = uiState.value.activeTab ?: return
        transientState.update { st ->
            val existing = st.siteNavByTab[activeTab.id] ?: uiState.value.siteNavigation
            val updatedItems = existing.items.map { candidate ->
                candidate.copy(isActive = candidate.url == item.url && candidate.label == item.label)
            }
            st.copy(
                siteNavByTab = st.siteNavByTab + (activeTab.id to existing.copy(items = updatedItems))
            )
        }
        if (!item.isAnchor && (item.url.startsWith("http://", true) || item.url.startsWith("https://", true))) {
            viewModelScope.launch {
                repository.updateTabNavigation(activeTab.id, item.url, null)
            }
        }
        _webCommand.value = WebCommand.NavigateSiteItem(item)
    }

    fun submitSiteSearch(query: String) {
        val state = uiState.value
        val targetUrl = SiteNavigationEngine.resolveSiteSearchUrl(
            query = query,
            navData = state.siteNavigation,
            currentUrl = state.activeTab?.url.orEmpty()
        )
        if (targetUrl.isNotBlank()) {
            openUrlOrSearch(targetUrl)
        }
    }

    fun setReplaceSiteNavBar(enabled: Boolean) {
        feedback.onSegmentSwitch()
        _webCommand.value = WebCommand.SetReplaceSiteNavBar(enabled)
        viewModelScope.launch {
            repository.setReplaceSiteNavBar(enabled)
            showToast(
                if (enabled) "Custom Site Navigation Bar active"
                else "Original website header restored"
            )
        }
    }

    fun copyUrlToClipboard(context: Context, url: String) {
        if (url.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("URL", url))
        showToast("Copied link to clipboard")
    }

    fun sharePageUrl(context: Context, title: String, url: String) {
        if (url.isBlank()) return
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(
                Intent.createChooser(send, title.ifBlank { "Share Link" })
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            showToast("Unable to open share sheet")
        }
    }

    fun returnToStartPage() {
        feedback.onGlassTap()
        liveWebBlurredBackdrop = null
        latestLivePageBitmap = null
        latestPageAiContext = WebPageAiContext()
        viewModelScope.launch {
            val current = uiState.value.activeTab ?: return@launch
            repository.updateTabNavigation(current.id, "", "Start Page")
            transientState.update { st ->
                st.copy(
                    canWebGoBack = false,
                    canWebGoForward = false,
                    isLoadingWebPage = false,
                    isVideoPlaying = false,
                    isFullscreenVideo = false,
                    isSiteBookmarksSheetOpen = false,
                    isFindInPageOpen = false,
                    isWebToolsSheetOpen = false,
                    isReaderMode = false,
                    webLoadProgress = 0f,
                    siteNavByTab = st.siteNavByTab - current.id
                )
            }
        }
    }

    fun onSystemHomeAction() {
        transientState.update {
            it.copy(
                isTabSwitcherOpen = false,
                isAddressOverlayOpen = false,
                isSettingsSheetOpen = false,
                isWebToolsSheetOpen = false,
                isSiteBookmarksSheetOpen = false,
                isSiriOpen = false,
                isFindInPageOpen = false,
                isVideoPlaying = false,
                isFullscreenVideo = false,
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
            state.isSiriOpen -> closeSiriOverlay()
            state.isSiteBookmarksSheetOpen -> setSiteBookmarksSheetOpen(false)
            state.isWebToolsSheetOpen -> setWebToolsSheetOpen(false)
            state.isFindInPageOpen -> closeFindInPage()
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
        val placeholderNav = SiteNavigationEngine.buildInitialPlaceholderForUrl(url)
        transientState.update { st ->
            val currentCached = st.siteNavByTab[tabId]
            val sameDomain = currentCached != null &&
                BrowserRepository.extractDomain(currentCached.homeUrl)
                    .equals(BrowserRepository.extractDomain(url), ignoreCase = true)
            val nextMap = if (sameDomain) {
                st.siteNavByTab
            } else {
                st.siteNavByTab + (tabId to placeholderNav)
            }
            st.copy(
                isLoadingWebPage = true,
                isVideoPlaying = false,
                isFullscreenVideo = false,
                isReaderMode = false,
                webLoadProgress = 0.12f,
                siteNavByTab = nextMap
            )
        }
        viewModelScope.launch {
            repository.updateTabNavigation(tabId, url, null)
        }
    }

    fun onWebVideoPlaybackChanged(tabId: Long, isPlaying: Boolean) {
        val activeId = uiState.value.activeTab?.id
        if (activeId == null || tabId == activeId || !isPlaying) {
            transientState.update { current ->
                if (current.isVideoPlaying == isPlaying) current
                else current.copy(isVideoPlaying = isPlaying)
            }
        }
    }

    fun onWebFullscreenVideoChanged(tabId: Long, isFullscreen: Boolean) {
        val activeId = uiState.value.activeTab?.id
        if (activeId == null || tabId == activeId || !isFullscreen) {
            transientState.update { current ->
                if (current.isFullscreenVideo == isFullscreen) current
                else current.copy(isFullscreenVideo = isFullscreen)
            }
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
        latestLivePageBitmap = bitmap
        snapshotLruOrder.remove(tabId)
        snapshotLruOrder.add(tabId)
        tabSnapshots[tabId] = bitmap.asImageBitmap()

        // Also compute a silky downscaled blurred backdrop asynchronously for live frosted glass over webpages
        viewModelScope.launch {
            val blurred = withContext(Dispatchers.Default) {
                WallpaperLuminanceAnalyzer.blurBitmapForBackdrop(
                    source = bitmap,
                    downscale = 3,
                    radius = deviceProfile.fallbackStackBlurRadius
                )
            }
            if (blurred != null && uiState.value.activeTab?.id == tabId) {
                liveWebBlurredBackdrop = blurred
            }
        }

        val maxCached = deviceProfile.maxCachedTabSnapshots.coerceAtLeast(2)
        val activeId = uiState.value.activeTab?.id
        while (tabSnapshots.size > maxCached && snapshotLruOrder.isNotEmpty()) {
            val evictCandidate = snapshotLruOrder.firstOrNull { it != activeId && it != tabId }
                ?: break
            snapshotLruOrder.remove(evictCandidate)
            tabSnapshots.remove(evictCandidate)
        }
    }

    fun updateLiveWebBackdropOnly(bitmap: Bitmap) {
        latestLivePageBitmap = bitmap
        viewModelScope.launch {
            val blurred = withContext(Dispatchers.Default) {
                WallpaperLuminanceAnalyzer.blurBitmapForBackdrop(
                    source = bitmap,
                    downscale = 3,
                    radius = deviceProfile.fallbackStackBlurRadius
                )
            }
            if (blurred != null) {
                liveWebBlurredBackdrop = blurred
            }
        }
    }

    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            val activeId = uiState.value.activeTab?.id
            val toRemove = tabSnapshots.keys.filter { it != activeId }
            toRemove.forEach { id ->
                tabSnapshots.remove(id)
                snapshotLruOrder.remove(id)
            }
        }
    }

    fun createNewTab(groupName: String? = null) {
        feedback.onTabZoom(opening = false)
        liveWebBlurredBackdrop = null
        viewModelScope.launch {
            val isPrivate = uiState.value.settings.isPrivateMode
            repository.createNewTab(isPrivate = isPrivate, initialUrl = "", groupName = groupName)
            transientState.update {
                it.copy(
                    isTabSwitcherOpen = false,
                    isVideoPlaying = false,
                    isFindInPageOpen = false,
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
                    isVideoPlaying = false,
                    isFindInPageOpen = false,
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
            snapshotLruOrder.remove(tab.id)
            transientState.update { st -> st.copy(siteNavByTab = st.siteNavByTab - tab.id) }
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
            transientState.update {
                it.copy(
                    activeTabGroupFilter = null,
                    siteNavByTab = emptyMap()
                )
            }
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
        viewModelScope.launch { repository.setDarkMode(dark) }
    }

    fun selectWallpaper(wallpaperId: String) {
        feedback.onGlassTap()
        viewModelScope.launch { repository.setSelectedWallpaper(wallpaperId) }
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

    fun setEnableGpuRasterization(enabled: Boolean) {
        feedback.onSegmentSwitch()
        viewModelScope.launch {
            repository.setEnableGpuRasterization(enabled)
            showToast(if (enabled) "GPU Rasterization enabled" else "GPU Rasterization off")
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

    fun showToast(message: String) {
        viewModelScope.launch {
            transientState.update { it.copy(statusToast = message) }
            kotlinx.coroutines.delay(2200L)
            transientState.update {
                if (it.statusToast == message) it.copy(statusToast = null) else it
            }
        }
    }
}
