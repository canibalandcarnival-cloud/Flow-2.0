package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.theme.LocalGlassStyle
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LiquidIceSilver = Color(0xFFF5F9FA)
private val LiquidPrismBlue = Color(0xFF7EC8E3)
private val LiquidPrismCyan = Color(0xFF38BDF8)
private val LiquidPrismSlate = Color(0xFF647D92)
private val LiquidPrismIndigo = Color(0xFF818CF8)

/**
 * 1. BROWSER OPENING ANIMATION:
 * Spatial Liquid-Glass cold-start entrance featuring the custom "GL • Glass Browser" brand emblem,
 * prismatic orbital rings, diagonal specular sweep, and smooth zoom-through dissolve into the Start Page.
 */
@Composable
fun BrowserOpeningGlassAnimation(
    visible: Boolean,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val emblemScale = remember { Animatable(0.76f) }
    val emblemAlpha = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }
    val ringSweep = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                emblemAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }
            launch {
                emblemScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.64f, stiffness = 300f)
                )
            }
            launch {
                ringSweep.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
                )
            }
        }
        delay(120L)
        coroutineScope {
            launch {
                emblemScale.animateTo(
                    targetValue = 1.12f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                )
            }
            launch {
                emblemAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            }
            launch {
                backdropAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )
            }
        }
        onAnimationFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdropAlpha.value }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFAFDFF),
                        LiquidIceSilver,
                        Color(0xFFE6EEF3)
                    )
                )
            )
            .testTag("browser_opening_animation")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAnimationFinished
            )
    ) {
        // Ambient Refractive Glass Halo Rings & Specular Diagonal Sweep
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minDim = size.minDimension
            val progress = ringSweep.value

            val outerRadius = minDim * (0.34f + 0.14f * progress)
            val innerRadius = minDim * (0.24f + 0.08f * progress)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LiquidPrismBlue.copy(alpha = 0.22f * (1f - progress * 0.35f)),
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = outerRadius * 1.3f
                ),
                radius = outerRadius * 1.3f,
                center = Offset(cx, cy)
            )

            rotate(degrees = progress * 140f, pivot = Offset(cx, cy)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            LiquidPrismBlue.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.15f),
                            LiquidPrismCyan.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.85f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    radius = outerRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = innerRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    scaleX = emblemScale.value
                    scaleY = emblemScale.value
                    alpha = emblemAlpha.value
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(36.dp),
                        ambientColor = LiquidPrismSlate.copy(alpha = 0.22f),
                        spotColor = LiquidPrismSlate.copy(alpha = 0.26f)
                    )
                    .clip(RoundedCornerShape(36.dp))
                    .background(LiquidIceSilver)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                LiquidPrismBlue.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(36.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.brand_glass_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                )
            }

            // Luminous capsule pill below emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.72f))
                    .border(
                        width = 1.dp,
                        color = LiquidPrismBlue.copy(alpha = 0.36f),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidPrismSpinnerOrb(sizeDp = 16)
                    Text(
                        text = stringResource(R.string.opening_browser_ready_pill),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF475B6D)
                    )
                }
            }
        }
    }
}

/**
 * 2. SITE OPENING PORTAL TRANSITION CONTAINER:
 * Smoothly animates between the Start Page (`false`) and active Website (`true`) using spatial
 * depth scaling, rounded glass portal framing, and spring physics.
 */
@Composable
fun SitePortalTransitionContainer(
    isViewingWebPage: Boolean,
    modifier: Modifier = Modifier,
    webContent: @Composable () -> Unit,
    startPageContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isViewingWebPage,
        transitionSpec = {
            if (targetState) {
                // Opening a site: incoming webpage springs forward from a rounded portal while Start Page zooms deep
                (
                    fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.91f,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 340f)
                        ) +
                        slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight / 14 },
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f)
                        )
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 1.07f,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        )
                ).using(SizeTransform(clip = false))
            } else {
                // Returning to Start Page: webpage shrinks cleanly back into the Start Page canvas
                (
                    fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.95f,
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = 360f)
                        )
                    ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.90f,
                            animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing)
                        )
                ).using(SizeTransform(clip = false))
            }
        },
        label = "sitePortalTransition",
        modifier = modifier
    ) { viewingSite ->
        if (viewingSite) {
            webContent()
        } else {
            startPageContent()
        }
    }
}

/**
 * Non-blocking Liquid-Glass Portal Ripple Veil that plays for ~500ms whenever a user opens a site
 * (either from the Start Page or jumping to a new domain), adding a tactile refractive lens reveal.
 */
@Composable
fun SiteOpeningGlassVeil(
    siteTransitionNonce: Long,
    siteBrand: String,
    modifier: Modifier = Modifier
) {
    if (siteTransitionNonce <= 0L) return

    var active by remember(siteTransitionNonce) { mutableStateOf(true) }
    val portalProgress = remember(siteTransitionNonce) { Animatable(0f) }

    LaunchedEffect(siteTransitionNonce) {
        active = true
        portalProgress.snapTo(0f)
        portalProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
        active = false
    }

    if (!active) return

    val p = portalProgress.value
    val fadeEnvelope = sin(p * PI).toFloat().coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .testTag("site_opening_animation")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.maxDimension * 0.72f
            val currentR = maxR * (0.18f + 0.82f * p)
            val corner = (28f * (1f - p)).dp.toPx()

            // Expanding prismatic portal wave
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        LiquidPrismCyan.copy(alpha = 0.18f * fadeEnvelope),
                        Color.White.copy(alpha = 0.28f * fadeEnvelope),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = currentR.coerceAtLeast(16f)
                ),
                radius = currentR.coerceAtLeast(16f),
                center = Offset(cx, cy)
            )

            // Morphing perimeter glass frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f * (1f - p)),
                        LiquidPrismBlue.copy(alpha = 0.35f * (1f - p)),
                        Color.White.copy(alpha = 0.45f * (1f - p))
                    )
                ),
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = Size(
                    (size.width - 6.dp.toPx()).coerceAtLeast(1f),
                    (size.height - 6.dp.toPx()).coerceAtLeast(1f)
                ),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }

        if (siteBrand.isNotBlank() && p < 0.85f) {
            FrostedGlassBox(
                shape = RoundedCornerShape(50),
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier.graphicsLayer {
                    val pillScale = 0.88f + 0.14f * p
                    scaleX = pillScale
                    scaleY = pillScale
                    alpha = (1f - (p / 0.85f)).coerceIn(0f, 1f)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(LiquidPrismCyan, LiquidPrismIndigo)
                                )
                            )
                    ) {
                        Text(
                            text = siteBrand.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.opening_site_format, siteBrand),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalGlassStyle.current.contentPrimary
                    )
                }
            }
        }
    }
}

/**
 * 3. WEBPAGE LOADING ANIMATION:
 * Dual-layer Liquid-Glass loading feedback while a webpage loads:
 *  - Luminous animated multi-stop liquid-wave ribbon along the top edge (`web_loading_ribbon`).
 *  - Compact floating frosted-glass pill with dual counter-rotating prism rings, site title, and
 *    live animated progress percentage (`web_loading_animation`), passing all touches cleanly through.
 */
@Composable
fun WebPageGlassLoadingAnimation(
    visible: Boolean,
    progress: Float,
    siteTitleOrDomain: String,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val clampedTarget = if (visible) progress.coerceIn(0.08f, 0.99f) else 1f
    val smoothProgress by animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 260f),
        label = "smoothWebLoadProgress"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f)
        ),
        exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.95f, animationSpec = tween(220)),
        modifier = modifier.fillMaxWidth()
    ) {
        val infinite = rememberInfiniteTransition(label = "webGlassLoading")
        val wavePhase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "loadingWavePhase"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Top Luminous Liquid-Glass Wave Ribbon
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("web_loading_ribbon")
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // Subtle frosted track
                drawRect(
                    color = Color.White.copy(alpha = 0.16f),
                    size = Size(w, h)
                )

                val activeW = (w * smoothProgress).coerceIn(24.dp.toPx(), w)
                val shimmerCenter = (activeW * wavePhase).coerceIn(0f, activeW)

                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            LiquidPrismBlue,
                            LiquidPrismCyan,
                            Color.White,
                            LiquidPrismIndigo,
                            LiquidPrismCyan
                        ),
                        startX = shimmerCenter - activeW * 0.6f,
                        endX = shimmerCenter + activeW * 0.6f
                    ),
                    topLeft = Offset.Zero,
                    size = Size(activeW, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )

                // Specular comet tip glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            LiquidPrismCyan.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(activeW, h / 2f),
                        radius = 10.dp.toPx()
                    ),
                    radius = 10.dp.toPx(),
                    center = Offset(activeW, h / 2f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact Floating Liquid-Glass Orbital Loading Pill
            FrostedGlassBox(
                shape = RoundedCornerShape(50),
                elevation = GlassElevation.FLOATING_BAR,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag("web_loading_animation")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    LiquidPrismSpinnerOrb(sizeDp = 18)

                    val labelText = if (siteTitleOrDomain.isNotBlank()) {
                        stringResource(R.string.loading_site_format, siteTitleOrDomain)
                    } else {
                        stringResource(R.string.loading_web_fallback)
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = style.contentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 190.dp)
                    )

                    val pct = (smoothProgress * 100f).roundToInt().coerceIn(8, 99)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(style.activeSegmentFill)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = style.contentSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dual-ring counter-rotating Liquid-Glass prism spinner with pulsing luminous core droplet.
 */
@Composable
fun LiquidPrismSpinnerOrb(
    sizeDp: Int = 18,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "liquidPrismSpinner")
    val outerAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerSpin"
    )
    val corePulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val strokeOuter = 2.dp.toPx()
        val strokeInner = 1.5.dp.toPx()

        rotate(degrees = outerAngle, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        LiquidPrismBlue,
                        LiquidPrismCyan,
                        Color.White
                    ),
                    center = center
                ),
                startAngle = 15f,
                sweepAngle = 265f,
                useCenter = false,
                topLeft = Offset(strokeOuter, strokeOuter),
                size = Size(
                    (size.width - strokeOuter * 2f).coerceAtLeast(2f),
                    (size.height - strokeOuter * 2f).coerceAtLeast(2f)
                ),
                style = Stroke(width = strokeOuter, cap = StrokeCap.Round)
            )
        }

        rotate(degrees = -outerAngle * 1.35f, pivot = center) {
            val inset = r * 0.34f
            drawArc(
                color = LiquidPrismIndigo.copy(alpha = 0.85f),
                startAngle = 120f,
                sweepAngle = 190f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(
                    (size.width - inset * 2f).coerceAtLeast(2f),
                    (size.height - inset * 2f).coerceAtLeast(2f)
                ),
                style = Stroke(width = strokeInner, cap = StrokeCap.Round)
            )
        }

        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = (r * 0.20f * corePulse).coerceAtLeast(1f),
            center = center
        )
    }
}
