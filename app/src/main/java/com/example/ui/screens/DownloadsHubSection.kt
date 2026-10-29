package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.DownloadEngine
import com.example.data.DownloadItemEntity
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.components.rubberBandOverscroll
import com.example.ui.theme.LocalGlassStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DownloadsFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

/**
 * Dedicated spatial frosted-glass Downloads Hub tab where users inspect active & completed
 * web downloads, start direct URL downloads, open or share files, retry failures, and clean up.
 */
@Composable
fun DownloadsSectionList(
    downloads: List<DownloadItemEntity>,
    headerColor: Color,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onStartDirectDownload: (String) -> Unit,
    onOpenDownload: (DownloadItemEntity) -> Unit,
    onShareDownload: (DownloadItemEntity) -> Unit,
    onRetryDownload: (DownloadItemEntity) -> Unit,
    onDeleteDownload: (DownloadItemEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onEdgeBounce: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val momentumFling = rememberPhysicalMomentumFlingBehavior()
    var activeFilter by remember { mutableStateOf(DownloadsFilter.ALL) }
    var directUrlInput by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }

    val filteredDownloads = remember(downloads, activeFilter) {
        when (activeFilter) {
            DownloadsFilter.ALL -> downloads
            DownloadsFilter.ACTIVE -> downloads.filter { it.status == "RUNNING" || it.status == "PAUSED" }
            DownloadsFilter.COMPLETED -> downloads.filter { it.status == "SUCCESSFUL" || it.status == "FAILED" }
        }
    }

    val hasCompletedOrFailed = remember(downloads) {
        downloads.any { it.status == "SUCCESSFUL" || it.status == "FAILED" }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_hub_section")
    ) {
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
                .testTag("downloads_scroll_list")
                .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
        ) {
            // Header + Clear Completed button
            item(key = "downloads_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = headerColor
                    )
                    if (hasCompletedOrFailed) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(50),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_completed_downloads_button")
                                .bouncyClickable(
                                    pressedScale = 0.90f,
                                    contentDescriptionText = stringResource(R.string.downloads_clear_completed),
                                    onClick = onClearCompleted
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
                                    text = stringResource(R.string.downloads_clear_completed),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = style.contentPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Direct File URL Quick-Download Bar
            item(key = "downloads_direct_url_bar") {
                FrostedGlassBox(
                    shape = RoundedCornerShape(22.dp),
                    elevation = GlassElevation.CARD,
                    useLiveSceneBackdrop = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = style.contentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (directUrlInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.downloads_url_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = style.contentMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = directUrlInput,
                                onValueChange = { directUrlInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = style.contentPrimary
                                ),
                                cursorBrush = SolidColor(style.contentPrimary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val clean = directUrlInput.trim()
                                        if (clean.isNotEmpty()) {
                                            onStartDirectDownload(clean)
                                            directUrlInput = ""
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("downloads_direct_url_input")
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(48.dp)
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.solidActionFill)
                                .testTag("downloads_direct_start_button")
                                .bouncyClickable(
                                    pressedScale = 0.90f,
                                    contentDescriptionText = stringResource(R.string.downloads_start_button),
                                    onClick = {
                                        val clean = directUrlInput.trim()
                                        if (clean.isNotEmpty()) {
                                            onStartDirectDownload(clean)
                                            directUrlInput = ""
                                        }
                                    }
                                )
                                .padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.downloads_start_button),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = style.solidActionContent
                            )
                        }
                    }
                }
            }

            // Filter Pills Row (All / Active / Completed)
            item(key = "downloads_filter_row") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DownloadsFilterChip(
                        label = stringResource(R.string.downloads_filter_all),
                        count = downloads.size,
                        selected = activeFilter == DownloadsFilter.ALL,
                        tag = "downloads_filter_all",
                        onClick = { activeFilter = DownloadsFilter.ALL },
                        modifier = Modifier.weight(1f)
                    )
                    DownloadsFilterChip(
                        label = stringResource(R.string.downloads_filter_active),
                        count = downloads.count { it.status == "RUNNING" || it.status == "PAUSED" },
                        selected = activeFilter == DownloadsFilter.ACTIVE,
                        tag = "downloads_filter_active",
                        onClick = { activeFilter = DownloadsFilter.ACTIVE },
                        modifier = Modifier.weight(1f)
                    )
                    DownloadsFilterChip(
                        label = stringResource(R.string.downloads_filter_completed),
                        count = downloads.count { it.status == "SUCCESSFUL" || it.status == "FAILED" },
                        selected = activeFilter == DownloadsFilter.COMPLETED,
                        tag = "downloads_filter_completed",
                        onClick = { activeFilter = DownloadsFilter.COMPLETED },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (filteredDownloads.isEmpty()) {
                item(key = "downloads_empty_card") {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(24.dp),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("downloads_empty_state")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = style.contentPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_downloads_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = style.contentSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredDownloads,
                    key = { "dl_${it.id}" },
                    contentType = { "download_card" }
                ) { item ->
                    DownloadItemGlassCard(
                        item = item,
                        formattedDate = dateFormatter.format(Date(item.createdAt)),
                        onOpen = { onOpenDownload(item) },
                        onShare = { onShareDownload(item) },
                        onRetry = { onRetryDownload(item) },
                        onDelete = { onDeleteDownload(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(50),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        customTintAlpha = if (selected) 0.72f else null,
        modifier = modifier
            .height(48.dp)
            .testTag(tag)
            .bouncyClickable(
                pressedScale = 0.92f,
                contentDescriptionText = "$label ($count)",
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) style.contentPrimary else style.contentSecondary,
                maxLines = 1
            )
            if (count > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) style.solidActionFill else style.activeSegmentFill)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) style.solidActionContent else style.contentPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemGlassCard(
    item: DownloadItemEntity,
    formattedDate: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val style = LocalGlassStyle.current
    val isRunning = item.status == "RUNNING" || item.status == "PAUSED"
    val isCompleted = item.status == "SUCCESSFUL"
    val isFailed = item.status == "FAILED"
    val progressFraction = if (item.totalBytes > 0L) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    FrostedGlassBox(
        shape = RoundedCornerShape(24.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_item_${item.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FrostedGlassBox(
                    shape = CircleShape,
                    elevation = GlassElevation.FLOATING_BAR,
                    useLiveSceneBackdrop = false,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = fileTypeIcon(item.mimeType, item.fileName),
                        contentDescription = null,
                        tint = style.contentPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = style.contentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        isRunning && item.totalBytes > 0L -> {
                            val pct = (progressFraction * 100).toInt()
                            "${DownloadEngine.formatBytes(item.downloadedBytes)} / ${DownloadEngine.formatBytes(item.totalBytes)} • $pct%"
                        }
                        isRunning -> "${DownloadEngine.formatBytes(item.downloadedBytes)} • Downloading…"
                        isCompleted -> "${DownloadEngine.formatBytes(item.totalBytes.coerceAtLeast(item.downloadedBytes))} • $formattedDate"
                        else -> "Download failed • Tap Retry"
                    }
                    Text(
                        text = subtitle,
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
                        .testTag("delete_download_${item.id}")
                        .bouncyClickable(
                            pressedScale = 0.86f,
                            contentDescriptionText = stringResource(R.string.download_delete_action),
                            onClick = onDelete
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.download_delete_action),
                        tint = style.contentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(style.activeSegmentFill)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (item.totalBytes > 0L) progressFraction.coerceAtLeast(0.06f) else 0.38f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(style.contentPrimary)
                    )
                }
            }

            if (isCompleted || isFailed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCompleted) {
                        DownloadActionPill(
                            icon = Icons.Outlined.OpenInNew,
                            label = stringResource(R.string.download_open_action),
                            isPrimary = true,
                            tag = "open_download_${item.id}",
                            onClick = onOpen,
                            modifier = Modifier.weight(1f)
                        )
                        DownloadActionPill(
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.download_share_action),
                            isPrimary = false,
                            tag = "share_download_${item.id}",
                            onClick = onShare,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        DownloadActionPill(
                            icon = Icons.Outlined.Refresh,
                            label = stringResource(R.string.download_retry_action),
                            isPrimary = true,
                            tag = "retry_download_${item.id}",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadActionPill(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isPrimary) style.solidActionFill else style.activeSegmentFill)
            .testTag(tag)
            .bouncyClickable(
                pressedScale = 0.92f,
                contentDescriptionText = label,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary) style.solidActionContent else style.contentPrimary,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isPrimary) style.solidActionContent else style.contentPrimary
            )
        }
    }
}

private fun fileTypeIcon(mimeType: String, fileName: String): ImageVector {
    val lowerMime = mimeType.lowercase(Locale.US)
    val lowerName = fileName.lowercase(Locale.US)
    return when {
        lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".webp") ->
            Icons.Outlined.Image
        lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".webm") ->
            Icons.Outlined.VideoFile
        lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ->
            Icons.Outlined.AudioFile
        lowerMime.contains("zip") || lowerMime.contains("compressed") || lowerName.endsWith(".zip") || lowerName.endsWith(".tar.gz") ->
            Icons.Outlined.FolderZip
        lowerMime.contains("pdf") || lowerMime.startsWith("text/") || lowerName.endsWith(".pdf") || lowerName.endsWith(".md") ->
            Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}
