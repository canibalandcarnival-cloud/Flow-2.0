package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FavoriteSiteEntity
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite

/**
 * Renders crisp editorial vector icons & squircle badges for Favorites on the Start Page,
 * inspired by the reference Start Page grid while maintaining razor-sharp vector clarity at any DPI.
 */
@Composable
fun FavoriteSquircleIcon(
    site: FavoriteSiteEntity,
    size: Dp = 68.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val bgBrush = when (site.bgStyle) {
        "DARK_ESPRESSO" -> Brush.linearGradient(listOf(Color(0xFF231F1A), Color(0xFF171411)))
        "SOFT_PRISM" -> Brush.linearGradient(
            listOf(Color(0xFFE6E6E6), Color(0xFFCFCFCF), Color(0xFFB5B5B5))
        )
        "PURE_BLACK" -> Brush.linearGradient(listOf(Color(0xFF0B0B0B), Color(0xFF000000)))
        "SLATE_GRAY" -> Brush.linearGradient(listOf(Color(0xFF6E7170), Color(0xFF5A5D5C)))
        "BLUSH_LIGHT" -> Brush.linearGradient(listOf(Color(0xFFF3E9EA), Color(0xFFE8DCDD)))
        "PURE_WHITE" -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F1F1)))
        "SILVER_GRAY" -> Brush.linearGradient(listOf(Color(0xFFC8CACF), Color(0xFFB6B9BF)))
        else -> Brush.linearGradient(listOf(Color(0xFF141414), Color(0xFF050505)))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgBrush)
            .border(0.75.dp, PureWhite.copy(alpha = 0.16f), shape)
    ) {
        when (site.iconType) {
            "GEOMETRIC_A" -> {
                Canvas(modifier = Modifier.size(size * 0.66f)) {
                    val r = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val strokeColor = Color(0xFFC4B08A)
                    val sw = this.size.minDimension * 0.065f
                    drawCircle(color = strokeColor, radius = r * 0.90f, center = center, style = Stroke(sw))
                    val tri = Path().apply {
                        moveTo(center.x, center.y - r * 0.84f)
                        lineTo(center.x + r * 0.74f, center.y + r * 0.56f)
                        lineTo(center.x - r * 0.74f, center.y + r * 0.56f)
                        close()
                    }
                    drawPath(tri, color = strokeColor, style = Stroke(sw, join = StrokeJoin.Round))
                    drawCircle(color = strokeColor, radius = r * 0.36f, center = Offset(center.x, center.y + r * 0.12f), style = Stroke(sw))
                }
            }
            "CC_SLASH" -> {
                Text(
                    text = "CC/",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp
                    ),
                    color = PureBlack
                )
            }
            "BOLD_S" -> {
                Text(
                    text = "S",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = PureWhite
                )
            }
            "MONO_M" -> {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = PureWhite
                )
            }
            "DROPLET" -> {
                Canvas(modifier = Modifier.size(size * 0.62f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val drop = Path().apply {
                        moveTo(w * 0.5f, h * 0.08f)
                        cubicTo(w * 0.82f, h * 0.48f, w * 0.86f, h * 0.78f, w * 0.5f, h * 0.92f)
                        cubicTo(w * 0.14f, h * 0.78f, w * 0.18f, h * 0.48f, w * 0.5f, h * 0.08f)
                        close()
                    }
                    drawPath(drop, color = PureBlack, style = Stroke(width = w * 0.055f))
                }
            }
            "PIN_P" -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(size * 0.60f)
                        .clip(RoundedCornerShape(50))
                        .background(PureBlack)
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = PureWhite
                    )
                }
            }
            "MONO_K" -> {
                Text(
                    text = "K",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = PureWhite
                )
            }
            "EC_MARK" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val sw = w * 0.09f
                    // Horizontal crossbar + dual geometric C arcs matching Reference Image 2
                    drawLine(
                        color = PureWhite,
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w * 0.72f, h * 0.5f),
                        strokeWidth = sw
                    )
                    drawArc(
                        color = PureWhite,
                        startAngle = 60f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.52f, h * 0.56f),
                        style = Stroke(width = sw, cap = StrokeCap.Butt)
                    )
                    drawArc(
                        color = PureWhite,
                        startAngle = 70f,
                        sweepAngle = 220f,
                        useCenter = false,
                        topLeft = Offset(w * 0.70f, h * 0.22f),
                        size = Size(w * 0.52f, h * 0.56f),
                        style = Stroke(width = sw, cap = StrokeCap.Butt)
                    )
                }
            }
            else -> {
                val fg = if (site.bgStyle == "PURE_WHITE" || site.bgStyle == "SILVER_GRAY" || site.bgStyle == "BLUSH_LIGHT") {
                    PureBlack
                } else {
                    PureWhite
                }
                Text(
                    text = site.monogram.take(2),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = fg
                )
            }
        }
    }
}

/**
 * Editorial thumbnail artwork for Reading List cards matching Reference Image 2.
 */
@Composable
fun ReadingListThumbnail(
    artworkStyle: String,
    size: Dp = 76.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
    ) {
        when (artworkStyle) {
            "CERAMIC_CUBE" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    drawRect(color = Color(0xFFF2F2F0))
                    val table = Path().apply {
                        moveTo(0f, h * 0.44f)
                        lineTo(w, h * 0.28f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(table, color = Color(0xFFD68910))
                    // Sculptural clay vessel cube
                    drawRoundRect(
                        color = Color(0xFFA87958),
                        topLeft = Offset(w * 0.34f, h * 0.33f),
                        size = Size(w * 0.30f, h * 0.32f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFF7E5539),
                        topLeft = Offset(w * 0.50f, h * 0.36f),
                        size = Size(w * 0.16f, h * 0.29f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
            "BOTANICAL_FLORAL" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFC5BFC1), Color(0xFFA69F9E))
                        )
                    )
                    // Minimalist stems & blossoms
                    val stemColor = Color(0xFF3A4B34)
                    drawLine(stemColor, Offset(w * 0.5f, h * 0.88f), Offset(w * 0.32f, h * 0.28f), strokeWidth = 3f)
                    drawLine(stemColor, Offset(w * 0.5f, h * 0.88f), Offset(w * 0.66f, h * 0.24f), strokeWidth = 3f)
                    drawLine(stemColor, Offset(w * 0.5f, h * 0.88f), Offset(w * 0.52f, h * 0.42f), strokeWidth = 3.5f)
                    drawCircle(Color(0xFFF5F3F0), radius = w * 0.11f, center = Offset(w * 0.32f, h * 0.28f))
                    drawCircle(Color(0xFFEBE4E8), radius = w * 0.10f, center = Offset(w * 0.66f, h * 0.24f))
                    drawCircle(Color(0xFFFDFCFB), radius = w * 0.13f, center = Offset(w * 0.52f, h * 0.44f))
                    drawCircle(Color(0xFF6B5344), radius = w * 0.03f, center = Offset(w * 0.52f, h * 0.44f))
                    drawRoundRect(
                        color = Color(0xFF4A3D36),
                        topLeft = Offset(w * 0.36f, h * 0.74f),
                        size = Size(w * 0.28f, h * 0.26f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
            "BOLD_S_MONO" -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = PureWhite
                    )
                }
            }
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF222222), Color(0xFF0A0A0A))
                        )
                    )
                    drawCircle(
                        color = PureWhite.copy(alpha = 0.85f),
                        radius = w * 0.26f,
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = 3.5f)
                    )
                    drawCircle(
                        color = PureWhite.copy(alpha = 0.35f),
                        radius = w * 0.14f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Custom Eyeglasses vector icon matching the 3rd segment of the Top Frosted Pill in Reference Image 2.
 */
@Composable
fun EyeglassesIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.085f
        val lensR = w * 0.20f
        val leftCenter = Offset(w * 0.27f, h * 0.54f)
        val rightCenter = Offset(w * 0.73f, h * 0.54f)

        // Left & right round lenses
        drawCircle(color = tint, radius = lensR, center = leftCenter, style = Stroke(width = sw))
        drawCircle(color = tint, radius = lensR, center = rightCenter, style = Stroke(width = sw))

        // Curved nose bridge
        val bridge = Path().apply {
            moveTo(leftCenter.x + lensR * 0.92f, leftCenter.y - lensR * 0.15f)
            quadraticTo(
                w * 0.5f,
                leftCenter.y - lensR * 0.65f,
                rightCenter.x - lensR * 0.92f,
                rightCenter.y - lensR * 0.15f
            )
        }
        drawPath(bridge, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))

        // Tiny outer temple stems
        drawLine(
            color = tint,
            start = Offset(w * 0.02f, leftCenter.y - lensR * 0.08f),
            end = Offset(leftCenter.x - lensR, leftCenter.y - lensR * 0.08f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(rightCenter.x + lensR, rightCenter.y - lensR * 0.08f),
            end = Offset(w * 0.98f, rightCenter.y - lensR * 0.08f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Overlapping dual-squares Tabs icon matching the bottom-right circle button in Reference Image 2.
 */
@Composable
fun OverlappingTabsIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(21.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.088f
        val boxSize = w * 0.58f
        val corner = CornerRadius(w * 0.14f, w * 0.14f)

        // Back top-left square (partial arc/rounded rect)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = sw)
        )
        // Front bottom-right square
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.30f, h * 0.30f),
            size = Size(boxSize, boxSize),
            cornerRadius = corner,
            style = Stroke(width = sw)
        )
    }
}
