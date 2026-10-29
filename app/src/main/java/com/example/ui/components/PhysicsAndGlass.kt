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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.LocalGlassStyle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Draw-phase-only motion tracker that invalidates `FrostedGlassBox` `drawBehind` blocks
 * on every scroll pixel, rubber-band bounce frame, WebView scroll delta, or floating-glass drag
 * with ZERO Compose recomposition overhead.
 */
@Stable
class GlassMotionTracker {
    var tick by mutableIntStateOf(0)
        private set

    var webScrollDeltaY by mutableFloatStateOf(0f)
        private set

    fun notifyMoved() {
        tick = (tick + 1) and 0x3FFFFFFF
    }

    fun updateWebScrollDelta(deltaY: Float) {
        webScrollDeltaY = deltaY
        notifyMoved()
    }
}

/**
 * Shared live backdrop state enabling zero-recursion spatial frosted glass across Start Page,
 * scrolling Hub lists, live WebView pages, floating chrome bars, and movable Siri / tool pills:
 * - [wallpaperBlurredLayer]: Hardware-blurred GraphicsLayer of the untinted Start Page wallpaper
 * - [sceneBlurredLayer]: Hardware-blurred GraphicsLayer of the live scene (wallpaper + Hub content)
 * - [fallbackBlurredBitmap]: StackBlurred ImageBitmap for Start Page or live WebView backdrop
 * - [motionTracker]: Draw-phase invalidator so frosted glass follows content underneath when scrolling or moving
 */
@Immutable
data class SpatialBackdropState(
    val wallpaperBlurredLayer: GraphicsLayer? = null,
    val sceneBlurredLayer: GraphicsLayer? = null,
    val fallbackBlurredBitmap: ImageBitmap? = null,
    val isWebBackdrop: Boolean = false,
    val rootSize: IntSize = IntSize.Zero,
    val motionTracker: GlassMotionTracker = GlassMotionTracker()
) {
    val hasActiveBackdrop: Boolean
        get() = wallpaperBlurredLayer != null ||
            sceneBlurredLayer != null ||
            fallbackBlurredBitmap != null
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
    var rimBrush: Brush? = null
}

/**
 * Renders an authentic frosted-glass container that dynamically tracks, samples, and refracts
 * the live content directly beneath its current screen coordinates when scrolling or moving,
 * layered with crisp translucency and a specular rim highlight.
 *
 * Reads [GlassMotionTracker.tick] strictly inside `drawBehind` so scrolling and dragging never
 * trigger Compose recompositions while maintaining 60–360Hz spatial tracking.
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
            .onGloballyPositioned {
                coordsRef.value = it
                backdrop.motionTracker.notifyMoved()
            }
            .clip(shape)
            .drawBehind {
                // Observe draw-phase motion state so scrolling lists, WebView scrolling, and
                // dragging glass elements dynamically re-sample their exact root coordinates!
                val motionTick = backdrop.motionTracker.tick
                val webScrollShiftY = if (backdrop.isWebBackdrop) {
                    backdrop.motionTracker.webScrollDeltaY
                } else {
                    0f
                }
                if (motionTick < 0) return@drawBehind // keeps read live without branching overhead

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
                    geometryCache.rimBrush = Brush.linearGradient(
                        colors = listOf(style.rimTopLeft, style.rimBottomRight),
                        start = Offset.Zero,
                        end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                    )
                }

                val outline = geometryCache.cachedOutline ?: return@drawBehind
                val coords = coordsRef.value
                val posInRoot = if (coords != null && coords.isAttached) {
                    try {
                        coords.positionInRoot()
                    } catch (_: Throwable) {
                        Offset.Zero
                    }
                } else {
                    Offset.Zero
                }

                val sheen = geometryCache.sheenBrush
                val rim = geometryCache.rimBrush

                clipPath(geometryCache.clipPath) {
                    val activeLayer = if (useLiveSceneBackdrop) {
                        backdrop.sceneBlurredLayer ?: backdrop.wallpaperBlurredLayer
                    } else {
                        backdrop.wallpaperBlurredLayer ?: backdrop.sceneBlurredLayer
                    }
                    val activeFallbackBmp = backdrop.fallbackBlurredBitmap

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
                        val scale = maxOf(
                            rootW.toFloat() / bmp.width.coerceAtLeast(1),
                            rootH.toFloat() / bmp.height.coerceAtLeast(1)
                        )
                        val dstW = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                        val dstH = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                        val offsetX = ((rootW - dstW) / 2f) - posInRoot.x
                        // Apply live WebView scroll delta (wrapped smoothly if scrolling fast before next snapshot)
                        val wrappedShiftY = if (backdrop.isWebBackdrop && dstH > 0) {
                            (webScrollShiftY % dstH)
                        } else {
                            0f
                        }
                        val offsetY = ((rootH - dstH) / 2f) - posInRoot.y - wrappedShiftY
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                            dstSize = IntSize(dstW, dstH)
                        )
                        // Seamless vertical tile fill when rapid WebView scrolling shifts past top/bottom edge
                        if (backdrop.isWebBackdrop && abs(wrappedShiftY) > 1f) {
                            val neighborY = if (wrappedShiftY > 0f) offsetY + dstH else offsetY - dstH
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset(offsetX.roundToInt(), neighborY.roundToInt()),
                                dstSize = IntSize(dstW, dstH)
                            )
                        }
                    }

                    val effectiveTintAlpha = if (hasSampledBackdrop) {
                        tintAlpha
                    } else {
                        (tintAlpha + 0.14f).coerceAtMost(0.88f)
                    }
                    drawRect(color = style.glassBaseColor.copy(alpha = effectiveTintAlpha))

                    if (sheen != null) {
                        drawRect(brush = sheen)
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
 * Makes any floating frosted-glass pill, orb, or card freely movable across the screen with
 * tactile inertia and live backdrop refraction tracking as it moves.
 */
fun Modifier.draggableGlassSurface(
    enabled: Boolean = true,
    maxHorizontalOffsetPx: Float = 900f,
    maxVerticalOffsetPx: Float = 1400f
): Modifier = composed {
    if (!enabled) return@composed this
    val backdrop = LocalSpatialBackdrop.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    this
        .offset {
            IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt())
        }
        .pointerInput(maxHorizontalOffsetPx, maxVerticalOffsetPx) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    val nextX = (offsetX.value + dragAmount.x)
                        .coerceIn(-maxHorizontalOffsetPx, maxHorizontalOffsetPx)
                    val nextY = (offsetY.value + dragAmount.y)
                        .coerceIn(-maxVerticalOffsetPx, maxVerticalOffsetPx)
                    scope.launch {
                        offsetX.snapTo(nextX)
                        offsetY.snapTo(nextY)
                        backdrop.motionTracker.notifyMoved()
                    }
                },
                onDragEnd = {
                    // Subtle spring settle for physical glass feel
                    scope.launch {
                        offsetX.animateTo(
                            targetValue = offsetX.value,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f)
                        ) {
                            backdrop.motionTracker.notifyMoved()
                        }
                    }
                }
            )
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
 * Rubber-Banding (Bouncing) Overscroll + Live Frosted-Glass Scroll Tracking Modifier:
 * - Notifies [GlassMotionTracker] on every consumed scroll delta and elastic bounce frame so
 *   both scrolling glass cards and stationary floating bars continuously refract the live content beneath.
 */
fun Modifier.rubberBandOverscroll(
    maxOverscrollPx: Float = 380f,
    onEdgeHit: ((velocityPxPerSec: Float) -> Unit)? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val backdrop = LocalSpatialBackdrop.current
    val overscrollY = remember { Animatable(0f) }

    val connection = remember(maxOverscrollPx, onEdgeHit, backdrop.motionTracker) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollY.value
                if (source == NestedScrollSource.UserInput && abs(current) > 0.5f) {
                    val sameSign = (current > 0f && available.y > 0f) || (current < 0f && available.y < 0f)
                    if (!sameSign) {
                        val next = if (current > 0f) {
                            (current + available.y).coerceAtLeast(0f)
                        } else {
                            (current + available.y).coerceAtMost(0f)
                        }
                        val consumedY = next - current
                        scope.launch {
                            overscrollY.snapTo(next)
                            backdrop.motionTracker.notifyMoved()
                        }
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
                if (consumed.x != 0f || consumed.y != 0f) {
                    backdrop.motionTracker.notifyMoved()
                }
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.05f) {
                    val current = overscrollY.value
                    val normalized = (abs(current) / maxOverscrollPx).coerceIn(0f, 2f)
                    val resistance = 0.55f / (1f + normalized).pow(1.35f)
                    val dampedDelta = available.y * resistance
                    val target = (current + dampedDelta).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                    scope.launch {
                        overscrollY.snapTo(target)
                        backdrop.motionTracker.notifyMoved()
                    }
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
                        ) {
                            backdrop.motionTracker.notifyMoved()
                        }
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                backdrop.motionTracker.notifyMoved()
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
                        ) {
                            backdrop.motionTracker.notifyMoved()
                        }
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
