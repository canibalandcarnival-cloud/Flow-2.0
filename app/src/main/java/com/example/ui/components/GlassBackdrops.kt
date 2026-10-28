package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GlassBackdropMode
import com.example.ui.theme.LocalGlassStyle
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zero-allocation cache for spatial glassmorphism backdrop light-fields anchored in root
 * viewport coordinates so scrolling cards, floating bars, and modal sheets refract stationary
 * luminous depth without per-frame Brush allocations.
 */
internal class SpatialLightFieldCache {
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedMode: GlassBackdropMode? = null
    private var cachedIsDark: Boolean? = null

    private var primaryOrbBrush: Brush? = null
    private var secondaryOrbBrush: Brush? = null
    private var tertiaryCausticBrush: Brush? = null
    private var ambientVeilBrush: Brush? = null

    fun drawInScreenSpace(
        scope: DrawScope,
        posInRoot: Offset,
        rootSize: IntSize,
        mode: GlassBackdropMode,
        isDark: Boolean,
        hasSampledBackdrop: Boolean
    ) {
        val viewportW = if (rootSize.width > 0) {
            rootSize.width.toFloat()
        } else {
            scope.size.width.coerceAtLeast(360f)
        }
        val viewportH = if (rootSize.height > 0) {
            rootSize.height.toFloat()
        } else {
            scope.size.height.coerceAtLeast(640f)
        }

        if (cachedWidth != viewportW ||
            cachedHeight != viewportH ||
            cachedMode != mode ||
            cachedIsDark != isDark
        ) {
            rebuildBrushes(viewportW, viewportH, mode, isDark)
            cachedWidth = viewportW
            cachedHeight = viewportH
            cachedMode = mode
            cachedIsDark = isDark
        }

        // Skip extra light-field passes when in LIVE_SCENE mode and a real blurred layer/bitmap was already drawn
        if (mode == GlassBackdropMode.LIVE_SCENE && hasSampledBackdrop) {
            return
        }

        val intensityScale = if (hasSampledBackdrop) 1.0f else 1.35f

        scope.translate(left = -posInRoot.x, top = -posInRoot.y) {
            val drawSize = Size(viewportW, viewportH)
            ambientVeilBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = drawSize,
                    alpha = (0.85f * intensityScale).coerceAtMost(1f)
                )
            }
            primaryOrbBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = drawSize,
                    alpha = (0.90f * intensityScale).coerceAtMost(1f)
                )
            }
            secondaryOrbBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = drawSize,
                    alpha = (0.88f * intensityScale).coerceAtMost(1f)
                )
            }
            tertiaryCausticBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = drawSize,
                    alpha = (0.85f * intensityScale).coerceAtMost(1f)
                )
            }
        }
    }

    private fun rebuildBrushes(
        w: Float,
        h: Float,
        mode: GlassBackdropMode,
        isDark: Boolean
    ) {
        val maxSide = max(w, h).coerceAtLeast(1f)
        when (mode) {
            GlassBackdropMode.PRISM_AURA -> {
                val a1 = if (isDark) 0.28f else 0.22f
                val a2 = if (isDark) 0.24f else 0.19f
                val a3 = if (isDark) 0.20f else 0.16f
                ambientVeilBrush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF0E1726).copy(alpha = 0.34f),
                            Color(0xFF090D16).copy(alpha = 0.18f),
                            Color(0xFF141022).copy(alpha = 0.30f)
                        )
                    } else {
                        listOf(
                            Color(0xFFEFF6FF).copy(alpha = 0.42f),
                            Color(0xFFF8FAFC).copy(alpha = 0.24f),
                            Color(0xFFF5F3FF).copy(alpha = 0.38f)
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                primaryOrbBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = a1),
                        Color(0xFF6366F1).copy(alpha = a1 * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.19f),
                    radius = maxSide * 0.58f
                )
                secondaryOrbBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA855F7).copy(alpha = a2),
                        Color(0xFF10B981).copy(alpha = a2 * 0.42f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.84f, h * 0.48f),
                    radius = maxSide * 0.54f
                )
                tertiaryCausticBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF59E0B).copy(alpha = a3),
                        Color(0xFFEC4899).copy(alpha = a3 * 0.40f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.26f, h * 0.84f),
                    radius = maxSide * 0.52f
                )
            }

            GlassBackdropMode.STUDIO_HALO -> {
                ambientVeilBrush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            PureWhite.copy(alpha = 0.16f),
                            Color.Transparent,
                            PureWhite.copy(alpha = 0.10f)
                        )
                    } else {
                        listOf(
                            PureWhite.copy(alpha = 0.52f),
                            PureBlack.copy(alpha = 0.08f),
                            PureWhite.copy(alpha = 0.44f)
                        )
                    },
                    start = Offset(0f, h * 0.08f),
                    end = Offset(w, h * 0.92f)
                )
                primaryOrbBrush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            PureWhite.copy(alpha = 0.24f),
                            PureWhite.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            PureBlack.copy(alpha = 0.13f),
                            PureBlack.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.22f, h * 0.20f),
                    radius = maxSide * 0.55f
                )
                secondaryOrbBrush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            PureWhite.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            PureWhite.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.80f, h * 0.76f),
                    radius = maxSide * 0.50f
                )
                tertiaryCausticBrush = null
            }

            GlassBackdropMode.DEEP_FROST -> {
                ambientVeilBrush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            PureWhite.copy(alpha = 0.15f),
                            PureBlack.copy(alpha = 0.22f),
                            PureWhite.copy(alpha = 0.11f)
                        )
                    } else {
                        listOf(
                            PureWhite.copy(alpha = 0.58f),
                            Color(0xFFE2E8F0).copy(alpha = 0.34f),
                            PureWhite.copy(alpha = 0.50f)
                        )
                    },
                    startY = 0f,
                    endY = h
                )
                primaryOrbBrush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF94A3B8).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            PureWhite.copy(alpha = 0.64f),
                            Color.Transparent
                        )
                    },
                    center = Offset(w * 0.50f, h * 0.32f),
                    radius = maxSide * 0.62f
                )
                secondaryOrbBrush = null
                tertiaryCausticBrush = null
            }

            GlassBackdropMode.LIVE_SCENE -> {
                // Used only as graceful optical fallback before first snapshot arrives
                ambientVeilBrush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            PureWhite.copy(alpha = 0.12f),
                            PureBlack.copy(alpha = 0.18f),
                            PureWhite.copy(alpha = 0.08f)
                        )
                    } else {
                        listOf(
                            PureWhite.copy(alpha = 0.45f),
                            PureBlack.copy(alpha = 0.06f),
                            PureWhite.copy(alpha = 0.38f)
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                primaryOrbBrush = null
                secondaryOrbBrush = null
                tertiaryCausticBrush = null
            }
        }
    }
}

/**
 * Full-bleed frosted glassmorphic backdrop scrim placed behind modal sheets, omnibox search,
 * and dialogs so underlying scene/webpage content blurs & refracts across the entire viewport
 * when Frosted Glass is enabled, or renders a clean monochrome scrim when disabled.
 */
@Composable
fun GlassModalBackdrop(
    scrimAlpha: Float,
    onDismiss: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.BottomCenter,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val backdrop = LocalSpatialBackdrop.current
    val lightFieldCache = remember { SpatialLightFieldCache() }

    val dismissModifier = if (onDismiss != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss
        )
    } else {
        Modifier
    }

    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (size.width <= 0f || size.height <= 0f) return@drawBehind
                if (!style.isFrostedGlassEnabled) {
                    val solidScrim = (scrimAlpha + 0.32f).coerceIn(0.48f, 0.88f)
                    drawRect(color = style.glassBaseColor.copy(alpha = solidScrim))
                    return@drawBehind
                }

                val activeLayer = backdrop.sceneBlurredLayer ?: backdrop.wallpaperBlurredLayer
                val fallbackBmp = backdrop.fallbackBlurredBitmap ?: backdrop.fallbackCardBlurredBitmap
                var hasSampledBackdrop = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    activeLayer != null &&
                    activeLayer.size.width > 0 &&
                    activeLayer.size.height > 0
                ) {
                    hasSampledBackdrop = true
                    drawLayer(activeLayer)
                } else if (fallbackBmp != null && size.width > 0f && size.height > 0f) {
                    hasSampledBackdrop = true
                    val scale = maxOf(
                        size.width / fallbackBmp.width.coerceAtLeast(1),
                        size.height / fallbackBmp.height.coerceAtLeast(1)
                    )
                    val dstW = (fallbackBmp.width * scale).roundToInt()
                    val dstH = (fallbackBmp.height * scale).roundToInt()
                    val offsetX = ((size.width - dstW) / 2f).roundToInt()
                    val offsetY = ((size.height - dstH) / 2f).roundToInt()
                    drawImage(
                        image = fallbackBmp,
                        dstOffset = IntOffset(offsetX, offsetY),
                        dstSize = IntSize(dstW, dstH)
                    )
                }

                lightFieldCache.drawInScreenSpace(
                    scope = this,
                    posInRoot = Offset.Zero,
                    rootSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    mode = backdrop.backdropMode,
                    isDark = style.isDark,
                    hasSampledBackdrop = hasSampledBackdrop
                )

                val effectiveScrim = if (hasSampledBackdrop) {
                    scrimAlpha
                } else {
                    (scrimAlpha + 0.18f).coerceAtMost(0.82f)
                }
                drawRect(color = style.glassBaseColor.copy(alpha = effectiveScrim))
            }
            .then(dismissModifier),
        content = content
    )
}

/**
 * Interactive Glassmorphism Backdrop selector displayed in Customize & Wallpaper
 * when Frosted Glass is enabled.
 */
@Composable
fun GlassBackdropModeSelector(
    selectedMode: GlassBackdropMode,
    onSelectMode: (GlassBackdropMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val modes = GlassBackdropMode.entries

    FrostedGlassBox(
        shape = RoundedCornerShape(22.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = modifier
            .fillMaxWidth()
            .testTag("glass_backdrop_selector")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.glass_backdrop_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = style.contentPrimary
                )
                Text(
                    text = stringResource(R.string.glass_backdrop_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentSecondary
                )
            }

            val rows = modes.chunked(2)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rows.forEach { rowModes ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowModes.forEach { mode ->
                            val isSelected = mode == selectedMode
                            val label = when (mode) {
                                GlassBackdropMode.PRISM_AURA -> stringResource(R.string.backdrop_mode_prism)
                                GlassBackdropMode.LIVE_SCENE -> stringResource(R.string.backdrop_mode_live)
                                GlassBackdropMode.STUDIO_HALO -> stringResource(R.string.backdrop_mode_halo)
                                GlassBackdropMode.DEEP_FROST -> stringResource(R.string.backdrop_mode_deep)
                            }
                            BackdropModeChip(
                                mode = mode,
                                label = label,
                                isSelected = isSelected,
                                onClick = { onSelectMode(mode) },
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
private fun BackdropModeChip(
    mode: GlassBackdropMode,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val chipShape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(48.dp)
            .clip(chipShape)
            .background(if (isSelected) style.solidActionFill else style.activeSegmentFill)
            .border(
                width = 1.dp,
                color = if (isSelected) style.solidActionFill else style.rimTopLeft,
                shape = chipShape
            )
            .testTag("backdrop_mode_${mode.storageKey}")
            .bouncyClickable(
                pressedScale = 0.93f,
                contentDescriptionText = label,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Miniature backdrop preview swatch orb
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@drawBehind
                        when (mode) {
                            GlassBackdropMode.PRISM_AURA -> {
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF38BDF8),
                                            Color(0xFFA855F7),
                                            Color(0xFFF59E0B)
                                        )
                                    )
                                )
                            }
                            GlassBackdropMode.LIVE_SCENE -> {
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            PureWhite,
                                            Color(0xFF64748B),
                                            PureBlack
                                        )
                                    )
                                )
                            }
                            GlassBackdropMode.STUDIO_HALO -> {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            PureWhite,
                                            Color(0xFF94A3B8),
                                            Color(0xFF1E293B)
                                        )
                                    )
                                )
                            }
                            GlassBackdropMode.DEEP_FROST -> {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            PureWhite,
                                            Color(0xFFCBD5E1)
                                        )
                                    )
                                )
                            }
                        }
                    }
                    .border(
                        width = 0.8.dp,
                        color = if (isSelected) {
                            style.solidActionContent.copy(alpha = 0.55f)
                        } else {
                            style.contentPrimary.copy(alpha = 0.45f)
                        },
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.size(0.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isSelected) style.solidActionContent else style.contentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
