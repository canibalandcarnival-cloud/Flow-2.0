package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.DisplayRefreshRateManager
import com.example.core.OsShellManager
import com.example.core.WallpaperLuminanceAnalyzer
import com.example.data.CustomWallpaperEntity
import com.example.ui.BrowserUiState
import com.example.ui.components.EyeglassesIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.components.rememberPhysicalMomentumFlingBehavior
import com.example.ui.components.rubberBandOverscroll
import com.example.ui.theme.LocalGlassStyle
import java.io.File

/**
 * Bouncy Frosted Glass Omnibox Overlay for entering URLs, search queries, and quick page actions.
 */
@Composable
fun AddressSearchOverlay(
    currentUrl: String,
    isViewingWebPage: Boolean,
    onSubmitUrlOrSearch: (String) -> Unit,
    onAddToFavorites: () -> Unit,
    onAddToReadingList: () -> Unit,
    onAddBookmark: () -> Unit,
    onReloadPage: () -> Unit,
    onOpenSiriAi: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    var inputText by remember(currentUrl) { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            // Quick Page Actions Row when viewing a webpage
            if (isViewingWebPage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionGlassChip(
                        icon = { Icon(Icons.Outlined.StarOutline, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                        label = "Favorite",
                        onClick = {
                            onAddToFavorites()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionGlassChip(
                        icon = { EyeglassesIcon(tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                        label = "Read Later",
                        onClick = {
                            onAddToReadingList()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionGlassChip(
                        icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                        label = "Bookmark",
                        onClick = {
                            onAddBookmark()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionGlassChip(
                        icon = { Icon(Icons.Outlined.Refresh, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                        label = "Reload",
                        onClick = {
                            onReloadPage()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickActionGlassChip(
                    icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                    label = "Siri AI",
                    onClick = {
                        onDismiss()
                        onOpenSiriAi()
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickActionGlassChip(
                    icon = { Icon(Icons.Outlined.Download, null, tint = style.contentPrimary, modifier = Modifier.size(18.dp)) },
                    label = "Downloads",
                    onClick = {
                        onDismiss()
                        onOpenDownloads()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Frosted Omnibox Input Bar
            FrostedGlassBox(
                shape = RoundedCornerShape(28.dp),
                elevation = GlassElevation.MODAL_OVERLAY,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
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
                        tint = style.contentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_or_enter_website),
                                style = MaterialTheme.typography.bodyLarge,
                                color = style.contentMuted
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = style.contentPrimary
                            ),
                            cursorBrush = SolidColor(style.contentPrimary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (inputText.isNotBlank()) {
                                        onSubmitUrlOrSearch(inputText)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("omnibox_text_field")
                        )
                    }

                    if (inputText.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .bouncyClickable(
                                    pressedScale = 0.85f,
                                    contentDescriptionText = "Clear address",
                                    onClick = { inputText = "" }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear address",
                                tint = style.contentSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(style.solidActionFill)
                            .testTag("omnibox_go_button")
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = "Go",
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        onSubmitUrlOrSearch(inputText)
                                    }
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Go",
                            tint = style.solidActionContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .bouncyClickable(
                                pressedScale = 0.88f,
                                contentDescriptionText = stringResource(R.string.cd_more_options),
                                onClick = {
                                    onDismiss()
                                    onOpenSettings()
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = style.contentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionGlassChip(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(18.dp),
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        modifier = modifier
            .height(62.dp)
            .bouncyClickable(
                pressedScale = 0.92f,
                contentDescriptionText = label,
                onClick = onClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = style.contentPrimary,
                maxLines = 1
            )
        }
    }
}

/**
 * Monochrome Frosted Glass Customization & Settings Sheet:
 * - Light Mode (White UI) & Dark Mode (Black UI) Switcher (without tinting wallpaper!)
 * - Wallpaper Picker with '+' Import Button from Android Photo Picker
 * - Up to 360Hz High Refresh Rate Engine + Live FPS Monitor
 * - SFX & Haptic Feedback toggles + Close All Tabs
 */
@Composable
fun WallpaperAndSettingsSheet(
    uiState: BrowserUiState,
    displayCapability: DisplayRefreshRateManager.DisplayCapability,
    osRoleStatus: OsShellManager.OsRoleStatus,
    onSelectDarkMode: (Boolean) -> Unit,
    onSelectWallpaper: (String) -> Unit,
    onImportWallpaperClick: () -> Unit,
    onDeleteCustomWallpaper: (CustomWallpaperEntity) -> Unit,
    onRequestHomeOsRole: () -> Unit,
    onRequestBrowserRole: () -> Unit,
    onToggleImmersiveOsMode: (Boolean) -> Unit,
    onToggleHighRefresh: (Boolean) -> Unit,
    onToggleForceGpu: (Boolean) -> Unit,
    onToggleGpuRasterization: (Boolean) -> Unit,
    onToggleDisableHwOverlays: (Boolean) -> Unit,
    onToggleSfx: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    onEdgeBounce: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    val liveFps by DisplayRefreshRateManager.observeLiveFps().collectAsState(initial = displayCapability.activeHz)
    val momentumFling = rememberPhysicalMomentumFlingBehavior()

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.26f))
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
                    .testTag("settings_wallpaper_sheet")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
            LazyColumn(
                flingBehavior = momentumFling,
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 28.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .rubberBandOverscroll(onEdgeHit = onEdgeBounce)
            ) {
                // Sheet grab handle + Header
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.contentMuted)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.customize_start_page),
                                style = MaterialTheme.typography.titleLarge,
                                color = style.contentPrimary
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(style.activeSegmentFill)
                                    .testTag("close_settings_sheet_button")
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Close settings",
                                        onClick = onDismiss
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close settings",
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 1. Light Mode (White UI) / Dark Mode (Black UI) Segmented Toggle
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.appearance_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = style.contentPrimary
                        )

                        FrostedGlassBox(
                            shape = RoundedCornerShape(50),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            ) {
                                val halfW = maxWidth / 2
                                val slideX by animateDpAsState(
                                    targetValue = if (!uiState.settings.isDarkMode) 0.dp else halfW,
                                    animationSpec = spring(dampingRatio = 0.64f, stiffness = 380f),
                                    label = "themeSlide"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset(x = slideX)
                                        .width(halfW)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(style.solidActionFill)
                                )

                                Row(modifier = Modifier.fillMaxSize()) {
                                    val isLightSelected = !uiState.settings.isDarkMode
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("light_mode_button")
                                            .bouncyClickable(
                                                pressedScale = 0.94f,
                                                contentDescriptionText = stringResource(R.string.light_mode_label),
                                                onClick = { onSelectDarkMode(false) }
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.LightMode,
                                                contentDescription = null,
                                                tint = if (isLightSelected) style.solidActionContent else style.contentSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.light_mode_label),
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isLightSelected) style.solidActionContent else style.contentSecondary
                                            )
                                        }
                                    }

                                    val isDarkSelected = uiState.settings.isDarkMode
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("dark_mode_button")
                                            .bouncyClickable(
                                                pressedScale = 0.94f,
                                                contentDescriptionText = stringResource(R.string.dark_mode_label),
                                                onClick = { onSelectDarkMode(true) }
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DarkMode,
                                                contentDescription = null,
                                                tint = if (isDarkSelected) style.solidActionContent else style.contentSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.dark_mode_label),
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isDarkSelected) style.solidActionContent else style.contentSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Wallpaper Picker with '+' Import Button
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.wallpaper_picker_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = style.contentPrimary
                                )
                                Text(
                                    text = stringResource(R.string.wallpaper_untinted_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = style.contentSecondary
                                )
                            }

                            FrostedGlassBox(
                                shape = RoundedCornerShape(50),
                                elevation = GlassElevation.CARD,
                                useLiveSceneBackdrop = false,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = if (uiState.isWallpaperLight) "Dark Labels" else "Light Labels",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = style.contentPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // '+' Import Custom Wallpaper Tile
                            item(key = "import_plus_tile") {
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = GlassElevation.CARD,
                                    useLiveSceneBackdrop = false,
                                    modifier = Modifier
                                        .width(92.dp)
                                        .height(136.dp)
                                        .testTag("import_wallpaper_button")
                                        .bouncyClickable(
                                            pressedScale = 0.90f,
                                            contentDescriptionText = stringResource(R.string.cd_import_wallpaper),
                                            onClick = onImportWallpaperClick
                                        )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(style.solidActionFill)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = stringResource(R.string.cd_import_wallpaper),
                                                tint = style.solidActionContent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.import_wallpaper_label),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = style.contentPrimary
                                        )
                                    }
                                }
                            }

                            // User-imported custom wallpapers
                            items(
                                items = uiState.customWallpapers,
                                key = { "custom_wp_${it.id}" }
                            ) { customWp ->
                                val wpKey = "custom:${customWp.id}"
                                val isSelected = uiState.settings.selectedWallpaperId == wpKey
                                CustomWallpaperTile(
                                    wallpaper = customWp,
                                    isSelected = isSelected,
                                    onSelect = { onSelectWallpaper(wpKey) },
                                    onDelete = { onDeleteCustomWallpaper(customWp) }
                                )
                            }

                            // Built-in Dark & Light Wallpapers
                            items(
                                items = WallpaperLuminanceAnalyzer.presets,
                                key = { it.id }
                            ) { preset ->
                                val isSelected = uiState.settings.selectedWallpaperId == preset.id
                                val cardShape = RoundedCornerShape(18.dp)
                                Box(
                                    modifier = Modifier
                                        .width(92.dp)
                                        .height(136.dp)
                                        .clip(cardShape)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) style.contentPrimary else style.rimTopLeft,
                                            shape = cardShape
                                        )
                                        .testTag("wallpaper_${preset.id}")
                                        .bouncyClickable(
                                            pressedScale = 0.91f,
                                            contentDescriptionText = preset.title,
                                            onClick = { onSelectWallpaper(preset.id) }
                                        )
                                ) {
                                    Image(
                                        painter = painterResource(id = preset.drawableRes),
                                        contentDescription = preset.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(style.solidActionFill)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = style.solidActionContent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    FrostedGlassBox(
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = GlassElevation.CARD,
                                        useLiveSceneBackdrop = false,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(6.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = style.contentPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2.5 Operating System Shell (ROLE_HOME, ROLE_BROWSER & Immersive Fullscreen OS Mode)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.os_shell_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = style.contentPrimary
                        )

                        MonochromeOsRoleRow(
                            title = stringResource(R.string.default_home_os_label),
                            subtitle = stringResource(R.string.default_home_os_subtitle),
                            isHeld = osRoleStatus.isDefaultHomeShell,
                            onRequestRole = onRequestHomeOsRole,
                            tag = "button_set_default_home_os"
                        )
                        MonochromeOsRoleRow(
                            title = stringResource(R.string.default_browser_os_label),
                            subtitle = stringResource(R.string.default_browser_os_subtitle),
                            isHeld = osRoleStatus.isDefaultBrowser,
                            onRequestRole = onRequestBrowserRole,
                            tag = "button_set_default_browser"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.immersive_os_mode_label),
                            subtitle = stringResource(R.string.immersive_os_mode_subtitle),
                            checked = uiState.settings.immersiveOsMode,
                            onCheckedChange = onToggleImmersiveOsMode,
                            tag = "toggle_immersive_os_mode"
                        )
                    }
                }

                // 3. Physics, 360Hz Engine, GPU Rasterization & Compositor, SFX & Haptics
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.motion_and_feedback_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = style.contentPrimary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.device_optimization_badge_format,
                                        uiState.deviceProfile.tierLabel,
                                        uiState.deviceProfile.cpuCores
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = style.contentSecondary
                                )
                            }
                            Text(
                                text = "${liveFps} FPS • Max ${displayCapability.engineTargetHz}Hz",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentSecondary
                            )
                        }

                        MonochromeToggleRow(
                            title = stringResource(R.string.refresh_rate_360_label),
                            subtitle = "Active display mode: ${displayCapability.activeHz}Hz (supports up to 360Hz)",
                            checked = uiState.settings.highRefreshRateEnabled,
                            onCheckedChange = onToggleHighRefresh,
                            tag = "toggle_360hz"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.enable_gpu_rasterization_label),
                            subtitle = stringResource(R.string.enable_gpu_rasterization_subtitle),
                            checked = uiState.settings.enableGpuRasterization,
                            onCheckedChange = onToggleGpuRasterization,
                            tag = "toggle_gpu_rasterization"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.force_gpu_rendering_label),
                            subtitle = stringResource(R.string.force_gpu_rendering_subtitle),
                            checked = uiState.settings.forceGpuRendering,
                            onCheckedChange = onToggleForceGpu,
                            tag = "toggle_force_gpu_rendering"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.disable_hw_overlays_label),
                            subtitle = stringResource(R.string.disable_hw_overlays_subtitle),
                            checked = uiState.settings.disableHardwareOverlays,
                            onCheckedChange = onToggleDisableHwOverlays,
                            tag = "toggle_disable_hw_overlays"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.sfx_feedback_label),
                            subtitle = "Synthesized low-latency glass acoustic micro-ticks",
                            checked = uiState.settings.sfxEnabled,
                            onCheckedChange = onToggleSfx,
                            tag = "toggle_sfx"
                        )
                        MonochromeToggleRow(
                            title = stringResource(R.string.haptic_feedback_label),
                            subtitle = "Tactile ticks on buttons, tab switches & edge bounce",
                            checked = uiState.settings.hapticsEnabled,
                            onCheckedChange = onToggleHaptics,
                            tag = "toggle_haptics"
                        )
                    }
                }

                // 4. Close All Tabs monochrome action
                item {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(18.dp),
                        elevation = GlassElevation.CARD,
                        useLiveSceneBackdrop = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("close_all_tabs_button")
                            .bouncyClickable(
                                pressedScale = 0.95f,
                                contentDescriptionText = stringResource(R.string.close_all_tabs),
                                onClick = {
                                    onCloseAllTabs()
                                    onDismiss()
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = style.contentPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = stringResource(R.string.close_all_tabs),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = style.contentPrimary
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
private fun CustomWallpaperTile(
    wallpaper: CustomWallpaperEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val style = LocalGlassStyle.current
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .width(92.dp)
            .height(136.dp)
            .clip(shape)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) style.contentPrimary else style.rimTopLeft,
                shape = shape
            )
            .bouncyClickable(
                pressedScale = 0.91f,
                contentDescriptionText = wallpaper.title,
                onClick = onSelect
            )
    ) {
        AsyncImage(
            model = File(wallpaper.filePath),
            contentDescription = wallpaper.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(40.dp)
                .bouncyClickable(
                    pressedScale = 0.85f,
                    contentDescriptionText = "Delete custom wallpaper",
                    onClick = onDelete
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(style.glassBaseColor.copy(alpha = 0.72f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = style.contentPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(style.solidActionFill)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = style.solidActionContent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MonochromeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
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
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = style.contentPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentSecondary
                )
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

@Composable
private fun MonochromeOsRoleRow(
    title: String,
    subtitle: String,
    isHeld: Boolean,
    onRequestRole: () -> Unit,
    tag: String
) {
    val style = LocalGlassStyle.current
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = style.contentPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentSecondary
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(48.dp)
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isHeld) style.activeSegmentFill else style.solidActionFill)
                    .testTag(tag)
                    .bouncyClickable(
                        pressedScale = 0.91f,
                        contentDescriptionText = title,
                        onClick = onRequestRole
                    )
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isHeld) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = style.contentPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = if (isHeld) {
                            stringResource(R.string.os_role_active_badge)
                        } else {
                            stringResource(R.string.os_role_set_button)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isHeld) style.contentPrimary else style.solidActionContent
                    )
                }
            }
        }
    }
}

/**
 * Monochrome Frosted Glass Dialog for creating a new Tab Group or grouping the active tab.
 */
@Composable
fun NewTabGroupDialog(
    onConfirmGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    var groupName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        contentAlignment = Alignment.Center,
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
            shape = RoundedCornerShape(28.dp),
            elevation = GlassElevation.MODAL_OVERLAY,
            useLiveSceneBackdrop = true,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    text = stringResource(R.string.new_tab_group_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = style.contentPrimary
                )
                FrostedGlassBox(
                    shape = RoundedCornerShape(16.dp),
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
                        if (groupName.isEmpty()) {
                            Text(
                                text = "Group name (e.g. Workspace)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = style.contentMuted
                            )
                        }
                        BasicTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = style.contentPrimary
                            ),
                            cursorBrush = SolidColor(style.contentPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                onClick = onDismiss
                            )
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = style.contentPrimary
                        )
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
                                contentDescriptionText = "Create Group",
                                onClick = {
                                    if (groupName.isNotBlank()) {
                                        onConfirmGroup(groupName)
                                        onDismiss()
                                    }
                                }
                            )
                    ) {
                        Text(
                            text = "Save Group",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = style.solidActionContent
                        )
                    }
                }
            }
        }
    }
}
