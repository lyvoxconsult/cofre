package com.lyvox.vault.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.data.model.VaultEntryDecrypted
import com.lyvox.vault.ui.components.CopyButton
import com.lyvox.vault.ui.theme.*

/**
 * Entry detail screen — view entry with copy and navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: Long,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val settings = app.settingsRepository

    var entry by remember { mutableStateOf<VaultEntryDecrypted?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        val key = session.getKey() ?: return@LaunchedEffect
        val raw = db.getEntry(entryId)
        if (raw != null) {
            try {
                val password = if (raw.encryptedPassword.isEmpty()) ""
                    else CryptoManager.decryptField(key, raw.encryptedPassword, raw.passwordNonce)
                val notes = if (raw.encryptedNotes.isEmpty()) ""
                    else try { CryptoManager.decryptField(key, raw.encryptedNotes, raw.notesNonce ?: "") }
                        catch (_: Exception) { "" }
                val categories = db.listCategories()
                val categoryName = raw.categoryId?.let { id ->
                    categories.find { it.id == id }?.name
                }
                entry = VaultEntryDecrypted(
                    id = raw.id, serviceName = raw.serviceName, login = raw.login,
                    password = password, notes = notes, url = raw.url,
                    categoryId = raw.categoryId, categoryName = categoryName,
                    createdAt = raw.createdAt, updatedAt = raw.updatedAt
                )
            } catch (e: Exception) { /* handled by null entry */ }
        }
        isLoading = false
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir entrada") },
            text = { Text("Tem certeza que deseja excluir esta entrada?") },
            confirmButton = {
                TextButton(onClick = {
                    db.deleteEntry(entryId)
                    showDeleteDialog = false
                    onDeleted()
                }) {
                    Text("Excluir", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.serviceName ?: "Detalhes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir",
                            tint = Danger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else if (entry == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Entrada não encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val e = entry!!
            val clipboardClearSecs = settings.getClipboardClearSeconds()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Service name
                Text(
                    text = e.serviceName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (e.categoryName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = e.categoryName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Login
                DetailRow(
                    label = "Login / E-mail",
                    value = e.login,
                    copyButton = { CopyButton(e.login, clipboardClearSeconds = clipboardClearSecs) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                DetailRow(
                    label = "Senha",
                    value = e.password,
                    copyButton = { CopyButton(e.password, clipboardClearSeconds = clipboardClearSecs) }
                )

                if (e.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // URL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "URL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                e.url,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                val urlStr = e.url.trim()
                                if (urlStr.isNotEmpty()) {
                                    try {
                                        val normalized = if (!urlStr.startsWith("http://", ignoreCase = true) && 
                                            !urlStr.startsWith("https://", ignoreCase = true)) {
                                            "https://$urlStr"
                                        } else {
                                            urlStr
                                        }
                                        val uri = Uri.parse(normalized)
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            addCategory(Intent.CATEGORY_BROWSABLE)
                                        }
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "URL inválida ou vazia.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Abrir link",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (e.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Anotações",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                e.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Timestamps
                Text(
                    "Criado em: ${e.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    "Atualizado em: ${e.updatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyButton: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            copyButton()
        }
    }
}
