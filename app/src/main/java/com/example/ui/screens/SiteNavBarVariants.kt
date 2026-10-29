package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.core.SiteNavItem
import com.example.core.SiteNavigationBarData
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.OverlappingTabsIcon
import com.example.ui.components.SafariChevronIcon
import com.example.ui.components.SafariOpenBookIcon
import com.example.ui.components.SafariPageMenuIcon
import com.example.ui.components.SafariReloadIcon
import com.example.ui.components.SafariShareIcon
import com.example.ui.components.bouncyClickable
import com.example.ui.components.draggableGlassSurface
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.theme.LocalGlassStyle

/**
 * 1. YOUTUBE & VIDEO ISLAND (`youtube.com`, `youtu.be`, `vimeo.com`, `twitch.tv`)
 * Exact 2-tier iOS 26 Safari dark smoked-glass island docked beneath YouTube's original bottom bar:
 * - Top recessed dark capsule: `[ ⊟ Page Menu ]   youtube.com   [ ↻ Reload ]`
 * - Bottom 5-icon row: `<`  `>`  `[↑ Share]`  `[📖 Bookmarks]`  `[⧉ Tabs]`
 */
@Composable
internal fun YouTubeSafariIslandBar(
    domainLabel: String,
    isLoading: Boolean,
    animatedProgress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    barMaxWidth: Dp,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadOrStopClick: () -> Unit,
    onShareClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onWebToolsClick: () -> Unit,
    onTabsClick: () -> Unit
) {
    val islandShape = RoundedCornerShape(28.dp)
    val capsuleShape = RoundedCornerShape(50)
    val rimBrush = Brush.verticalGradient(
        listOf(Color(0xFF434349), Color(0xFF2A2A2F))
    )

    FrostedGlassBox(
        shape = islandShape,
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        customTintAlpha = 0.24f,
        modifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .draggableGlassSurface(maxHorizontalOffsetPx = 280f, maxVerticalOffsetPx = 560f)
            .testTag("site_custom_navigation_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212124).copy(alpha = 0.90f))
                .border(width = 1.dp, brush = rimBrush, shape = islandShape)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 5.dp)
        ) {
            // Top Recessed Dark Address Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(capsuleShape)
                    .background(Color(0xFF111113))
                    .border(width = 0.75.dp, color = Color(0xFF2B2B30), shape = capsuleShape)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_tools_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_web_tools_button),
                                onClick = onWebToolsClick
                            )
                    ) {
                        SafariPageMenuIcon(
                            tint = Color(0xFFEBEBF0),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_domain_pill")
                            .bouncyClickable(
                                pressedScale = 0.96f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_domain),
                                onClick = onDomainClick
                            )
                    ) {
                        Text(
                            text = domainLabel,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.5.sp,
                                letterSpacing = (-0.15).sp
                            ),
                            color = Color(0xFFF5F5F7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_reload_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_reload),
                                onClick = onReloadOrStopClick
                            )
                    ) {
                        if (isLoading) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_site_nav_reload),
                                tint = Color(0xFFEBEBF0),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            SafariReloadIcon(
                                tint = Color(0xFFEBEBF0),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (animatedProgress > 0.01f && isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth(animatedProgress)
                            .height(2.dp)
                            .background(Color(0xFFFF2D55))
                    )
                }
            }

            // Bottom 5-Icon Safari Action Row (<, >, Share, Bookmarks, Tabs)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp)
            ) {
                SafariToolbarIconButton(
                    testTag = "site_nav_back_button",
                    contentDesc = stringResource(R.string.cd_back_button),
                    onClick = onBackClick
                ) {
                    SafariChevronIcon(
                        isLeft = true,
                        tint = if (canGoBack) Color(0xFFF2F2F7) else Color(0xFF7D7D84),
                        modifier = Modifier.size(21.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_forward_button",
                    contentDesc = stringResource(R.string.cd_forward_button),
                    onClick = { if (canGoForward) onForwardClick() }
                ) {
                    SafariChevronIcon(
                        isLeft = false,
                        tint = if (canGoForward) Color(0xFFF2F2F7) else Color(0xFF636369),
                        modifier = Modifier.size(21.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_share_button",
                    contentDesc = stringResource(R.string.cd_site_nav_share),
                    onClick = onShareClick
                ) {
                    SafariShareIcon(
                        tint = Color(0xFFF2F2F7),
                        modifier = Modifier.size(21.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_bookmarks_button",
                    contentDesc = stringResource(R.string.cd_site_nav_bookmarks),
                    onClick = onBookmarksClick
                ) {
                    SafariOpenBookIcon(
                        tint = Color(0xFFF2F2F7),
                        modifier = Modifier.size(22.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_tabs_button",
                    contentDesc = stringResource(R.string.cd_tabs_button),
                    onClick = onTabsClick
                ) {
                    OverlappingTabsIcon(
                        tint = Color(0xFFF2F2F7),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2. EDITORIAL READER DECK (`wikipedia.org`, `dezeen.com`, `archdaily.com`, `nytimes.com`, etc.)
 * Warm split-deck card with live article chapter chips on top and 1-tap `AA Reader` toggle on bottom.
 */
@Composable
internal fun EditorialReaderDeckBar(
    navData: SiteNavigationBarData,
    domainLabel: String,
    isLoading: Boolean,
    animatedProgress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isReaderMode: Boolean,
    isSiriActive: Boolean,
    listState: LazyListState,
    barMaxWidth: Dp,
    onHomeClick: () -> Unit,
    onNavItemClick: (SiteNavItem) -> Unit,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onToggleReaderClick: () -> Unit,
    onSiriClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onTabsClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    val shape = RoundedCornerShape(24.dp)

    FrostedGlassBox(
        shape = shape,
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        modifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .draggableGlassSurface(maxHorizontalOffsetPx = 280f, maxVerticalOffsetPx = 560f)
            .testTag("site_custom_navigation_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.9.dp, Color(0xFFD8B88A).copy(alpha = 0.34f), shape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            SiteExtractedLinksStrip(
                navData = navData,
                listState = listState,
                onHomeClick = onHomeClick,
                onNavItemClick = onNavItemClick
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                SafariToolbarIconButton(
                    testTag = "site_nav_back_button",
                    contentDesc = stringResource(R.string.cd_back_button),
                    onClick = onBackClick
                ) {
                    SafariChevronIcon(
                        isLeft = true,
                        tint = if (canGoBack) style.contentPrimary else style.contentMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Editorial AA Reader + Domain Center Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(style.activeSegmentFill)
                        .border(0.75.dp, style.rimTopLeft, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isReaderMode) Color(0xFFD99B4E) else style.solidActionFill.copy(alpha = 0.14f)
                            )
                            .testTag("site_nav_reader_button")
                            .bouncyClickable(
                                pressedScale = 0.90f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_reader),
                                onClick = onToggleReaderClick
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "AA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            color = if (isReaderMode) Color(0xFF1A1208) else style.contentPrimary
                        )
                    }

                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_domain_pill")
                            .bouncyClickable(
                                pressedScale = 0.96f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_domain),
                                onClick = onDomainClick
                            )
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = domainLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = style.contentPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (canGoForward) {
                    SafariToolbarIconButton(
                        testTag = "site_nav_forward_button",
                        contentDesc = stringResource(R.string.cd_forward_button),
                        onClick = onForwardClick
                    ) {
                        SafariChevronIcon(isLeft = false, tint = style.contentPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_siri_button",
                    contentDesc = stringResource(R.string.cd_siri_orb_button),
                    onClick = onSiriClick
                ) {
                    SiriLiquidOrbIcon(isThinking = isSiriActive, size = 21.dp)
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_bookmarks_button",
                    contentDesc = stringResource(R.string.cd_site_nav_bookmarks),
                    onClick = onBookmarksClick
                ) {
                    SafariOpenBookIcon(tint = style.contentPrimary, modifier = Modifier.size(20.dp))
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_tabs_button",
                    contentDesc = stringResource(R.string.cd_tabs_button),
                    onClick = onTabsClick
                ) {
                    OverlappingTabsIcon(tint = style.contentPrimary, modifier = Modifier.size(19.dp))
                }
            }

            if (animatedProgress > 0.01f && isLoading) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(Color(0xFFD99B4E))
                )
            }
        }
    }
}

/**
 * 3. DEVELOPER COMMAND HUD (`github.com`, `news.ycombinator.com`, `developer.mozilla.org`, etc.)
 * Architectural 16dp slate HUD with cyan specular accent, repo/HN nav strip, Find-in-Page & Desktop toggles.
 */
@Composable
internal fun DevCommandHudBar(
    navData: SiteNavigationBarData,
    domainLabel: String,
    isLoading: Boolean,
    animatedProgress: Float,
    canGoBack: Boolean,
    isDesktopMode: Boolean,
    isSiriActive: Boolean,
    listState: LazyListState,
    barMaxWidth: Dp,
    onHomeClick: () -> Unit,
    onNavItemClick: (SiteNavItem) -> Unit,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onOpenFindClick: () -> Unit,
    onToggleDesktopClick: () -> Unit,
    onSiriClick: () -> Unit,
    onTabsClick: () -> Unit
) {
    val hudShape = RoundedCornerShape(18.dp)
    val cyanAccent = Color(0xFF5AC8FA)

    FrostedGlassBox(
        shape = hudShape,
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        customTintAlpha = 0.22f,
        modifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .draggableGlassSurface(maxHorizontalOffsetPx = 280f, maxVerticalOffsetPx = 560f)
            .testTag("site_custom_navigation_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141820).copy(alpha = 0.88f))
                .border(1.dp, cyanAccent.copy(alpha = 0.35f), hudShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            SiteExtractedLinksStrip(
                navData = navData,
                listState = listState,
                forceLightContent = true,
                accentColor = cyanAccent,
                onHomeClick = onHomeClick,
                onNavItemClick = onNavItemClick
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                SafariToolbarIconButton(
                    testTag = "site_nav_back_button",
                    contentDesc = stringResource(R.string.cd_back_button),
                    onClick = onBackClick
                ) {
                    SafariChevronIcon(
                        isLeft = true,
                        tint = if (canGoBack) Color(0xFFF2F6FA) else Color(0xFF6B7785),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0E14))
                        .border(0.75.dp, Color(0xFF283242), RoundedCornerShape(10.dp))
                        .testTag("site_nav_domain_pill")
                        .bouncyClickable(
                            pressedScale = 0.96f,
                            contentDescriptionText = stringResource(R.string.cd_site_nav_domain),
                            onClick = onDomainClick
                        )
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = "~/ $domainLabel",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = Color(0xFFD9E8F6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_find_button",
                    contentDesc = stringResource(R.string.cd_site_nav_find),
                    onClick = onOpenFindClick
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.cd_site_nav_find),
                        tint = Color(0xFFE4EEF8),
                        modifier = Modifier.size(19.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_desktop_button",
                    contentDesc = stringResource(R.string.cd_site_nav_desktop),
                    onClick = onToggleDesktopClick
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DesktopWindows,
                        contentDescription = stringResource(R.string.cd_site_nav_desktop),
                        tint = if (isDesktopMode) cyanAccent else Color(0xFFE4EEF8),
                        modifier = Modifier.size(19.dp)
                    )
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_siri_button",
                    contentDesc = stringResource(R.string.cd_siri_orb_button),
                    onClick = onSiriClick
                ) {
                    SiriLiquidOrbIcon(isThinking = isSiriActive, size = 21.dp)
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_tabs_button",
                    contentDesc = stringResource(R.string.cd_tabs_button),
                    onClick = onTabsClick
                ) {
                    OverlappingTabsIcon(tint = Color(0xFFE4EEF8), modifier = Modifier.size(19.dp))
                }
            }

            if (animatedProgress > 0.01f && isLoading) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(cyanAccent)
                )
            }
        }
    }
}

/**
 * 4. SOCIAL & VISUAL FLOATING ORBS (`pinterest.com`, `reddit.com`, `are.na`, `awwwards.com`, `x.com`, etc.)
 * Detached 3-piece floating Liquid-Glass bubble trio: `( < )` + `( Brand  domain  ↻ )` + `( ↑  🔮  ⧉ )`.
 */
@Composable
internal fun SocialFloatingOrbsBar(
    navData: SiteNavigationBarData,
    domainLabel: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    isSiriActive: Boolean,
    barMaxWidth: Dp,
    onHomeClick: () -> Unit,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onReloadOrStopClick: () -> Unit,
    onShareClick: () -> Unit,
    onSiriClick: () -> Unit,
    onTabsClick: () -> Unit
) {
    val style = LocalGlassStyle.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .draggableGlassSurface(maxHorizontalOffsetPx = 280f, maxVerticalOffsetPx = 560f)
            .testTag("site_custom_navigation_bar")
    ) {
        // Left Detached Orb: Back
        FrostedGlassBox(
            shape = CircleShape,
            elevation = GlassElevation.FLOATING_BAR,
            useLiveSceneBackdrop = true,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .minimumInteractiveComponentSize()
                    .testTag("site_nav_back_button")
                    .bouncyClickable(
                        pressedScale = 0.88f,
                        contentDescriptionText = stringResource(R.string.cd_back_button),
                        onClick = onBackClick
                    )
            ) {
                SafariChevronIcon(
                    isLeft = true,
                    tint = if (canGoBack) style.contentPrimary else style.contentMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Center Detached Brand + Domain Capsule
        FrostedGlassBox(
            shape = RoundedCornerShape(50),
            elevation = GlassElevation.FLOATING_BAR,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(style.solidActionFill)
                        .testTag("site_nav_home_button")
                        .bouncyClickable(
                            pressedScale = 0.88f,
                            contentDescriptionText = stringResource(R.string.cd_site_nav_home),
                            onClick = onHomeClick
                        )
                ) {
                    Text(
                        text = navData.monogram.take(2),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        color = style.solidActionContent
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .minimumInteractiveComponentSize()
                        .testTag("site_nav_domain_pill")
                        .bouncyClickable(
                            pressedScale = 0.96f,
                            contentDescriptionText = stringResource(R.string.cd_site_nav_domain),
                            onClick = onDomainClick
                        )
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = domainLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = style.contentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("site_nav_reload_button")
                        .bouncyClickable(
                            pressedScale = 0.88f,
                            contentDescriptionText = stringResource(R.string.cd_site_nav_reload),
                            onClick = onReloadOrStopClick
                        )
                ) {
                    if (isLoading) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_site_nav_reload),
                            tint = style.contentPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    } else {
                        SafariReloadIcon(tint = style.contentPrimary, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }

        // Right Detached Action Cluster (Share + Siri + Tabs)
        FrostedGlassBox(
            shape = RoundedCornerShape(50),
            elevation = GlassElevation.FLOATING_BAR,
            useLiveSceneBackdrop = true,
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                SafariToolbarIconButton(
                    testTag = "site_nav_share_button",
                    contentDesc = stringResource(R.string.cd_site_nav_share),
                    onClick = onShareClick
                ) {
                    SafariShareIcon(tint = style.contentPrimary, modifier = Modifier.size(19.dp))
                }
                SafariToolbarIconButton(
                    testTag = "site_nav_siri_button",
                    contentDesc = stringResource(R.string.cd_siri_orb_button),
                    onClick = onSiriClick
                ) {
                    SiriLiquidOrbIcon(isThinking = isSiriActive, size = 20.dp)
                }
                SafariToolbarIconButton(
                    testTag = "site_nav_tabs_button",
                    contentDesc = stringResource(R.string.cd_tabs_button),
                    onClick = onTabsClick
                ) {
                    OverlappingTabsIcon(tint = style.contentPrimary, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/**
 * 5. UNIVERSAL LIQUID CAPSULE (`google.com` and all other websites)
 * Dynamic 2-tier rounded Liquid-Glass dock combining extracted site links with recessed Safari controls.
 */
@Composable
internal fun UniversalLiquidCapsuleBar(
    navData: SiteNavigationBarData,
    domainLabel: String,
    isLoading: Boolean,
    animatedProgress: Float,
    canGoBack: Boolean,
    isSiriActive: Boolean,
    listState: LazyListState,
    barMaxWidth: Dp,
    onHomeClick: () -> Unit,
    onNavItemClick: (SiteNavItem) -> Unit,
    onDomainClick: () -> Unit,
    onBackClick: () -> Unit,
    onReloadOrStopClick: () -> Unit,
    onSiriClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onWebToolsClick: () -> Unit,
    onTabsClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    val shape = RoundedCornerShape(28.dp)

    FrostedGlassBox(
        shape = shape,
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        modifier = Modifier
            .widthIn(max = barMaxWidth)
            .fillMaxWidth()
            .draggableGlassSurface(maxHorizontalOffsetPx = 280f, maxVerticalOffsetPx = 560f)
            .testTag("site_custom_navigation_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (navData.items.isNotEmpty()) {
                SiteExtractedLinksStrip(
                    navData = navData,
                    listState = listState,
                    onHomeClick = onHomeClick,
                    onNavItemClick = onNavItemClick
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                SafariToolbarIconButton(
                    testTag = "site_nav_back_button",
                    contentDesc = stringResource(R.string.cd_back_button),
                    onClick = onBackClick
                ) {
                    SafariChevronIcon(
                        isLeft = true,
                        tint = if (canGoBack) style.contentPrimary else style.contentMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Recessed URL & Page Menu Capsule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(style.activeSegmentFill)
                        .border(0.75.dp, style.rimTopLeft, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_tools_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_web_tools_button),
                                onClick = onWebToolsClick
                            )
                    ) {
                        SafariPageMenuIcon(tint = style.contentPrimary, modifier = Modifier.size(17.dp))
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_domain_pill")
                            .bouncyClickable(
                                pressedScale = 0.96f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_domain),
                                onClick = onDomainClick
                            )
                    ) {
                        Text(
                            text = domainLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = style.contentPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("site_nav_reload_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_site_nav_reload),
                                onClick = onReloadOrStopClick
                            )
                    ) {
                        if (isLoading) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_site_nav_reload),
                                tint = style.contentPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        } else {
                            SafariReloadIcon(tint = style.contentPrimary, modifier = Modifier.size(17.dp))
                        }
                    }
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_siri_button",
                    contentDesc = stringResource(R.string.cd_siri_orb_button),
                    onClick = onSiriClick
                ) {
                    SiriLiquidOrbIcon(isThinking = isSiriActive, size = 21.dp)
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_bookmarks_button",
                    contentDesc = stringResource(R.string.cd_site_nav_bookmarks),
                    onClick = onBookmarksClick
                ) {
                    SafariOpenBookIcon(tint = style.contentPrimary, modifier = Modifier.size(20.dp))
                }

                SafariToolbarIconButton(
                    testTag = "site_nav_tabs_button",
                    contentDesc = stringResource(R.string.cd_tabs_button),
                    onClick = onTabsClick
                ) {
                    OverlappingTabsIcon(tint = style.contentPrimary, modifier = Modifier.size(19.dp))
                }
            }

            if (animatedProgress > 0.01f && isLoading) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(style.contentPrimary)
                )
            }
        }
    }
}

@Composable
private fun SafariToolbarIconButton(
    testTag: String,
    contentDesc: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .testTag(testTag)
            .bouncyClickable(
                pressedScale = 0.86f,
                contentDescriptionText = contentDesc,
                onClick = onClick
            )
    ) {
        icon()
    }
}

@Composable
private fun SiteExtractedLinksStrip(
    navData: SiteNavigationBarData,
    listState: LazyListState,
    forceLightContent: Boolean = false,
    accentColor: Color? = null,
    onHomeClick: () -> Unit,
    onNavItemClick: (SiteNavItem) -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()
    val primaryFg = if (forceLightContent) Color(0xFFF2F6FA) else style.contentPrimary
    val secondaryFg = if (forceLightContent) Color(0xFFA7B5C6) else style.contentSecondary
    val pillBg = if (forceLightContent) Color(0xFF222A38) else style.activeSegmentFill
    val pillRim = accentColor?.copy(alpha = 0.45f) ?: style.rimTopLeft

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .minimumInteractiveComponentSize()
                .testTag("site_nav_home_button")
                .bouncyClickable(
                    pressedScale = 0.91f,
                    contentDescriptionText = stringResource(R.string.cd_site_nav_home),
                    onClick = onHomeClick
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .border(0.75.dp, pillRim, RoundedCornerShape(50))
                    .padding(start = 5.dp, end = 9.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accentColor ?: style.solidActionFill)
                ) {
                    Text(
                        text = navData.monogram.take(2),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp
                        ),
                        color = if (accentColor != null) Color(0xFF081018) else style.solidActionContent
                    )
                }
                Text(
                    text = navData.siteBrand.ifBlank { stringResource(R.string.site_nav_home_fallback) },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = primaryFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 88.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(pillRim.copy(alpha = 0.45f))
        )

        LazyRow(
            state = listState,
            flingBehavior = momentumFling,
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("site_nav_items_row")
        ) {
            itemsIndexed(
                items = navData.items,
                key = { index, item -> "${index}_${item.url}_${item.label}" }
            ) { index, item ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(42.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("site_nav_item_$index")
                        .bouncyClickable(
                            pressedScale = 0.91f,
                            contentDescriptionText = item.label,
                            onClick = { onNavItemClick(item) }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (item.isActive) {
                                    Modifier
                                        .background(pillBg)
                                        .border(0.75.dp, pillRim, RoundedCornerShape(50))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        if (item.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(accentColor ?: primaryFg)
                            )
                        } else if (item.isAnchor) {
                            Text(
                                text = "#",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                ),
                                color = secondaryFg
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (item.isActive) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (item.isActive) primaryFg else secondaryFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
