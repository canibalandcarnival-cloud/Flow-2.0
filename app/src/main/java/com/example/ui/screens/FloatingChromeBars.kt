package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.data.BrowserRepository
import com.example.ui.TopHubSection
import com.example.ui.components.EyeglassesIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.OverlappingTabsIcon
import com.example.ui.components.bouncyClickable
import com.example.ui.components.draggableGlassSurface
import com.example.ui.theme.LocalGlassStyle

/**
 * Top floating 5-segment frosted glass capsule pill:
 * [Start Page] | [Bookmarks] | [Reading List] | [History] | [Downloads]
 */
@Composable
fun TopSegmentedGlassBar(
    activeSection: TopHubSection,
    activeDownloadsCount: Int = 0,
    onSelectSection: (TopHubSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val sections = TopHubSection.entries
    val selectedIndex = sections.indexOf(activeSection).coerceAtLeast(0)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        FrostedGlassBox(
            shape = RoundedCornerShape(50),
            elevation = GlassElevation.FLOATING_BAR,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .widthIn(max = metrics.chromeBarMaxWidth)
                .fillMaxWidth()
                .height(48.dp)
                .testTag("top_segmented_glass_bar")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / sections.size
                val indicatorOffset by animateDpAsState(
                    targetValue = segmentWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.64f,
                        stiffness = 380f
                    ),
                    label = "topPillIndicatorOffset"
                )

                // Sliding active frosted capsule
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(style.activeSegmentFill)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    sections.forEach { section ->
                        val isSelected = section == activeSection
                        val iconTint = if (isSelected) style.contentPrimary else style.contentSecondary
                        val labelDesc = when (section) {
                            TopHubSection.START_PAGE -> stringResource(R.string.start_page_title)
                            TopHubSection.BOOKMARKS -> stringResource(R.string.bookmarks_title)
                            TopHubSection.READING_LIST -> stringResource(R.string.reading_list_title)
                            TopHubSection.HISTORY -> stringResource(R.string.history_title)
                            TopHubSection.DOWNLOADS -> stringResource(R.string.downloads_title)
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("segment_${section.name.lowercase()}")
                                .bouncyClickable(
                                    pressedScale = 0.88f,
                                    contentDescriptionText = labelDesc,
                                    onClick = { onSelectSection(section) }
                                )
                        ) {
                            when (section) {
                                TopHubSection.START_PAGE -> Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Apps else Icons.Outlined.Apps,
                                    contentDescription = labelDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(21.dp)
                                )
                                TopHubSection.BOOKMARKS -> Icon(
                                    imageVector = if (isSelected) Icons.Rounded.MenuBook else Icons.Outlined.MenuBook,
                                    contentDescription = labelDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                                TopHubSection.READING_LIST -> EyeglassesIcon(
                                    tint = iconTint,
                                    modifier = Modifier.size(23.dp)
                                )
                                TopHubSection.HISTORY -> Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Schedule else Icons.Outlined.Schedule,
                                    contentDescription = labelDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                                TopHubSection.DOWNLOADS -> Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Rounded.Download else Icons.Outlined.Download,
                                        contentDescription = labelDesc,
                                        tint = iconTint,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    if (activeDownloadsCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 3.dp, y = (-2).dp)
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF36E2FF))
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

/**
 * Bottom floating & movable frosted-glass browser bar:
 * [ < Back ]  [ Search / Domain + WebTools ]  [ Siri iOS 26 Orb ]  [ Tabs ]
 *
 * Supports smooth drag repositioning (`draggableGlassSurface`) so moving the bar dynamically
 * refracts the live webpage or wallpaper directly underneath it!
 */
@Composable
fun BottomBrowserGlassBar(
    currentUrl: String,
    canNavigateBack: Boolean,
    isLoading: Boolean,
    loadProgress: Float,
    isPrivateMode: Boolean,
    isSiriActive: Boolean = false,
    onBackClick: () -> Unit,
    onAddressBarClick: () -> Unit,
    onWebToolsOrVoiceClick: () -> Unit,
    onSiriClick: () -> Unit,
    onTabsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val isWebActive = currentUrl.isNotBlank()
    val animatedProgress by animateFloatAsState(
        targetValue = if (isLoading) loadProgress.coerceIn(0.06f, 1f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "webProgress"
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .draggableGlassSurface(
                maxHorizontalOffsetPx = 320f,
                maxVerticalOffsetPx = 900f
            )
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (metrics.isUltraCompactPhone) 6.dp else 8.dp),
            modifier = Modifier
                .widthIn(max = metrics.chromeBarMaxWidth)
                .fillMaxWidth()
        ) {
            // Left Circular Frosted Back Button
            FrostedGlassBox(
                shape = CircleShape,
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .size(metrics.floatingActionSize)
                    .testTag("back_glass_button")
                    .bouncyClickable(
                        pressedScale = 0.88f,
                        enabled = canNavigateBack,
                        contentDescriptionText = stringResource(R.string.cd_back_button),
                        onClick = onBackClick
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back_button),
                    tint = if (canNavigateBack) style.contentPrimary else style.contentMuted,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Center Capsule Frosted Address / Search Pill
            FrostedGlassBox(
                shape = RoundedCornerShape(50),
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("address_bar_pill")
                    .bouncyClickable(
                        pressedScale = 0.96f,
                        contentDescriptionText = stringResource(R.string.cd_search_bar),
                        onClick = onAddressBarClick
                    )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isWebActive) Icons.Rounded.Lock else Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (isWebActive) style.contentSecondary else style.contentPrimary,
                            modifier = Modifier.size(if (isWebActive) 15.dp else 19.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val displayText = if (isWebActive) {
                            val domain = BrowserRepository.extractDomain(currentUrl)
                            if (isPrivateMode) "Private • $domain" else domain
                        } else {
                            if (isPrivateMode) "Private • Search or web"
                            else stringResource(R.string.search_or_enter_website)
                        }

                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isWebActive) style.contentPrimary else style.contentSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("web_tools_or_voice_button")
                                .bouncyClickable(
                                    pressedScale = 0.85f,
                                    contentDescriptionText = if (isWebActive) {
                                        stringResource(R.string.cd_web_tools_button)
                                    } else {
                                        stringResource(R.string.cd_voice_search)
                                    },
                                    onClick = onWebToolsOrVoiceClick
                                )
                        ) {
                            Icon(
                                imageVector = if (isWebActive) Icons.Outlined.MoreHoriz else Icons.Outlined.MicNone,
                                contentDescription = if (isWebActive) {
                                    stringResource(R.string.cd_web_tools_button)
                                } else {
                                    stringResource(R.string.cd_voice_search)
                                },
                                tint = style.contentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Smooth loading progress bar inside capsule bottom edge
                    if (animatedProgress > 0.01f && isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(animatedProgress)
                                .height(2.dp)
                                .background(style.contentPrimary)
                        )
                    }
                }
            }

            // Siri iOS 26 Liquid-Glass Orb Button
            FrostedGlassBox(
                shape = CircleShape,
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .size(metrics.floatingActionSize)
                    .testTag("siri_orb_glass_button")
                    .bouncyClickable(
                        pressedScale = 0.86f,
                        contentDescriptionText = stringResource(R.string.cd_siri_orb_button),
                        onClick = onSiriClick
                    )
            ) {
                SiriLiquidOrbIcon(
                    isThinking = isSiriActive,
                    size = 26.dp
                )
            }

            // Right Circular Frosted Tabs Button
            FrostedGlassBox(
                shape = CircleShape,
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .size(metrics.floatingActionSize)
                    .testTag("tabs_switcher_button")
                    .bouncyClickable(
                        pressedScale = 0.88f,
                        contentDescriptionText = stringResource(R.string.cd_tabs_button),
                        onClick = onTabsClick
                    )
            ) {
                OverlappingTabsIcon(
                    tint = style.contentPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
