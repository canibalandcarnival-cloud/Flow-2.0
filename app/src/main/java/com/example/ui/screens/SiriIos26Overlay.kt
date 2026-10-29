package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.SiriAiUiState
import com.example.core.SiriChatTurn
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.components.draggableGlassSurface
import com.example.ui.theme.LocalGlassStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val SiriCyan = Color(0xFF36E2FF)
private val SiriIndigo = Color(0xFF5A6BFF)
private val SiriViolet = Color(0xFF9B4DFF)
private val SiriMagenta = Color(0xFFFF3D9A)
private val SiriCoral = Color(0xFFFF8C42)

/**
 * Animated iOS 26 Siri Liquid-Glass Orb icon rendered via multi-lobe luminous gradients.
 */
@Composable
fun SiriLiquidOrbIcon(
    isThinking: Boolean = false,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "siriOrb")
    val phaseDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 1600 else 3800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "siriOrbPhase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 700 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "siriOrbPulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val radius = (this.size.minDimension / 2f) * pulse.coerceIn(0.85f, 1.02f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val rad = Math.toRadians(phaseDeg.toDouble())

        // Ambient outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SiriMagenta.copy(alpha = 0.42f),
                    SiriCyan.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.15f
            ),
            radius = radius * 1.15f,
            center = center
        )

        // Core liquid Siri sphere
        rotate(degrees = phaseDeg, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        SiriCyan,
                        SiriIndigo,
                        SiriViolet,
                        SiriMagenta,
                        SiriCoral,
                        SiriCyan
                    ),
                    center = center
                ),
                radius = radius * 0.88f,
                center = center
            )
        }

        // Floating inner plasma lobe
        val lobeOffset = Offset(
            x = center.x + (cos(rad) * radius * 0.22f).toFloat(),
            y = center.y + (sin(rad) * radius * 0.22f).toFloat()
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    SiriCyan.copy(alpha = 0.55f),
                    Color.Transparent
                ),
                center = lobeOffset,
                radius = radius * 0.68f
            ),
            radius = radius * 0.68f,
            center = lobeOffset
        )

        // Specular Liquid-Glass rim
        drawCircle(
            color = Color.White.copy(alpha = 0.72f),
            radius = radius * 0.88f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * Full-viewport iOS 26 Siri Perimeter Edge-Glow Aura:
 * Renders a rotating iridescent light ribbon around the curved screen bezel without intercepting
 * touch events (`Modifier` has no pointerInput, so scrolling the webpage underneath remains 100% fluid).
 */
@Composable
fun SiriIos26PerimeterAura(
    visible: Boolean,
    isIntense: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)),
        exit = fadeOut(tween(280)),
        modifier = modifier.fillMaxSize()
    ) {
        val infinite = rememberInfiniteTransition(label = "siriBezelAura")
        val sweepAngle by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isIntense) 2100 else 4400,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "siriSweepAngle"
        )
        val breathAlpha by infinite.animateFloat(
            initialValue = if (isIntense) 0.72f else 0.45f,
            targetValue = if (isIntense) 1.0f else 0.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isIntense) 760 else 1600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "siriBreathAlpha"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val center = Offset(w / 2f, h / 2f)
            val corner = 36.dp.toPx()

            // Smooth phase-shifted color stops around exact rectangular screen perimeter
            val phase = (sweepAngle / 360f)
            val palette = listOf(SiriCyan, SiriIndigo, SiriViolet, SiriMagenta, SiriCoral)
            fun colorAt(slot: Int): Color {
                val pos = ((slot.toFloat() / palette.size) + phase) % 1f
                val scaled = pos * palette.size
                val idx = scaled.toInt() % palette.size
                val next = (idx + 1) % palette.size
                val frac = scaled - scaled.toInt()
                val c1 = palette[idx]
                val c2 = palette[next]
                return Color(
                    red = c1.red + (c2.red - c1.red) * frac,
                    green = c1.green + (c2.green - c1.green) * frac,
                    blue = c1.blue + (c2.blue - c1.blue) * frac,
                    alpha = breathAlpha
                )
            }

            val dynamicSweep = Brush.sweepGradient(
                colors = listOf(
                    colorAt(0),
                    colorAt(1),
                    colorAt(2),
                    colorAt(3),
                    colorAt(4),
                    colorAt(0)
                ),
                center = center
            )

            val outerStroke = 18.dp.toPx()
            val midStroke = 9.dp.toPx()
            val innerStroke = 3.dp.toPx()

            drawRoundRect(
                brush = dynamicSweep,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(corner, corner),
                alpha = 0.22f,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
            drawRoundRect(
                brush = dynamicSweep,
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
                cornerRadius = CornerRadius(corner, corner),
                alpha = 0.44f,
                style = Stroke(width = midStroke, cap = StrokeCap.Round)
            )
            drawRoundRect(
                brush = dynamicSweep,
                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
                cornerRadius = CornerRadius(corner, corner),
                alpha = 0.92f,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Floating & movable iOS 26 Siri Liquid-Glass Assistant card + composer pill.
 * Dynamically tracks and refracts whatever content is scrolling or moving directly beneath it!
 */
@Composable
fun SiriIos26AssistantOverlay(
    aiState: SiriAiUiState,
    chatHistory: List<SiriChatTurn>,
    isViewingWebPage: Boolean,
    activePageTitle: String,
    onSubmitPrompt: (String) -> Unit,
    onSummarizePage: () -> Unit,
    onAnalyzeVisualScreen: (String) -> Unit,
    onKeyTakeaways: () -> Unit,
    onSmartDigest: () -> Unit,
    onTranslatePage: () -> Unit,
    onOpenSuggestedUrl: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onClearConversation: () -> Unit,
    onDismiss: () -> Unit,
    onVoiceUnavailableToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    val isBusy = aiState is SiriAiUiState.Loading || aiState is SiriAiUiState.Streaming

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
                .trim()
            if (spoken.isNotEmpty()) {
                promptText = ""
                onSubmitPrompt(spoken)
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = metrics.sheetMaxWidth)
                .fillMaxWidth()
                .draggableGlassSurface(
                    maxHorizontalOffsetPx = 540f,
                    maxVerticalOffsetPx = 1100f
                )
                .testTag("siri_ios26_overlay_card")
        ) {
            // 1. Live Siri Intelligence Response / Digest / History Card
            if (aiState !is SiriAiUiState.Idle || chatHistory.isNotEmpty()) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(30.dp),
                    elevation = GlassElevation.MODAL_OVERLAY,
                    useLiveSceneBackdrop = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Drag indicator + Header + Clear/Close
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                SiriLiquidOrbIcon(isThinking = isBusy, size = 24.dp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.siri_ai_title),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = style.contentPrimary
                                    )
                                    Text(
                                        text = if (isViewingWebPage && activePageTitle.isNotBlank()) {
                                            activePageTitle
                                        } else {
                                            stringResource(R.string.siri_ai_subtitle)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = style.contentSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = "Drag Siri card",
                                tint = style.contentMuted,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(20.dp)
                            )

                            if (chatHistory.isNotEmpty() && !isBusy) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .bouncyClickable(
                                            pressedScale = 0.88f,
                                            contentDescriptionText = "Reset Siri chat",
                                            onClick = onClearConversation
                                        )
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = style.contentSecondary
                                    )
                                }
                            }
                        }

                        // Body Content by State
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (aiState) {
                                SiriAiUiState.Idle -> {
                                    val last = chatHistory.lastOrNull()
                                    if (last != null) {
                                        SiriAnswerBody(
                                            prompt = last.userPrompt,
                                            answer = last.modelReply,
                                            suggestedUrl = null,
                                            context = context,
                                            onOpenSuggestedUrl = onOpenSuggestedUrl
                                        )
                                    }
                                }
                                is SiriAiUiState.Loading -> {
                                    Text(
                                        text = "“${aiState.activePrompt}”",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = style.contentSecondary
                                    )
                                    Text(
                                        text = if (aiState.isVision) {
                                            "Inspecting live screen pixels with Gemini 3.8 Flash…"
                                        } else {
                                            stringResource(R.string.siri_listening_hint)
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = style.contentPrimary
                                    )
                                }
                                is SiriAiUiState.Streaming -> {
                                    Text(
                                        text = "“${aiState.activePrompt}”",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = style.contentSecondary
                                    )
                                    Text(
                                        text = aiState.partialText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = style.contentPrimary,
                                        modifier = Modifier.testTag("siri_streaming_text")
                                    )
                                }
                                is SiriAiUiState.Success -> {
                                    SiriAnswerBody(
                                        prompt = aiState.prompt,
                                        answer = aiState.text,
                                        suggestedUrl = aiState.suggestedNavigateUrl,
                                        context = context,
                                        onOpenSuggestedUrl = onOpenSuggestedUrl
                                    )
                                }
                                is SiriAiUiState.DigestSuccess -> {
                                    val digest = aiState.digest
                                    Text(
                                        text = digest.headline,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = style.contentPrimary
                                    )
                                    if (digest.tldr.isNotBlank()) {
                                        Text(
                                            text = digest.tldr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = style.contentSecondary
                                        )
                                    }
                                    digest.keyPoints.forEach { point ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("•", color = SiriCyan, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = point,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = style.contentPrimary
                                            )
                                        }
                                    }
                                    if (digest.suggestedFollowUps.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            digest.suggestedFollowUps.forEach { followUp ->
                                                SiriQuickChip(
                                                    label = followUp,
                                                    onClick = { onSubmitPrompt(followUp) }
                                                )
                                            }
                                        }
                                    }
                                    if (!digest.suggestedActionUrl.isNullOrBlank()) {
                                        SiriQuickChip(
                                            label = "Open ${digest.suggestedActionUrl}",
                                            onClick = { onOpenSuggestedUrl(digest.suggestedActionUrl) }
                                        )
                                    }
                                }
                                is SiriAiUiState.Error -> {
                                    Text(
                                        text = aiState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFFF6B81),
                                        modifier = Modifier.testTag("siri_error_message")
                                    )
                                    if (!aiState.retryPrompt.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(style.activeSegmentFill)
                                                .testTag("siri_retry_button")
                                                .bouncyClickable(
                                                    pressedScale = 0.90f,
                                                    contentDescriptionText = "Retry Siri request",
                                                    onClick = { onSubmitPrompt(aiState.retryPrompt) }
                                                )
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                tint = style.contentPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Retry",
                                                style = MaterialTheme.typography.labelLarge,
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

            // 2. Quick Contextual Siri Capabilities Strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                if (isViewingWebPage) {
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_summarize),
                        tag = "siri_chip_summarize",
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = SiriCyan, modifier = Modifier.size(15.dp))
                        },
                        onClick = onSummarizePage
                    )
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_visual_screen),
                        tag = "siri_chip_vision",
                        leadingIcon = {
                            Icon(Icons.Outlined.Visibility, null, tint = SiriMagenta, modifier = Modifier.size(15.dp))
                        },
                        onClick = {
                            onAnalyzeVisualScreen(
                                promptText.trim().ifBlank {
                                    "Inspect this webpage screenshot visually and explain what is shown, key UI/media highlights, and main information."
                                }
                            )
                        }
                    )
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_key_points),
                        tag = "siri_chip_keypoints",
                        onClick = onKeyTakeaways
                    )
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_smart_digest),
                        tag = "siri_chip_digest",
                        onClick = onSmartDigest
                    )
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_translate),
                        tag = "siri_chip_translate",
                        onClick = onTranslatePage
                    )
                } else {
                    SiriQuickChip(
                        label = stringResource(R.string.siri_chip_smart_digest),
                        tag = "siri_chip_digest",
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = SiriCyan, modifier = Modifier.size(15.dp))
                        },
                        onClick = onSmartDigest
                    )
                    SiriQuickChip(
                        label = "Research AI News",
                        onClick = { onSubmitPrompt("Give me a crisp overview of modern spatial computing and Liquid Glass interfaces.") }
                    )
                    SiriQuickChip(
                        label = "Open Wikipedia",
                        onClick = { onSubmitPrompt("Open Wikipedia and tell me 3 fascinating architecture facts.") }
                    )
                    SiriQuickChip(
                        label = "Compare Web Tech",
                        onClick = { onSubmitPrompt("Compare WebGPU vs WebGL2 for 120Hz mobile browser rendering.") }
                    )
                }
            }

            // 3. iOS 26 Type-to-Siri & Voice Frosted Capsule Pill
            FrostedGlassBox(
                shape = RoundedCornerShape(32.dp),
                elevation = GlassElevation.MODAL_OVERLAY,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    SiriLiquidOrbIcon(
                        isThinking = isBusy,
                        size = 30.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (promptText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.siri_input_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = style.contentMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = style.contentPrimary
                            ),
                            cursorBrush = SolidColor(SiriCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val clean = promptText.trim()
                                    if (clean.isNotEmpty()) {
                                        promptText = ""
                                        onSubmitPrompt(clean)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("siri_prompt_input")
                        )
                    }

                    // Voice Dictation Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("siri_voice_button")
                            .bouncyClickable(
                                pressedScale = 0.86f,
                                contentDescriptionText = stringResource(R.string.cd_siri_voice_button),
                                onClick = {
                                    try {
                                        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Siri Intelligence")
                                        }
                                        speechLauncher.launch(voiceIntent)
                                    } catch (_: Throwable) {
                                        onVoiceUnavailableToast("Voice recognizer unavailable on this device — use Type to Siri")
                                    }
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MicNone,
                            contentDescription = stringResource(R.string.cd_siri_voice_button),
                            tint = style.contentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Send or Stop Button
                    if (isBusy) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(style.activeSegmentFill)
                                .testTag("siri_stop_button")
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    contentDescriptionText = "Stop Siri response",
                                    onClick = onCancelGeneration
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Stop Siri response",
                                tint = style.contentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (promptText.isNotBlank()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(SiriCyan, SiriViolet, SiriMagenta)
                                    )
                                )
                                .testTag("siri_send_button")
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    contentDescriptionText = stringResource(R.string.cd_siri_send_button),
                                    onClick = {
                                        val clean = promptText.trim()
                                        if (clean.isNotEmpty()) {
                                            promptText = ""
                                            onSubmitPrompt(clean)
                                        }
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.cd_siri_send_button),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("siri_close_button")
                                .bouncyClickable(
                                    pressedScale = 0.86f,
                                    contentDescriptionText = stringResource(R.string.cd_siri_close_button),
                                    onClick = onDismiss
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_siri_close_button),
                                tint = style.contentSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiriAnswerBody(
    prompt: String,
    answer: String,
    suggestedUrl: String?,
    context: Context,
    onOpenSuggestedUrl: (String) -> Unit
) {
    val style = LocalGlassStyle.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "“$prompt”",
            style = MaterialTheme.typography.labelMedium,
            color = style.contentSecondary
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodyMedium,
            color = style.contentPrimary,
            modifier = Modifier.testTag("siri_response_text")
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(48.dp)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(style.activeSegmentFill)
                    .bouncyClickable(
                        pressedScale = 0.90f,
                        contentDescriptionText = "Copy Siri answer",
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Siri Response", answer))
                        }
                    )
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = style.contentPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = style.contentPrimary
                )
            }

            if (!suggestedUrl.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(style.solidActionFill)
                        .bouncyClickable(
                            pressedScale = 0.90f,
                            contentDescriptionText = "Open suggested link",
                            onClick = { onOpenSuggestedUrl(suggestedUrl) }
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = style.solidActionContent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Open Site",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = style.solidActionContent
                    )
                }
            }
        }
    }
}

@Composable
private fun SiriQuickChip(
    label: String,
    tag: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(50),
        elevation = GlassElevation.FLOATING_BAR,
        useLiveSceneBackdrop = true,
        modifier = Modifier
            .height(48.dp)
            .let { if (tag.isNotEmpty()) it.testTag(tag) else it }
            .bouncyClickable(
                pressedScale = 0.91f,
                contentDescriptionText = label,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = style.contentPrimary,
                maxLines = 1
            )
        }
    }
}
