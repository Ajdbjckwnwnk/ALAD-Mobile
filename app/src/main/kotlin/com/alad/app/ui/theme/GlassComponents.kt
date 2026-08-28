package com.alad.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen ambient lighting container that renders smooth background glow orbs
 * to give realistic depth and refraction behind translucent glass cards.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-Right Cyan/Blue Ambient Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.18f * pulseAlpha),
                        NeonViolet.copy(alpha = 0.08f * pulseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f, height * 0.15f),
                    radius = width * 0.75f
                )
            )

            // Center-Left Indigo/Purple Ambient Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonPurple.copy(alpha = 0.14f * pulseAlpha),
                        NeonBlue.copy(alpha = 0.05f * pulseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.1f, height * 0.55f),
                    radius = width * 0.8f
                )
            )

            // Bottom-Center Emerald/Teal Subtle Floor Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentTeal.copy(alpha = 0.12f * pulseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.95f),
                    radius = width * 0.7f
                )
            )
        }

        content()
    }
}

/**
 * Glassmorphic container with frosted translucent surface, dual-tone gradient fill,
 * and high-definition crystal border reflection.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = GlassSurfaceDark,
    borderColor: Color = Color.White,
    borderAlpha: Float = 0.20f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glassBrush = Brush.verticalGradient(
        colors = listOf(
            backgroundColor.copy(alpha = 0.75f),
            backgroundColor.copy(alpha = 0.45f)
        )
    )

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            borderColor.copy(alpha = borderAlpha),
            borderColor.copy(alpha = borderAlpha * 0.2f),
            borderColor.copy(alpha = borderAlpha * 0.6f)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickableModifier)
            .background(glassBrush)
            .border(BorderStroke(1.dp, borderBrush), shape = shape)
    ) {
        content()
    }
}

/**
 * Glassmorphic circular icon button with frosted backdrop and neon tint.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Status badge with glowing animated pulsing dot.
 */
@Composable
fun StatusBadge(
    isConnected: Boolean,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val glowColor = if (isConnected) NeonCyan else NeonCoral
    
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.6f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnected) 1000 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x500E1726),
        borderColor = glowColor,
        borderAlpha = if (isConnected) 0.45f else 0.25f
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing Halo
                Box(
                    modifier = Modifier
                        .size(10.dp * pulseScale)
                        .clip(CircleShape)
                        .background(glowColor.copy(alpha = if (isConnected) 0.35f else 0.2f))
                )
                // Core Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(glowColor)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Color.White else TextSecondary,
                    fontSize = 15.sp
                )
            )
        }
    }
}
