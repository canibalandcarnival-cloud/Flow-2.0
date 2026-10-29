package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.components.draggableGlassSurface
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.components.rubberBandOverscroll
import com.example.ui.theme.LocalGlassStyle

@Immutable
data class WebHitContextTarget(
    val targetUrl: String,
    val imageUrl: String? = null,
    val isImage: Boolean = false
)

@Immutable
data class WebJsDialogRequest(
    val title: String,
    val message: String,
    val isConfirm: Boolean,
    val isPrompt: Boolean = false,
    val defaultPromptValue: String = "",
    val onConfirm: (String?) -> Unit,
    val onCancel: () -> Unit
)

/**
 * Floating & draggable Find-in-Page frosted glass bar (`WebView.findAllAsync`) that refracts
 * the scrolling webpage directly underneath it.
 */
@Composable
fun FindInPageGlassBar(
    query: String,
    activeMatchOrdinal: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
        FrostedGlassBox(
            shape = RoundedCornerShape(50),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .widthIn(max = metrics.chromeBarMaxWidth)
                .fillMaxWidth()
                .height(52.dp)
                .draggableGlassSurface(maxHorizontalOffsetPx = 380f, maxVerticalOffsetPx = 900f)
                .testTag("find_in_page_bar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = style.contentPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.web_find_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.contentMuted
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = style.contentPrimary
                        ),
                        cursorBrush = SolidColor(style.contentPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onFindNext() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("find_in_page_input")
                    )
                }

                if (query.isNotEmpty()) {
                    val matchLabel = if (totalMatches > 0) {
                        "${(activeMatchOrdinal + 1).coerceAtMost(totalMatches)}/$totalMatches"
                    } else {
                        "0/0"
                    }
                    Text(
                        text = matchLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = style.contentSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .bouncyClickable(
                            pressedScale = 0.85f,
                            enabled = totalMatches > 0,
                            contentDescriptionText = "Previous match",
                            onClick = onFindPrev
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = if (totalMatches > 0) style.contentPrimary else style.contentMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .bouncyClickable(
                            pressedScale = 0.85f,
                            enabled = totalMatches > 0,
                            contentDescriptionText = "Next match",
                            onClick = onFindNext
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = if (totalMatches > 0) style.contentPrimary else style.contentMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_find_in_page_button")
                        .bouncyClickable(
                            pressedScale = 0.85f,
                            contentDescriptionText = "Close find in page",
                            onClick = onClose
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close find in page",
                        tint = style.contentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Rich WebView Components & Controls Frosted Sheet:
 * - Forward / Reload / Stop Loading
 * - Desktop Site toggle & Reader View toggle
 * - Text Zoom (`-` / `100%` / `+`)
 * - Find in Page, Ask Siri Intelligence, Open Downloads Hub (with live badge)
 * - SSL Certificate & Connection Security summary + Copy URL + Share Page
 */
@Composable
fun WebToolsGlassSheet(
    currentUrl: String,
    pageTitle: String,
    sslCertificateSummary: String,
    canGoForward: Boolean,
    isLoading: Boolean,
    isDesktopMode: Boolean,
    isReaderMode: Boolean,
    textZoomPercent: Int,
    activeDownloadsCount: Int,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleDesktopMode: (Boolean) -> Unit,
    onToggleReaderMode: (Boolean) -> Unit,
    onChangeTextZoom: (Int) -> Unit,
    onOpenFindInPage: () -> Unit,
    onOpenSiriAi: () -> Unit,
    onOpenDownloadsHub: () -> Unit,
    onCopyUrl: () -> Unit,
    onSharePage: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()
    val isHttps = currentUrl.startsWith("https://", ignoreCase = true)

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
            FrostedGlassBox(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                elevation = GlassElevation.MODAL_OVERLAY,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .widthIn(max = metrics.sheetMaxWidth)
                    .fillMaxWidth()
                    .testTag("webview_tools_sheet")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                LazyColumn(
                    flingBehavior = momentumFling,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .rubberBandOverscroll()
                ) {
                    // Header + SSL Connection Info
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isHttps) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    tint = if (isHttps) Color(0xFF4ADE80) else style.contentSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = pageTitle.ifBlank { stringResource(R.string.web_tools_title) },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = style.contentPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = sslCertificateSummary.ifBlank {
                                            if (isHttps) "Encrypted HTTPS Connection" else "Unencrypted / Local Page"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = style.contentSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(style.activeSegmentFill)
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Close WebView tools",
                                        onClick = onDismiss
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close WebView tools",
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Primary Quick Actions Grid
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            WebToolActionTile(
                                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                                label = stringResource(R.string.web_forward_action),
                                enabled = canGoForward,
                                tag = "web_tool_forward",
                                onClick = {
                                    onGoForward()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebToolActionTile(
                                icon = if (isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                label = if (isLoading) stringResource(R.string.web_stop_loading) else stringResource(R.string.reload_page),
                                tag = "web_tool_reload_stop",
                                onClick = {
                                    onReloadOrStop()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebToolActionTile(
                                icon = Icons.Outlined.Search,
                                label = "Find",
                                tag = "web_tool_find",
                                onClick = {
                                    onDismiss()
                                    onOpenFindInPage()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebToolActionTile(
                                icon = Icons.Outlined.Download,
                                label = if (activeDownloadsCount > 0) "Downloads ($activeDownloadsCount)" else stringResource(R.string.web_open_downloads),
                                tag = "web_tool_downloads",
                                onClick = {
                                    onDismiss()
                                    onOpenDownloadsHub()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Text Zoom Stepper Row
                    item {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Page Text Zoom",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = style.contentPrimary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(style.activeSegmentFill)
                                            .testTag("web_zoom_out_button")
                                            .bouncyClickable(
                                                pressedScale = 0.88f,
                                                contentDescriptionText = "Decrease text zoom",
                                                onClick = {
                                                    onChangeTextZoom((textZoomPercent - 15).coerceAtLeast(70))
                                                }
                                            )
                                    ) {
                                        Icon(Icons.Outlined.Remove, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$textZoomPercent%",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = style.contentPrimary,
                                        modifier = Modifier
                                            .bouncyClickable(
                                                pressedScale = 0.90f,
                                                contentDescriptionText = "Reset text zoom to 100 percent",
                                                onClick = { onChangeTextZoom(100) }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 10.dp)
                                    )
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(style.activeSegmentFill)
                                            .testTag("web_zoom_in_button")
                                            .bouncyClickable(
                                                pressedScale = 0.88f,
                                                contentDescriptionText = "Increase text zoom",
                                                onClick = {
                                                    onChangeTextZoom((textZoomPercent + 15).coerceAtMost(190))
                                                }
                                            )
                                    ) {
                                        Icon(Icons.Outlined.Add, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Desktop Site & Reader Mode Switches
                    item {
                        WebToggleRow(
                            icon = Icons.Outlined.DesktopWindows,
                            title = stringResource(R.string.web_desktop_site),
                            subtitle = "Request desktop User-Agent & wide viewport layout",
                            checked = isDesktopMode,
                            tag = "toggle_desktop_site",
                            onCheckedChange = onToggleDesktopMode
                        )
                    }
                    item {
                        WebToggleRow(
                            icon = Icons.Outlined.MenuBook,
                            title = stringResource(R.string.web_reader_mode),
                            subtitle = "Distraction-free editorial typography layout",
                            checked = isReaderMode,
                            tag = "toggle_reader_mode",
                            onCheckedChange = onToggleReaderMode
                        )
                    }

                    // Siri AI + Copy + Share Row
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            WebToolActionTile(
                                icon = Icons.Outlined.AutoAwesome,
                                label = "Ask Siri AI",
                                tag = "web_tool_siri_ai",
                                onClick = {
                                    onDismiss()
                                    onOpenSiriAi()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebToolActionTile(
                                icon = Icons.Outlined.ContentCopy,
                                label = stringResource(R.string.web_copy_link),
                                tag = "web_tool_copy_url",
                                onClick = {
                                    onCopyUrl()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            WebToolActionTile(
                                icon = Icons.Outlined.Share,
                                label = stringResource(R.string.web_share_page),
                                tag = "web_tool_share_page",
                                onClick = {
                                    onSharePage()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebToolActionTile(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(18.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = modifier
            .height(64.dp)
            .testTag(tag)
            .bouncyClickable(
                pressedScale = 0.92f,
                enabled = enabled,
                contentDescriptionText = label,
                onClick = onClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) style.contentPrimary else style.contentMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) style.contentPrimary else style.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WebToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(20.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Icon(icon, null, tint = style.contentPrimary, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = style.contentPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = style.contentSecondary)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = style.solidActionContent,
                    checkedTrackColor = style.solidActionFill,
                    checkedBorderColor = style.solidActionFill,
                    uncheckedThumbColor = style.contentSecondary,
                    uncheckedTrackColor = style.activeSegmentFill,
                    uncheckedBorderColor = style.rimTopLeft
                ),
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

/**
 * Long-Press Link / Image Context Sheet on WebView (`HitTestResult`).
 */
@Composable
fun WebContextMenuGlassSheet(
    target: WebHitContextTarget,
    onOpenInNewTab: (String) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onAskSiriAboutUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.30f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = if (target.isImage) "Image & Link Options" else "Link Options",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = style.contentPrimary
                )
                Text(
                    text = target.targetUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                ContextMenuRowButton(
                    icon = Icons.Outlined.OpenInNew,
                    label = "Open in New Tab",
                    onClick = {
                        onOpenInNewTab(target.targetUrl)
                        onDismiss()
                    }
                )
                ContextMenuRowButton(
                    icon = Icons.Outlined.Download,
                    label = if (target.isImage) "Download Image" else "Download Link File",
                    onClick = {
                        onDownloadUrl(target.imageUrl ?: target.targetUrl)
                        onDismiss()
                    }
                )
                ContextMenuRowButton(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Ask Siri AI About This",
                    onClick = {
                        onAskSiriAboutUrl(target.targetUrl)
                        onDismiss()
                    }
                )
                ContextMenuRowButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy Address",
                    onClick = {
                        onCopyUrl(target.targetUrl)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextMenuRowButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(16.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .bouncyClickable(
                pressedScale = 0.95f,
                contentDescriptionText = label,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(icon, null, tint = style.contentPrimary, modifier = Modifier.size(19.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = style.contentPrimary)
        }
    }
}

/**
 * Frosted-glass modal dialog for WebView `alert()`, `confirm()`, and `prompt()` calls.
 */
@Composable
fun WebJsGlassDialog(
    request: WebJsDialogRequest
) {
    val style = LocalGlassStyle.current
    var promptValue by remember(request) { mutableStateOf(request.defaultPromptValue) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.36f))
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(28.dp),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = style.contentPrimary
                )
                Text(
                    text = request.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.contentSecondary
                )
                if (request.isPrompt) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(14.dp),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                        ) {
                            BasicTextField(
                                value = promptValue,
                                onValueChange = { promptValue = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = style.contentPrimary),
                                cursorBrush = SolidColor(style.contentPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (request.isConfirm || request.isPrompt) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.activeSegmentFill)
                                .bouncyClickable(
                                    pressedScale = 0.92f,
                                    contentDescriptionText = "Cancel",
                                    onClick = request.onCancel
                                )
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = style.contentPrimary)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(style.solidActionFill)
                            .bouncyClickable(
                                pressedScale = 0.92f,
                                contentDescriptionText = "OK",
                                onClick = { request.onConfirm(if (request.isPrompt) promptValue else null) }
                            )
                    ) {
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = style.solidActionContent
                        )
                    }
                }
            }
        }
    }
}
