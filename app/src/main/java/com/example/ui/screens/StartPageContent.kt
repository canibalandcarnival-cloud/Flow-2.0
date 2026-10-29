package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.data.BookmarkEntity
import com.example.data.DownloadItemEntity
import com.example.data.FavoriteSiteEntity
import com.example.data.HistoryEntryEntity
import com.example.data.ReadingListItemEntity
import com.example.ui.BrowserUiState
import com.example.ui.TopHubSection
import com.example.ui.components.EyeglassesIcon
import com.example.ui.components.FavoriteSquircleIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.ReadingListThumbnail
import com.example.ui.components.bouncyClickable
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.components.rubberBandOverscroll
import com.example.ui.theme.LocalGlassStyle
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BrowserHubContent(
    uiState: BrowserUiState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onOpenUrl: (String) -> Unit,
    onToggleShowAllFavorites: () -> Unit,
    onToggleShowAllReadingList: () -> Unit,
    onOpenCustomizeSheet: () -> Unit,
    onDeleteReadingItem: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onStartDirectDownload: (String) -> Unit = {},
    onOpenDownload: (DownloadItemEntity) -> Unit = {},
    onShareDownload: (DownloadItemEntity) -> Unit = {},
    onRetryDownload: (DownloadItemEntity) -> Unit = {},
    onDeleteDownload: (DownloadItemEntity) -> Unit = {},
    onClearCompletedDownloads: () -> Unit = {},
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Strictly adapt Favorites labels & direct-on-wallpaper headers to wallpaper luminance:
    // Dark wallpaper -> Light (PureWhite); Light wallpaper -> Dark (PureBlack)
    val onWallpaperForeground = if (uiState.isWallpaperLight) PureBlack else PureWhite
    val onWallpaperSecondary = if (uiState.isWallpaperLight) {
        PureBlack.copy(alpha = 0.78f)
    } else {
        PureWhite.copy(alpha = 0.85f)
    }

    AnimatedContent(
        targetState = uiState.activeSection,
        transitionSpec = {
            (fadeIn(spring(stiffness = 420f)) +
                scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)))
                .togetherWith(
                    fadeOut(spring(stiffness = 500f)) +
                        scaleOut(targetScale = 0.98f, animationSpec = spring(stiffness = 460f))
                )
        },
        label = "hubSectionTransition",
        modifier = modifier.fillMaxSize()
    ) { section ->
        when (section) {
            TopHubSection.START_PAGE -> StartPageSectionList(
                favorites = uiState.favorites,
                readingList = uiState.readingList,
                showAllFavorites = uiState.showAllFavorites,
                showAllReadingList = uiState.showAllReadingList,
                favoriteLabelColor = onWallpaperForeground,
                headerSecondaryColor = onWallpaperSecondary,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                onOpenUrl = onOpenUrl,
                onToggleShowAllFavorites = onToggleShowAllFavorites,
                onToggleShowAllReadingList = onToggleShowAllReadingList,
                onOpenCustomizeSheet = onOpenCustomizeSheet,
                onEdgeBounce = onEdgeBounce
            )
            TopHubSection.BOOKMARKS -> BookmarksSectionList(
                bookmarks = uiState.bookmarks,
                headerColor = onWallpaperForeground,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                onOpenUrl = onOpenUrl,
                onDeleteBookmark = onDeleteBookmark,
                onEdgeBounce = onEdgeBounce
            )
            TopHubSection.READING_LIST -> FullReadingListSection(
                items = uiState.readingList,
                headerColor = onWallpaperForeground,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                onOpenUrl = onOpenUrl,
                onDeleteReadingItem = onDeleteReadingItem,
                onEdgeBounce = onEdgeBounce
            )
            TopHubSection.HISTORY -> HistorySectionList(
                entries = uiState.history,
                headerColor = onWallpaperForeground,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                onOpenUrl = onOpenUrl,
                onClearHistory = onClearHistory,
                onEdgeBounce = onEdgeBounce
            )
            TopHubSection.DOWNLOADS -> DownloadsSectionList(
                downloads = uiState.downloads,
                headerColor = onWallpaperForeground,
                topContentPadding = topContentPadding,
                bottomContentPadding = bottomContentPadding,
                onStartDirectDownload = onStartDirectDownload,
                onOpenDownload = onOpenDownload,
                onShareDownload = onShareDownload,
                onRetryDownload = onRetryDownload,
                onDeleteDownload = onDeleteDownload,
                onClearCompleted = onClearCompletedDownloads,
                onEdgeBounce = onEdgeBounce
            )
        }
    }
}

@Composable
private fun StartPageSectionList(
    favorites: List<FavoriteSiteEntity>,
    readingList: List<ReadingListItemEntity>,
    showAllFavorites: Boolean,
    showAllReadingList: Boolean,
    favoriteLabelColor: Color,
    headerSecondaryColor: Color,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onOpenUrl: (String) -> Unit,
    onToggleShowAllFavorites: () -> Unit,
    onToggleShowAllReadingList: () -> Unit,
    onOpenCustomizeSheet: () -> Unit,
    onEdgeBounce: (Float) -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        val favCols = metrics.favoritesColumns
        val defaultFavCount = favCols * 2
        val defaultReadingCount = if (metrics.readingListColumns == 2) 4 else 3
        val visibleFavorites = if (showAllFavorites) favorites else favorites.take(defaultFavCount)
        val visibleReading = if (showAllReadingList) readingList else readingList.take(defaultReadingCount)

        LazyColumn(
            flingBehavior = momentumFling,
            contentPadding = PaddingValues(
                top = topContentPadding,
                bottom = bottomContentPadding,
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = metrics.contentMaxWidth)
                .fillMaxSize()
                .testTag("start_page_scroll_list")
                .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
        ) {
            // Favorites Section Header
            item(key = "favorites_header") {
                SectionHeaderRow(
                    title = stringResource(R.string.favorites_title),
                    actionText = if (showAllFavorites) {
                        stringResource(R.string.show_less)
                    } else {
                        stringResource(R.string.show_all)
                    },
                    titleColor = favoriteLabelColor,
                    actionColor = headerSecondaryColor,
                    onActionClick = onToggleShowAllFavorites,
                    actionTag = "favorites_show_all_button"
                )
            }

            // Adaptive Favorites squircle grid (4 columns on handsets, 6 on foldables/landscape/tablets)
            item(key = "favorites_grid") {
                val rows = visibleFavorites.chunked(favCols)
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 340f)
                        )
                ) {
                    rows.forEach { rowSites ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (metrics.isUltraCompactPhone) 8.dp else 12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (col in 0 until favCols) {
                                val site = rowSites.getOrNull(col)
                                if (site != null) {
                                    FavoriteGridCell(
                                        site = site,
                                        iconSize = metrics.favoriteIconSize,
                                        labelColor = favoriteLabelColor,
                                        onClick = { onOpenUrl(site.url) },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Extra breathing space before Reading List matching Reference Image 2
            item(key = "section_spacer") {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Reading List Header
            item(key = "reading_list_header") {
                SectionHeaderRow(
                    title = stringResource(R.string.reading_list_title),
                    actionText = if (showAllReadingList) {
                        stringResource(R.string.show_less)
                    } else {
                        stringResource(R.string.show_all)
                    },
                    titleColor = favoriteLabelColor,
                    actionColor = headerSecondaryColor,
                    onActionClick = onToggleShowAllReadingList,
                    actionTag = "reading_list_show_all_button"
                )
            }

            // Reading List Frosted Glass Cards (1 column on handsets, 2-column canonical grid on wide foldables/tablets)
            if (metrics.readingListColumns == 2) {
                val readingPairs = visibleReading.chunked(2)
                items(
                    items = readingPairs,
                    key = { pair -> "reading_row_${pair.first().id}" }
                ) { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReadingListGlassCard(
                            article = pair[0],
                            onClick = { onOpenUrl(pair[0].url) },
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.size > 1) {
                            ReadingListGlassCard(
                                article = pair[1],
                                onClick = { onOpenUrl(pair[1].url) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(
                    items = visibleReading,
                    key = { "reading_${it.id}" }
                ) { article ->
                    ReadingListGlassCard(
                        article = article,
                        onClick = { onOpenUrl(article.url) }
                    )
                }
            }

            // Customize & Wallpaper Frosted Pill at bottom of Start Page
            item(key = "customize_pill") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp)
                ) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(50),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("customize_start_page_button")
                            .bouncyClickable(
                                pressedScale = 0.92f,
                                contentDescriptionText = stringResource(R.string.customize_start_page),
                                onClick = onOpenCustomizeSheet
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = style.contentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.customize_start_page),
                                style = MaterialTheme.typography.labelLarge,
                                color = style.contentPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionText: String?,
    titleColor: Color,
    actionColor: Color,
    onActionClick: (() -> Unit)? = null,
    actionTag: String = ""
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp
            ),
            color = titleColor
        )

        if (actionText != null && onActionClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .let { if (actionTag.isNotEmpty()) it.testTag(actionTag) else it }
                    .bouncyClickable(
                        pressedScale = 0.92f,
                        contentDescriptionText = "$actionText $title",
                        onClick = onActionClick
                    )
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = actionColor
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = actionColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FavoriteGridCell(
    site: FavoriteSiteEntity,
    iconSize: Dp,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag("favorite_item_${site.id}")
            .bouncyClickable(
                pressedScale = 0.90f,
                contentDescriptionText = site.title,
                onClick = onClick
            )
    ) {
        FavoriteSquircleIcon(
            site = site,
            size = iconSize
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = site.title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 14.5.sp
            ),
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ReadingListGlassCard(
    article: ReadingListItemEntity,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(24.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = modifier
            .fillMaxWidth()
            .testTag("reading_card_${article.id}")
            .bouncyClickable(
                pressedScale = 0.96f,
                contentDescriptionText = article.title,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            ReadingListThumbnail(
                artworkStyle = article.artworkStyle,
                size = 78.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    ),
                    color = style.contentPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = article.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = article.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDelete != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .bouncyClickable(
                            pressedScale = 0.86f,
                            contentDescriptionText = "Remove ${article.title}",
                            onClick = onDelete
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove ${article.title}",
                        tint = style.contentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksSectionList(
    bookmarks: List<BookmarkEntity>,
    headerColor: Color,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onEdgeBounce: (Float) -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()

    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        LazyColumn(
            flingBehavior = momentumFling,
            contentPadding = PaddingValues(
                top = topContentPadding,
                bottom = bottomContentPadding,
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = metrics.contentMaxWidth)
                .fillMaxSize()
                .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
        ) {
            item {
                SectionHeaderRow(
                    title = stringResource(R.string.bookmarks_title),
                    actionText = null,
                    titleColor = headerColor,
                    actionColor = headerColor
                )
            }
            if (bookmarks.isEmpty()) {
                item {
                    EmptyHubGlassCard(
                        icon = {
                            Icon(
                                Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                tint = style.contentPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        message = stringResource(R.string.empty_bookmarks_hint)
                    )
                }
            } else {
                items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                    FrostedGlassBox(
                        shape = RoundedCornerShape(22.dp),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable(
                                pressedScale = 0.96f,
                                contentDescriptionText = bm.title,
                                onClick = { onOpenUrl(bm.url) }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            FrostedGlassBox(
                                shape = CircleShape,
                                elevation = GlassElevation.FLOATING_BAR,
                                useLiveSceneBackdrop = false,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = style.contentPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bm.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = style.contentSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .bouncyClickable(
                                        pressedScale = 0.85f,
                                        contentDescriptionText = "Delete bookmark",
                                        onClick = { onDeleteBookmark(bm.id) }
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Delete bookmark",
                                    tint = style.contentSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullReadingListSection(
    items: List<ReadingListItemEntity>,
    headerColor: Color,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onOpenUrl: (String) -> Unit,
    onDeleteReadingItem: (Long) -> Unit,
    onEdgeBounce: (Float) -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()

    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        LazyColumn(
            flingBehavior = momentumFling,
            contentPadding = PaddingValues(
                top = topContentPadding,
                bottom = bottomContentPadding,
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = metrics.contentMaxWidth)
                .fillMaxSize()
                .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
        ) {
            item {
                SectionHeaderRow(
                    title = stringResource(R.string.reading_list_title),
                    actionText = null,
                    titleColor = headerColor,
                    actionColor = headerColor
                )
            }
            if (items.isEmpty()) {
                item {
                    EmptyHubGlassCard(
                        icon = {
                            EyeglassesIcon(tint = style.contentPrimary, modifier = Modifier.size(30.dp))
                        },
                        message = stringResource(R.string.empty_reading_list_hint)
                    )
                }
            } else {
                items(items, key = { "full_rl_${it.id}" }) { article ->
                    ReadingListGlassCard(
                        article = article,
                        onClick = { onOpenUrl(article.url) },
                        onDelete = { onDeleteReadingItem(article.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySectionList(
    entries: List<HistoryEntryEntity>,
    headerColor: Color,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onEdgeBounce: (Float) -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        LazyColumn(
            flingBehavior = momentumFling,
            contentPadding = PaddingValues(
                top = topContentPadding,
                bottom = bottomContentPadding,
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = metrics.contentMaxWidth)
                .fillMaxSize()
                .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = headerColor
                    )
                    if (entries.isNotEmpty()) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(50),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier
                                .height(48.dp)
                                .bouncyClickable(
                                    pressedScale = 0.90f,
                                    contentDescriptionText = stringResource(R.string.clear_history),
                                    onClick = onClearHistory
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = stringResource(R.string.clear_history),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = style.contentPrimary
                                )
                            }
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyHubGlassCard(
                        icon = {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = null,
                                tint = style.contentPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        message = stringResource(R.string.empty_history_hint)
                    )
                }
            } else {
                items(entries, key = { "hist_${it.id}" }) { entry ->
                    FrostedGlassBox(
                        shape = RoundedCornerShape(22.dp),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable(
                                pressedScale = 0.96f,
                                contentDescriptionText = entry.title,
                                onClick = { onOpenUrl(entry.url) }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = style.contentPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.domain,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = style.contentSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = timeFormatter.format(Date(entry.visitedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = style.contentMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHubGlassCard(
    icon: @Composable () -> Unit,
    message: String
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(24.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            icon()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = style.contentSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
