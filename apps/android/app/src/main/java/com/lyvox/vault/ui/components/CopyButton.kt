package com.lyvox.vault.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lyvox.vault.ui.theme.Success
import com.lyvox.vault.ui.theme.SuccessMuted

/**
 * Copy button that copies text to clipboard and shows a brief confirmation.
 *
 * @param textToCopy The text to copy when pressed
 * @param label Optional label shown next to the icon
 * @param clipboardClearSeconds Auto-clear clipboard after this many seconds (0 = no clear)
 */
@Composable
fun CopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    clipboardClearSeconds: Int = 0
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    FilledTonalButton(
        onClick = {
            copyToClipboard(context, textToCopy, "lyvox_vault")
            copied = true

            // Schedule clipboard clear if configured
            if (clipboardClearSeconds > 0) {
                Handler(Looper.getMainLooper()).postDelayed({
                    clearClipboard(context, textToCopy)
                }, clipboardClearSeconds * 1000L)
            }

            // Reset icon after 2 seconds
            coroutineScope.launch {
                delay(2000)
                copied = false
            }
        },
        modifier = modifier.height(36.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (copied) SuccessMuted else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (copied) Success else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = if (copied) "Copiado" else "Copiar",
            modifier = Modifier.size(16.dp)
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (copied) "Copiado!" else label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

private fun clearClipboard(context: Context, textToClear: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val currentText = clip.getItemAt(0).text?.toString()
                if (currentText == textToClear) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    } catch (_: SecurityException) {
        // Silent catch for background clipboard access issues on Android 12+
    } catch (_: Exception) {
        // Other potential errors
    }
}
