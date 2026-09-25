package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    var statusText by remember { mutableStateOf("Initializing Studio...") }

    // Initial spring entrance
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(600))
        contentAlpha.animateTo(1f, tween(800, delayMillis = 200))

        delay(700)
        statusText = "Loading Auto-Crop Engine..."
        delay(700)
        statusText = "Setting Up Zero-Margin Layouts..."
        delay(700)
        statusText = "Ready to Print!"
        delay(400)
        onSplashFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splashAnimations")

    // Slow pulsing glow
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotating glowing ring
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    // Scanner beam pulse
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14), // Deep Obsidian
                        Color(0xFF0F172A), // Slate Navy
                        Color(0xFF1E293B), // Dark Slate
                        Color(0xFF172554)  // Deep Blue
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background tech grid / radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x332563EB),
                        Color(0x1500E5FF),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.65f
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .alpha(alpha.value)
        ) {
            // ================= HERO LOGO BADGE WITH DYNAMIC GLOW =================
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(scale.value * pulseScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating gradient halo ring
                Box(
                    modifier = Modifier
                        .size(165.dp)
                        .rotate(rotationAngle)
                        .clip(CircleShape)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF), // Neon Cyan
                                    Color(0xFF3B82F6), // Royal Blue
                                    Color(0xFFF59E0B), // Amber Gold
                                    Color(0xFF10B981), // Emerald
                                    Color(0xFF00E5FF)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Secondary soft glow circle
                Box(
                    modifier = Modifier
                        .size(145.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x501D4ED8),
                                    Color(0x200F172A)
                                )
                            )
                        )
                )

                // Inner Logo Card
                Image(
                    painter = painterResource(id = R.drawable.ic_doc_a4_logo),
                    contentDescription = "Doc Album Logo",
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ================= BRAND TITLE & TYPOGRAPHY =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Doc Album",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF2563EB),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "A4",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Dual-Side Auto-Crop & Margin-Free A4 Studio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF93C5FD),
                letterSpacing = 0.2.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ================= FEATURE HIGHLIGHT PILLS =================
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(contentAlpha.value)
            ) {
                FeatureBadge(label = "Official PDF")
                FeatureBadge(label = "Aadhaar & PAN")
                FeatureBadge(label = "Zero Margin")
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ================= DYNAMIC STATUS & LOADING BAR =================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.alpha(contentAlpha.value)
            ) {
                // Sleek progress bar
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(scanY)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8),
                                        Color(0xFF2563EB),
                                        Color(0xFF00E5FF)
                                    )
                                )
                            )
                    )
                }

                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // ================= BOTTOM FOOTER =================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = "Version 1.0 • Instant Print Engine",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                text = "Developed By Azazmadkiya\nazazmadkiya.morbi.store",
                color = Color(0xFF38BDF8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://azazmadkiya.morbi.store")
                }
            )
        }
    }
}

@Composable
private fun FeatureBadge(label: String) {
    Surface(
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Text(
            text = label,
            color = Color(0xFFE2E8F0),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
