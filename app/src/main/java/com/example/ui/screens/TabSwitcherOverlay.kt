package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BrowserTabEntity
import com.example.data.FavoriteSiteEntity
import com.example.ui.BrowserUiState
import com.example.ui.TabGroupSummary
import com.example.ui.components.FavoriteSquircleIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.components.rubberBandOverscroll
import com.example.ui.theme.LocalGlassStyle
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

private sealed interface TabGridEntry {
    val key: String

    data class SingleTab(val tab: BrowserTabEntity) : TabGridEntry {
        override val key: String = "tab_${tab.id}"
    }

    data class GroupCard(val summary: TabGroupSummary) : TabGridEntry {
        override val key: String = "group_${summary.groupName}"
    }
}

/**
 * Full-screen spatial frosted-glass Tab Switcher matching Reference Image 1,
 * blending dynamically with the content behind it and strictly using pure White / Black monochrome colors.
 */
@Composable
fun TabSwitcherOverlay(
    uiState: BrowserUiState,
    tabSnapshots: Map<Long, ImageBitmap>,
    onSelectTab: (BrowserTabEntity) -> Unit,
    onCloseTab: (BrowserTabEntity) -> Unit,
    onNewTab: () -> Unit,
    onDone: () -> Unit,
    onSwitchPrivacyMode: (Boolean) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectGroupFilter: (String?) -> Unit,
    onOpenNewGroupDialog: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()

    // Filter tabs by privacy mode & search query
    val query = uiState.tabSearchQuery.trim()
    val filteredTabs = uiState.modeTabs.filter { tab ->
        query.isEmpty() ||
            tab.title.contains(query, ignoreCase = true) ||
            tab.url.contains(query, ignoreCase = true) ||
            (tab.groupName?.contains(query, ignoreCase = true) == true)
    }

    val gridEntries: List<TabGridEntry> = if (uiState.activeTabGroupFilter != null) {
        filteredTabs
            .filter { it.groupName == uiState.activeTabGroupFilter }
            .map { TabGridEntry.SingleTab(it) }
    } else {
        val ungrouped = filteredTabs.filter { it.groupName.isNullOrBlank() }
            .map { TabGridEntry.SingleTab(it) }
        val grouped = filteredTabs.filter { !it.groupName.isNullOrBlank() }
            .groupBy { it.groupName.orEmpty() }
            .map { (name, list) -> TabGridEntry.GroupCard(TabGroupSummary(name, list)) }
        // Interleave first ungrouped tab + first group card at top row just like Reference Image 1!
        buildList {
            if (ungrouped.isNotEmpty()) add(ungrouped.first())
            addAll(grouped)
            if (ungrouped.size > 1) addAll(ungrouped.drop(1))
        }
    }

    FrostedGlassBox(
        shape = RectangleShape,
        elevation = GlassElevation.MODAL_OVERLAY,
        useLiveSceneBackdrop = true,
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .testTag("tab_switcher_overlay")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrollable 2-Column Grid of Tab Cards & 2x2 Tab Group Cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                flingBehavior = momentumFling,
                contentPadding = PaddingValues(
                    top = if (uiState.isTabSearchActive || uiState.activeTabGroupFilter != null) 142.dp else 92.dp,
                    bottom = 118.dp,
                    start = 18.dp,
                    end = 18.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .testTag("tabs_grid")
                    .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
            ) {
                items(
                    items = gridEntries,
                    key = { it.key }
                ) { entry ->
                    when (entry) {
                        is TabGridEntry.SingleTab -> IndividualTabGridItem(
                            tab = entry.tab,
                            isActive = entry.tab.id == uiState.activeTab?.id,
                            snapshot = tabSnapshots[entry.tab.id],
                            wallpaperBitmap = uiState.resolvedWallpaper?.fullBitmap,
                            favorites = uiState.favorites,
                            onClick = { onSelectTab(entry.tab) },
                            onClose = { onCloseTab(entry.tab) }
                        )
                        is TabGridEntry.GroupCard -> TabGroup2x2CardItem(
                            summary = entry.summary,
                            tabSnapshots = tabSnapshots,
                            onClick = { onSelectGroupFilter(entry.summary.groupName) }
                        )
                    }
                }
            }

            // Top Floating Controls matching Reference Image 1: [Q] [=] ............ [...]
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Tabs Glass Circle
                        FrostedGlassBox(
                            shape = CircleShape,
                            elevation = GlassElevation.FLOATING_BAR,
                            useLiveSceneBackdrop = true,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tab_search_button")
                                .bouncyClickable(
                                    pressedScale = 0.88f,
                                    contentDescriptionText = stringResource(R.string.cd_search_tabs),
                                    onClick = onToggleSearch
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.cd_search_tabs),
                                tint = style.contentPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // Tab Groups Menu Glass Circle (= staggered lines like Reference Image 1)
                        FrostedGlassBox(
                            shape = CircleShape,
                            elevation = GlassElevation.FLOATING_BAR,
                            useLiveSceneBackdrop = true,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tab_groups_button")
                                .bouncyClickable(
                                    pressedScale = 0.88f,
                                    contentDescriptionText = stringResource(R.string.cd_tab_groups),
                                    onClick = {
                                        if (uiState.activeTabGroupFilter != null) {
                                            onSelectGroupFilter(null)
                                        } else {
                                            onOpenNewGroupDialog()
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.cd_tab_groups),
                                tint = style.contentPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Active Group Breadcrumb Pill if viewing inside a Tab Group
                    if (uiState.activeTabGroupFilter != null) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(50),
                            elevation = GlassElevation.FLOATING_BAR,
                            useLiveSceneBackdrop = true,
                            modifier = Modifier
                                .height(40.dp)
                                .bouncyClickable(
                                    pressedScale = 0.92f,
                                    contentDescriptionText = "Back to all tabs",
                                    onClick = { onSelectGroupFilter(null) }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = uiState.activeTabGroupFilter,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = style.contentPrimary
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = style.contentSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // More (...) Settings Glass Circle
                    FrostedGlassBox(
                        shape = CircleShape,
                        elevation = GlassElevation.FLOATING_BAR,
                        useLiveSceneBackdrop = true,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tab_more_settings_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_more_options),
                                onClick = onOpenSettings
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = style.contentPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Expandable Frosted Search Input
                AnimatedVisibility(
                    visible = uiState.isTabSearchActive,
                    enter = fadeIn() + expandVertically(spring(dampingRatio = 0.7f, stiffness = 380f)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(50),
                        elevation = GlassElevation.FLOATING_BAR,
                        useLiveSceneBackdrop = true,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = style.contentSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.tabSearchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_tabs_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = style.contentMuted
                                    )
                                }
                                BasicTextField(
                                    value = uiState.tabSearchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = style.contentPrimary
                                    ),
                                    cursorBrush = SolidColor(style.contentPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("tab_search_input")
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating Controls matching Reference Image 1 (Pure Monochrome Black/White!):
            // [ + ]     [ Private | 6 Tabs ]     [ ✓ ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Left Circular Frosted '+' New Tab Button
                FrostedGlassBox(
                    shape = CircleShape,
                    elevation = GlassElevation.FLOATING_BAR,
                    useLiveSceneBackdrop = true,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("new_tab_button")
                        .bouncyClickable(
                            pressedScale = 0.88f,
                            contentDescriptionText = stringResource(R.string.cd_new_tab),
                            onClick = onNewTab
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.cd_new_tab),
                        tint = style.contentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Center Frosted Segmented Pill: [ Private | N Tabs ]
                FrostedGlassBox(
                    shape = RoundedCornerShape(50),
                    elevation = GlassElevation.FLOATING_BAR,
                    useLiveSceneBackdrop = true,
                    modifier = Modifier
                        .width(204.dp)
                        .height(48.dp)
                        .testTag("privacy_mode_switcher")
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    ) {
                        val halfWidth = maxWidth / 2
                        val activeOffset by animateDpAsState(
                            targetValue = if (uiState.settings.isPrivateMode) 0.dp else halfWidth,
                            animationSpec = spring(dampingRatio = 0.64f, stiffness = 380f),
                            label = "privacyPillSlide"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = activeOffset)
                                .width(halfWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(style.activeSegmentFill)
                        )

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("private_mode_tab")
                                    .bouncyClickable(
                                        pressedScale = 0.92f,
                                        contentDescriptionText = stringResource(R.string.private_mode_label),
                                        onClick = { onSwitchPrivacyMode(true) }
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.private_mode_label),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (uiState.settings.isPrivateMode) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (uiState.settings.isPrivateMode) style.contentPrimary else style.contentSecondary
                                )
                            }

                            val count = uiState.standardTabCount.coerceAtLeast(1)
                            val tabsLabel = if (count == 1) {
                                stringResource(R.string.single_tab_label)
                            } else {
                                stringResource(R.string.tabs_count_format, count)
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("standard_mode_tab")
                                    .bouncyClickable(
                                        pressedScale = 0.92f,
                                        contentDescriptionText = tabsLabel,
                                        onClick = { onSwitchPrivacyMode(false) }
                                    )
                            ) {
                                Text(
                                    text = tabsLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (!uiState.settings.isPrivateMode) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (!uiState.settings.isPrivateMode) style.contentPrimary else style.contentSecondary
                                )
                            }
                        }
                    }
                }

                // Right Circular Done '✓' Button — Pure Monochrome (White in Dark Mode, Black in Light Mode; NO blue!)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(style.solidActionFill)
                        .testTag("done_tabs_button")
                        .bouncyClickable(
                            pressedScale = 0.88f,
                            contentDescriptionText = stringResource(R.string.cd_done_tabs),
                            onClick = onDone
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.cd_done_tabs),
                        tint = style.solidActionContent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndividualTabGridItem(
    tab: BrowserTabEntity,
    isActive: Boolean,
    snapshot: ImageBitmap?,
    wallpaperBitmap: ImageBitmap?,
    favorites: List<FavoriteSiteEntity>,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val style = LocalGlassStyle.current
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tab_card_${tab.id}")
            .bouncyClickable(
                pressedScale = 0.94f,
                contentDescriptionText = tab.title,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
                .clip(cardShape)
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) style.contentPrimary else style.rimTopLeft,
                    shape = cardShape
                )
        ) {
            if (tab.url.isBlank()) {
                // Mini Start Page preview matching bottom-left card in Reference Image 1
                MiniStartPagePreview(
                    wallpaperBitmap = wallpaperBitmap,
                    favorites = favorites.take(4)
                )
            } else if (snapshot != null) {
                Image(
                    bitmap = snapshot,
                    contentDescription = tab.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Editorial webpage preview card matching top-left card in Reference Image 1
                MiniArticlePagePreview(tab = tab)
            }

            // Top-right circular frosted 'X' Close Tab button
            FrostedGlassBox(
                shape = CircleShape,
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = false,
                customTintAlpha = 0.68f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp) // 48dp accessible touch target
                    .padding(10.dp)
                    .testTag("close_tab_${tab.id}")
                    .bouncyClickable(
                        pressedScale = 0.82f,
                        contentDescriptionText = stringResource(R.string.cd_close_tab),
                        onClick = onClose
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_close_tab),
                    tint = style.contentPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Caption row underneath tab card: [Site Icon] Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = if (tab.url.isBlank()) Icons.Outlined.Apps else Icons.Outlined.Public,
                contentDescription = null,
                tint = style.contentSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = style.contentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Frosted Glass 2x2 Tab Group Card matching the top-right group card in Reference Image 1.
 */
@Composable
private fun TabGroup2x2CardItem(
    summary: TabGroupSummary,
    tabSnapshots: Map<Long, ImageBitmap>,
    onClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    val previewTabs = summary.tabs.take(4)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tab_group_${summary.groupName}")
            .bouncyClickable(
                pressedScale = 0.94f,
                contentDescriptionText = "${summary.groupName} tab group",
                onClick = onClick
            )
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(22.dp),
            elevation = GlassElevation.CARD,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = summary.groupName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = style.contentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                // 2x2 Mini Preview Grid inside the Frosted Tab Group Card
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    for (row in 0 until 2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            for (col in 0 until 2) {
                                val index = row * 2 + col
                                val subTab = previewTabs.getOrNull(index)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (subTab != null) PureWhite else style.activeSegmentFill
                                        )
                                ) {
                                    if (subTab != null) {
                                        val snap = tabSnapshots[subTab.id]
                                        if (snap != null) {
                                            Image(
                                                bitmap = snap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.55f)
                                                        .height(3.dp)
                                                        .background(PureBlack.copy(alpha = 0.25f))
                                                )
                                                Text(
                                                    text = subTab.previewHeadline.ifBlank { subTab.title },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 8.sp,
                                                        lineHeight = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = PureBlack,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${summary.tabs.size} Tabs",
            style = MaterialTheme.typography.labelMedium,
            color = style.contentSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniStartPagePreview(
    wallpaperBitmap: ImageBitmap?,
    favorites: List<FavoriteSiteEntity>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A281E))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                favorites.forEach { fav ->
                    FavoriteSquircleIcon(
                        site = fav,
                        size = 28.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniArticlePagePreview(tab: BrowserTabEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        // Minimal monochrome editorial header bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(Color(0xFF1A1A1A))
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Text(
                text = tab.previewSubhead.ifBlank { "Web Article" }.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = PureBlack.copy(alpha = 0.50f),
                maxLines = 1
            )
            Text(
                text = tab.previewHeadline.ifBlank { tab.title },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                color = PureBlack,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tab.previewSubhead,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                ),
                color = PureBlack.copy(alpha = 0.65f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(Color(0xFFEAEAEA))
        )
    }
}
