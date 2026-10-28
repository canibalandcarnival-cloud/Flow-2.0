package com.example.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.data.GlassBackdropMode
import com.example.ui.theme.LocalGlassStyle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Shared live backdrop state enabling zero-recursion spatial frosted glass across Start Page,
 * live WebView browsing, floating chrome bars, and modal sheets:
 * - [wallpaperBlurredLayer]: Hardware-blurred GraphicsLayer for inner cards (untinted wallpaper on Start Page; settled webpage backdrop on sites)
 * - [sceneBlurredLayer]: Hardware-blurred GraphicsLayer of the full scene (wallpaper + Hub content on Start Page; settled webpage backdrop on sites)
 * - [fallbackBlurredBitmap]: StackBlurred ImageBitmap for scene backdrop on API < 31 / JVM tests
 * - [fallbackCardBlurredBitmap]: StackBlurred ImageBitmap for card backdrop on API < 31 / JVM tests
 * - [backdropMode]: Active glassmorphism optical backdrop mode
 */
@Immutable
data class SpatialBackdropState(
    val wallpaperBlurredLayer: GraphicsLayer? = null,
    val sceneBlurredLayer: GraphicsLayer? = null,
    val fallbackBlurredBitmap: ImageBitmap? = null,
    val fallbackCardBlurredBitmap: ImageBitmap? = null,
    val backdropMode: GlassBackdropMode = GlassBackdropMode.PRISM_AURA,
    val rootSize: IntSize = IntSize.Zero
) {
    val hasActiveBackdrop: Boolean
        get() = wallpaperBlurredLayer != null ||
            sceneBlurredLayer != null ||
            fallbackBlurredBitmap != null ||
            fallbackCardBlurredBitmap != null
}

val LocalSpatialBackdrop = staticCompositionLocalOf { SpatialBackdropState() }

enum class GlassElevation {
    CARD,
    FLOATING_BAR,
    MODAL_OVERLAY
}

private class LayoutCoordinatesRef(
    var value: LayoutCoordinates? = null
)

private class GlassGeometryCache {
    var cachedSize: androidx.compose.ui.geometry.Size = androidx.compose.ui.geometry.Size.Unspecified
    var cachedLayoutDirection: androidx.compose.ui.unit.LayoutDirection? = null
    var cachedOutline: Outline? = null
    val clipPath: Path = Path()
    var sheenBrush: Brush? = null
    var counterSheenBrush: Brush? = null
    var rimBrush: Brush? = null
    val lightFieldCache: SpatialLightFieldCache = SpatialLightFieldCache()
}

/**
 * Renders an authentic frosted-glass container that dynamically samples & blurs the content
 * directly behind its screen coordinates, layered with strict monochrome White (Light Mode) or
 * Black (Dark Mode) translucency and a dual specular rim highlight.
 *
 * Uses zero-allocation cached geometry & non-state LayoutCoordinates references so scrolling never
 * triggers Compose recompositions and stays locked at native display refresh up to 360Hz.
 */
@Composable
fun FrostedGlassBox(
    shape: Shape,
    modifier: Modifier = Modifier,
    elevation: GlassElevation = GlassElevation.FLOATING_BAR,
    useLiveSceneBackdrop: Boolean = true,
    customTintAlpha: Float? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val style = LocalGlassStyle.current
    val backdrop = LocalSpatialBackdrop.current
    val coordsRef = remember { LayoutCoordinatesRef() }
    val geometryCache = remember(shape, style) { GlassGeometryCache() }

    val tintAlpha = customTintAlpha ?: when (elevation) {
        GlassElevation.CARD -> style.cardTintAlpha
        GlassElevation.FLOATING_BAR -> style.barTintAlpha
        GlassElevation.MODAL_OVERLAY -> style.overlayTintAlpha
    }

    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .then(
                if (style.isFrostedGlassEnabled) {
                    Modifier.onGloballyPositioned { coordsRef.value = it }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .drawBehind {
                if (size.width <= 0f || size.height <= 0f) return@drawBehind
                if (geometryCache.cachedSize != size ||
                    geometryCache.cachedLayoutDirection != layoutDirection ||
                    geometryCache.cachedOutline == null
                ) {
                    val created = shape.createOutline(size, layoutDirection, this)
                    geometryCache.cachedSize = size
                    geometryCache.cachedLayoutDirection = layoutDirection
                    geometryCache.cachedOutline = created
                    geometryCache.clipPath.rewind()
                    when (created) {
                        is Outline.Rectangle -> geometryCache.clipPath.addRect(created.rect)
                        is Outline.Rounded -> geometryCache.clipPath.addRoundRect(created.roundRect)
                        is Outline.Generic -> geometryCache.clipPath.addPath(created.path)
                    }
                    geometryCache.sheenBrush = Brush.verticalGradient(
                        colors = listOf(style.topSheen, Color.Transparent),
                        startY = 0f,
                        endY = (size.height * 0.58f).coerceAtLeast(1f)
                    )
                    geometryCache.counterSheenBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            style.topSheen.copy(alpha = style.topSheen.alpha * 0.42f)
                        ),
                        start = Offset(size.width * 0.35f, size.height * 0.55f),
                        end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                    )
                    geometryCache.rimBrush = Brush.linearGradient(
                        colors = listOf(style.rimTopLeft, style.rimBottomRight),
                        start = Offset.Zero,
                        end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                    )
                }

                val outline = geometryCache.cachedOutline ?: return@drawBehind

                if (!style.isFrostedGlassEnabled) {
                    // Frosted Glass OFF: Crisp 100% opaque monochrome surface + hairline border
                    val solidFill = when (elevation) {
                        GlassElevation.CARD -> style.solidCardSurface
                        GlassElevation.FLOATING_BAR -> style.solidBarSurface
                        GlassElevation.MODAL_OVERLAY -> style.solidOverlaySurface
                    }
                    clipPath(geometryCache.clipPath) {
                        drawRect(color = solidFill)
                    }
                    if (shape != RectangleShape) {
                        val strokeWidthPx = 1.dp.toPx()
                        drawOutline(
                            outline = outline,
                            color = style.solidBorderColor,
                            style = Stroke(width = strokeWidthPx)
                        )
                    }
                    return@drawBehind
                }

                val coords = coordsRef.value
                val posInRoot = if (coords != null && coords.isAttached) {
                    coords.positionInRoot()
                } else {
                    Offset.Zero
                }

                val sheen = geometryCache.sheenBrush
                val counterSheen = geometryCache.counterSheenBrush
                val rim = geometryCache.rimBrush

                clipPath(geometryCache.clipPath) {
                    val activeLayer = if (useLiveSceneBackdrop) {
                        backdrop.sceneBlurredLayer ?: backdrop.wallpaperBlurredLayer
                    } else {
                        backdrop.wallpaperBlurredLayer ?: backdrop.sceneBlurredLayer
                    }
                    val activeFallbackBmp = if (useLiveSceneBackdrop) {
                        backdrop.fallbackBlurredBitmap ?: backdrop.fallbackCardBlurredBitmap
                    } else {
                        backdrop.fallbackCardBlurredBitmap ?: backdrop.fallbackBlurredBitmap
                    }

                    var hasSampledBackdrop = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        activeLayer != null &&
                        activeLayer.size.width > 0 &&
                        activeLayer.size.height > 0
                    ) {
                        hasSampledBackdrop = true
                        translate(left = -posInRoot.x, top = -posInRoot.y) {
                            drawLayer(activeLayer)
                        }
                    } else if (activeFallbackBmp != null &&
                        backdrop.rootSize.width > 0 &&
                        backdrop.rootSize.height > 0
                    ) {
                        hasSampledBackdrop = true
                        val bmp = activeFallbackBmp
                        val rootW = backdrop.rootSize.width
                        val rootH = backdrop.rootSize.height
                        // Center-crop scale calculation matching viewport display
                        val scale = maxOf(
                            rootW.toFloat() / bmp.width.coerceAtLeast(1),
                            rootH.toFloat() / bmp.height.coerceAtLeast(1)
                        )
                        val dstW = (bmp.width * scale).roundToInt()
                        val dstH = (bmp.height * scale).roundToInt()
                        val offsetX = ((rootW - dstW) / 2f) - posInRoot.x
                        val offsetY = ((rootH - dstH) / 2f) - posInRoot.y
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                            dstSize = IntSize(dstW, dstH)
                        )
                    }

                    // Spatial glassmorphism light-field backdrop anchored in viewport coordinates
                    geometryCache.lightFieldCache.drawInScreenSpace(
                        scope = this,
                        posInRoot = posInRoot,
                        rootSize = backdrop.rootSize,
                        mode = backdrop.backdropMode,
                        isDark = style.isDark,
                        hasSampledBackdrop = hasSampledBackdrop
                    )

                    // Monochrome Frosted Glass Tint (Black in Dark Mode, White in Light Mode)
                    val effectiveTintAlpha = if (hasSampledBackdrop) {
                        tintAlpha
                    } else {
                        (tintAlpha + 0.08f).coerceAtMost(0.88f)
                    }
                    drawRect(color = style.glassBaseColor.copy(alpha = effectiveTintAlpha))

                    // Dual optical refraction sheen (top specular + subtle bottom-right internal reflection)
                    if (sheen != null) {
                        drawRect(brush = sheen)
                    }
                    if (counterSheen != null) {
                        drawRect(brush = counterSheen)
                    }
                }

                // Specular 1dp glass edge rim highlight
                if (rim != null) {
                    val strokeWidthPx = 1.dp.toPx()
                    drawOutline(
                        outline = outline,
                        brush = rim,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
            },
        content = content
    )
}

/**
 * Helper to create a hardware-blurred GraphicsLayer on Android 12+ (API 31+).
 */
fun configureBlurLayer(layer: GraphicsLayer, blurRadiusPx: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        layer.renderEffect = BlurEffect(
            radiusX = blurRadiusPx,
            radiusY = blurRadiusPx,
            edgeTreatment = TileMode.Clamp
        )
    }
}

/**
 * Clears hardware RenderEffect blur from a GraphicsLayer when Frosted Glass is disabled.
 */
fun clearBlurLayer(layer: GraphicsLayer) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        layer.renderEffect = null
    }
}

/**
 * Momentum & Inertia FlingBehavior:
 * Preserves swipe velocity with weighted exponential inertia so content glides smoothly
 * before coming to a gentle physical stop, returning leftover boundary velocity to trigger
 * Rubber-Banding overshoot at scroll limits.
 */
@Composable
fun rememberPhysicalMomentumFlingBehavior(): FlingBehavior {
    val decaySpec = remember {
        exponentialDecay<Float>(
            frictionMultiplier = 0.74f,
            absVelocityThreshold = 0.15f
        )
    }
    return remember(decaySpec) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) <= 1f) return initialVelocity
                var velocityLeft = initialVelocity
                var lastValue = 0f
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity
                ).animateDecay(decaySpec) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    velocityLeft = this.velocity
                    // Hit scroll edge: hand remaining momentum directly to Rubber-Band spring!
                    if (abs(delta - consumed) > 0.5f) {
                        this.cancelAnimation()
                    }
                }
                return velocityLeft
            }
        }
    }
}

/**
 * Rubber-Banding (Bouncing) Overscroll Modifier:
 * - Finger drag past top or bottom edge applies progressive elastic resistance.
 * - Momentum fling hitting top or bottom edge overshoots proportionally and springs back smoothly.
 */
fun Modifier.rubberBandOverscroll(
    maxOverscrollPx: Float = 380f,
    onEdgeHit: ((velocityPxPerSec: Float) -> Unit)? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val overscrollY = remember { Animatable(0f) }

    val connection = remember(maxOverscrollPx, onEdgeHit) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollY.value
                if (source == NestedScrollSource.UserInput && abs(current) > 0.5f) {
                    // Unwind active rubber-band stretch first when reversing finger direction
                    val sameSign = (current > 0f && available.y > 0f) || (current < 0f && available.y < 0f)
                    if (!sameSign) {
                        val next = if (current > 0f) {
                            (current + available.y).coerceAtLeast(0f)
                        } else {
                            (current + available.y).coerceAtMost(0f)
                        }
                        val consumedY = next - current
                        scope.launch { overscrollY.snapTo(next) }
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.05f) {
                    val current = overscrollY.value
                    val normalized = (abs(current) / maxOverscrollPx).coerceIn(0f, 2f)
                    val resistance = 0.55f / (1f + normalized).pow(1.35f)
                    val dampedDelta = available.y * resistance
                    val target = (current + dampedDelta).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                    scope.launch { overscrollY.snapTo(target) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(overscrollY.value) > 0.5f) {
                    scope.launch {
                        overscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.62f,
                                stiffness = 310f
                            ),
                            initialVelocity = available.y * 0.25f
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(available.y) > 90f || abs(overscrollY.value) > 0.5f) {
                    if (abs(available.y) > 260f) {
                        onEdgeHit?.invoke(available.y)
                    }
                    val clampedVelocity = available.y.coerceIn(-4800f, 4800f) * 0.52f
                    scope.launch {
                        overscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.58f,
                                stiffness = 275f
                            ),
                            initialVelocity = clampedVelocity
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = overscrollY.value
        }
}

/**
 * Bouncy spring press-and-release interaction modifier for tactile buttons, cards, and squircles.
 */
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.91f,
    enabled: Boolean = true,
    contentDescriptionText: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) 0.72f else Spring.DampingRatioMediumBouncy,
            stiffness = if (isPressed) 620f else 420f
        ),
        label = "bouncyPressScale"
    )

    this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (contentDescriptionText != null) {
                contentDescription = contentDescriptionText
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled, onClick) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                val up = waitForUpOrCancellation()
                isPressed = false
                if (up != null) {
                    onClick()
                }
            }
        }
}
