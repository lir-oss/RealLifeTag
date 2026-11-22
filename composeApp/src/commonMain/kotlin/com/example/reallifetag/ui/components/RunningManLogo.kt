package com.example.reallifetag.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

// Displays rounded squircle badge with dynamic red running figure
@Composable
fun RunningManLogo(sizeDp: Int = 84) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.28f).dp))
            .background(Color(0xFFF5F5F7)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.65f)) {
            val w = size.width
            val h = size.height
            val runnerRed = Color(0xFFD32F2F)
            val stroke = (w * 0.11f).coerceAtLeast(3f)

            // Head
            drawCircle(
                color = runnerRed,
                radius = w * 0.14f,
                center = Offset(w * 0.62f, h * 0.20f)
            )

            // Torso / Body (angled forward)
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.58f, h * 0.32f),
                end = Offset(w * 0.44f, h * 0.60f),
                strokeWidth = stroke * 1.2f,
                cap = StrokeCap.Round
            )

            // Front arm (reaching forward)
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.54f, h * 0.40f),
                end = Offset(w * 0.76f, h * 0.46f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.76f, h * 0.46f),
                end = Offset(w * 0.84f, h * 0.32f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )

            // Back arm (pumping back)
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.50f, h * 0.42f),
                end = Offset(w * 0.30f, h * 0.48f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.30f, h * 0.48f),
                end = Offset(w * 0.22f, h * 0.62f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )

            // Front leg (bent high knee forward)
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.44f, h * 0.60f),
                end = Offset(w * 0.64f, h * 0.72f),
                strokeWidth = stroke * 1.1f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.64f, h * 0.72f),
                end = Offset(w * 0.68f, h * 0.92f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )

            // Back leg (extended backward stride)
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.44f, h * 0.60f),
                end = Offset(w * 0.26f, h * 0.72f),
                strokeWidth = stroke * 1.1f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = runnerRed,
                start = Offset(w * 0.26f, h * 0.72f),
                end = Offset(w * 0.12f, h * 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
