package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.SiteNavBarVariant
import com.example.core.SiteNavItem
import com.example.core.SiteNavigationBarData
import com.example.core.SiteNavigationEngine
import com.example.data.BookmarkEntity
import com.example.data.FavoriteSiteEntity
import com.example.ui.components.EyeglassesIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.LocalSpatialBackdrop
import com.example.ui.components.SafariOpenBookIcon
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.LocalGlassStyle

/**
 * Adaptive per-site bottom navigation dock that renders a custom navigation bar tailored to each
 * visited website archetype:
 * - `YOUTUBE_SAFARI_ISLAND`: Exact 2-tier iOS 26 Safari dark smoked-glass island docked beneath
 *   YouTube's original bottom navigation bar (`Home / Shorts / You`).
 * - `EDITORIAL_READER_DECK`: Warm split-deck with live article chapter chips & 1-tap `AA Reader`.
 * - `DEV_COMMAND_HUD`: Architectural slate HUD with repo/HN strip, Find-in-Page & Desktop toggles.
 * - `SOCIAL_FLOATING_ORBS`: Detached 3-piece Liquid-Glass bubble trio.
 * - `UNIVERSAL_LIQUID_CAPSULE`: Sculpted dynamic Liquid-Glass capsule dock for all other domains.
 */
@Composable
fun SiteGlassNavigationBar(
    navData: SiteNavigationBarData,
    currentUrl: String,
    isLoading: Boolean = false,
    loadProgress: Float = 0f,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    isReaderMode: Boolean = false,
    isDesktopMode: Boolean = false,
    isSiriActive: Boolean = false,
    onHomeClick: () -> Unit,
    onNavItemClick: (SiteNavItem) -> Unit,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadOrStopClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToggleReaderClick: () -> Unit,
    onOpenFindClick: () -> Unit,
    onToggleDesktopClick: () -> Unit,
    onSiriClick: () -> Unit = {},
    onWebToolsClick: () -> Unit = {},
    onTabsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val backdrop = LocalSpatialBackdrop.current
    val listState = rememberLazyListState()

    val animatedProgress by animateFloatAsState(
        targetValue = if (isLoading) loadProgress.coerceIn(0.06f, 1f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "siteNavLoadProgress"
    )

    val activeIndex = remember(navData.items) {
        navData.items.indexOfFirst { it.isActive }
    }

    LaunchedEffect(activeIndex, navData.siteOrigin) {
        if (activeIndex >= 0 && activeIndex < navData.items.size) {
            try {
                listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        backdrop.motionTracker.notifyMoved()
    }

    val domainLabel = remember(navData.displayDomain, navData.siteBrand, currentUrl) {
        navData.displayDomain.ifBlank {
            val host = try {
                android.net.Uri.parse(currentUrl).host.orEmpty()
            } catch (_: Throwable) {
                ""
            }
            SiteNavigationEngine.formatDisplayDomain(host).ifBlank {
                navData.siteBrand.ifBlank { "website" }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        val barMaxWidth = (metrics.chromeBarMaxWidth + 110.dp).coerceAtMost(640.dp)

        AnimatedContent(
            targetState = navData.variant,
            transitionSpec = {
                fadeIn(spring(stiffness = 420f)) togetherWith fadeOut(spring(stiffness = 460f))
            },
            label = "siteNavBarVariantMorph"
        ) { variant ->
            when (variant) {
                SiteNavBarVariant.YOUTUBE_SAFARI_ISLAND -> {
                    YouTubeSafariIslandBar(
                        domainLabel = domainLabel,
                        isLoading = isLoading,
                        animatedProgress = animatedProgress,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        barMaxWidth = barMaxWidth,
                        onDomainClick = onDomainClick,
                        onBackClick = onBackClick,
                        onForwardClick = onForwardClick,
                        onReloadOrStopClick = onReloadOrStopClick,
                        onShareClick = onShareClick,
                        onBookmarksClick = onBookmarksClick,
                        onWebToolsClick = onWebToolsClick,
                        onTabsClick = onTabsClick
                    )
                }

                SiteNavBarVariant.EDITORIAL_READER_DECK -> {
                    EditorialReaderDeckBar(
                        navData = navData,
                        domainLabel = domainLabel,
                        isLoading = isLoading,
                        animatedProgress = animatedProgress,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isReaderMode = isReaderMode,
                        isSiriActive = isSiriActive,
                        listState = listState,
                        barMaxWidth = barMaxWidth,
                        onHomeClick = onHomeClick,
                        onNavItemClick = onNavItemClick,
                        onDomainClick = onDomainClick,
                        onBackClick = onBackClick,
                        onForwardClick = onForwardClick,
                        onToggleReaderClick = onToggleReaderClick,
                        onSiriClick = onSiriClick,
                        onBookmarksClick = onBookmarksClick,
                        onTabsClick = onTabsClick
                    )
                }

                SiteNavBarVariant.DEV_COMMAND_HUD -> {
                    DevCommandHudBar(
                        navData = navData,
                        domainLabel = domainLabel,
                        isLoading = isLoading,
                        animatedProgress = animatedProgress,
                        canGoBack = canGoBack,
                        isDesktopMode = isDesktopMode,
                        isSiriActive = isSiriActive,
                        listState = listState,
                        barMaxWidth = barMaxWidth,
                        onHomeClick = onHomeClick,
                        onNavItemClick = onNavItemClick,
                        onDomainClick = onDomainClick,
                        onBackClick = onBackClick,
                        onOpenFindClick = onOpenFindClick,
                        onToggleDesktopClick = onToggleDesktopClick,
                        onSiriClick = onSiriClick,
                        onTabsClick = onTabsClick
                    )
                }

                SiteNavBarVariant.SOCIAL_FLOATING_ORBS -> {
                    SocialFloatingOrbsBar(
                        navData = navData,
                        domainLabel = domainLabel,
                        isLoading = isLoading,
                        canGoBack = canGoBack,
                        isSiriActive = isSiriActive,
                        barMaxWidth = barMaxWidth,
                        onHomeClick = onHomeClick,
                        onDomainClick = onDomainClick,
                        onBackClick = onBackClick,
                        onReloadOrStopClick = onReloadOrStopClick,
                        onShareClick = onShareClick,
                        onSiriClick = onSiriClick,
                        onTabsClick = onTabsClick
                    )
                }

                SiteNavBarVariant.UNIVERSAL_LIQUID_CAPSULE -> {
                    UniversalLiquidCapsuleBar(
                        navData = navData,
                        domainLabel = domainLabel,
                        isLoading = isLoading,
                        animatedProgress = animatedProgress,
                        canGoBack = canGoBack,
                        isSiriActive = isSiriActive,
                        listState = listState,
                        barMaxWidth = barMaxWidth,
                        onHomeClick = onHomeClick,
                        onNavItemClick = onNavItemClick,
                        onDomainClick = onDomainClick,
                        onBackClick = onBackClick,
                        onReloadOrStopClick = onReloadOrStopClick,
                        onSiriClick = onSiriClick,
                        onBookmarksClick = onBookmarksClick,
                        onWebToolsClick = onWebToolsClick,
                        onTabsClick = onTabsClick
                    )
                }
            }
        }
    }
}

/**
 * Frosted Liquid-Glass Bookmarks & Reading Quick Popover Sheet opened from the `📖` Open Book button
 * in the Safari Island and per-site navigation bars without interrupting active webpage playback.
 */
@Composable
fun SiteBookmarksQuickSheet(
    bookmarks: List<BookmarkEntity>,
    favorites: List<FavoriteSiteEntity>,
    onSaveBookmark: () -> Unit,
    onSaveToReadingList: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenAllBookmarksHub: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.34f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(30.dp),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("site_bookmarks_quick_sheet")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SafariOpenBookIcon(tint = style.contentPrimary, modifier = Modifier.size(21.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.site_bookmarks_quick_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = style.contentPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(34.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .background(style.activeSegmentFill)
                            .bouncyClickable(
                                pressedScale = 0.90f,
                                contentDescriptionText = stringResource(R.string.site_bookmarks_open_all),
                                onClick = onOpenAllBookmarksHub
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.site_bookmarks_open_all),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = style.contentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(style.activeSegmentFill)
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = "Close bookmarks sheet",
                                onClick = onDismiss
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close bookmarks sheet",
                            tint = style.contentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1-Tap Save Current Page Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(style.activeSegmentFill)
                            .border(0.75.dp, style.rimTopLeft, RoundedCornerShape(16.dp))
                            .testTag("quick_save_bookmark_button")
                            .bouncyClickable(
                                pressedScale = 0.94f,
                                contentDescriptionText = stringResource(R.string.site_bookmarks_save_current),
                                onClick = {
                                    onSaveBookmark()
                                    onDismiss()
                                }
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            tint = style.contentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.site_bookmarks_save_current),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = style.contentPrimary,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(style.activeSegmentFill)
                            .border(0.75.dp, style.rimTopLeft, RoundedCornerShape(16.dp))
                            .testTag("quick_save_reading_button")
                            .bouncyClickable(
                                pressedScale = 0.94f,
                                contentDescriptionText = stringResource(R.string.site_bookmarks_save_reading),
                                onClick = {
                                    onSaveToReadingList()
                                    onDismiss()
                                }
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        EyeglassesIcon(tint = style.contentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.site_bookmarks_save_reading),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = style.contentPrimary,
                            maxLines = 1
                        )
                    }
                }

                // Saved Bookmarks & Top Favorites Quick Jump List
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(bookmarks.take(8), key = { "bm_${it.id}" }) { bm ->
                        QuickDestinationRow(
                            monogram = bm.title.take(1).uppercase().ifBlank { "B" },
                            title = bm.title,
                            subtitle = SiteNavigationEngine.formatDisplayDomain(
                                android.net.Uri.parse(bm.url).host.orEmpty()
                            ),
                            onClick = {
                                onDismiss()
                                onOpenUrl(bm.url)
                            }
                        )
                    }
                    items(favorites.take(6), key = { "fav_${it.id}" }) { fav ->
                        QuickDestinationRow(
                            monogram = fav.monogram.take(2),
                            title = fav.title,
                            subtitle = SiteNavigationEngine.formatDisplayDomain(
                                android.net.Uri.parse(fav.url).host.orEmpty()
                            ),
                            onClick = {
                                onDismiss()
                                onOpenUrl(fav.url)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDestinationRow(
    monogram: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(style.activeSegmentFill.copy(alpha = 0.55f))
            .bouncyClickable(
                pressedScale = 0.97f,
                contentDescriptionText = title,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(style.solidActionFill)
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = style.solidActionContent
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = style.contentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.contentSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
