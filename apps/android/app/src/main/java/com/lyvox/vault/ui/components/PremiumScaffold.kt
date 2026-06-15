package com.lyvox.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBg
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.PremiumBlue

val PremiumScreenBrush = Brush.radialGradient(
    colors = listOf(Color(0x663C1E8F), Color.Transparent),
    radius = 760f
)

val PremiumButtonBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8B1CF6), Color(0xFF5A7CFF), Color(0xFF60A5FA))
)

@Composable
fun PremiumScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .background(PremiumScreenBrush)
    ) {
        content()
    }
}

@Composable
fun LyvoxMobileHeader(
    title: String,
    subtitle: String,
    showActions: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Accent, PremiumBlue)))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("Lyvox ") }
                    withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append("Vault") }
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            if (showActions) {
                IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = "Buscar") }
                IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções") }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PremiumChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB3181D2C))
            .border(1.dp, DarkBorder.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
