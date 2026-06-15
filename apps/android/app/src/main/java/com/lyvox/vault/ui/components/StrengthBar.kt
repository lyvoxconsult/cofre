package com.lyvox.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.lyvox.vault.ui.theme.*

/**
 * Password strength indicator bar.
 *
 * Color-coded and label-matched to the PasswordGenerator strength levels:
 * - < 40 bits:  Fraca  (red)
 * - 40–59:      Razoável (amber)
 * - 60–79:      Boa    (indigo)
 * - 80–99:      Forte  (blue)
 * - ≥ 100:      Muito Forte (green)
 */
@Composable
fun StrengthBar(
    entropyBits: Double,
    label: String,
    modifier: Modifier = Modifier
) {
    val progress = (entropyBits / 120.0).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "strengthProgress"
    )

    val targetColor = when {
        entropyBits < 40 -> StrengthWeak
        entropyBits < 60 -> StrengthFair
        entropyBits < 80 -> StrengthGood
        entropyBits < 100 -> StrengthStrong
        else -> StrengthVeryStrong
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "strengthColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Força",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = animatedColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round,
        )
    }
}
