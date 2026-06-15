package com.lyvox.vault.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lyvox.vault.data.model.AttachmentDecrypted
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.data.model.VaultEntryDecrypted
import com.lyvox.vault.service.AttachmentManager
import com.lyvox.vault.ui.components.CopyButton
import com.lyvox.vault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry detail screen — view entry with copy and navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val settings = app.settingsRepository
    val readOnlyMode = remember { settings.getReadOnlyMode() }

    val attachmentManager = remember {
        AttachmentManager(context, db, session)
    }

    val coroutineScope = rememberCoroutineScope()

    var entry by remember { mutableStateOf<VaultEntryDecrypted?>(null) }
    var attachments by remember { mutableStateOf<List<AttachmentDecrypted>>(emptyList()) }
    var selectedAttachment by remember { mutableStateOf<AttachmentDecrypted?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUploading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun loadAttachments() {
        try {
            attachments = attachmentManager.listAttachments(entryId)
        } catch (_: Exception) {}
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (readOnlyMode) {
            Toast.makeText(context, "Modo somente leitura ativo.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        uri?.let {
            isUploading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val filename = getFileName(context, uri) ?: "anexo"
                        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        attachmentManager.createAttachment(entryId, filename, mimeType, bytes)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Anexo adicionado com sucesso!", Toast.LENGTH_SHORT).show()
                            loadAttachments()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Erro ao ler arquivo.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar anexo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isUploading = false
                }
            }
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedAttachment?.mimeType ?: "*/*")
    ) { uri: Uri? ->
        uri?.let {
            selectedAttachment?.let { att ->
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val decryptedBytes = attachmentManager.getAttachmentData(att.id)
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            outStream.write(decryptedBytes)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Arquivo salvo com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Erro ao salvar anexo: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

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
                loadAttachments()
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
                    if (readOnlyMode) return@TextButton
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
                    IconButton(onClick = onEdit, enabled = !readOnlyMode) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !readOnlyMode) {
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

                // Seção de Anexos
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Anexos e Mídia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !readOnlyMode && !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = "Adicionar Anexo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (attachments.isEmpty()) {
                    Text(
                        "Nenhum anexo encontrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    attachments.forEach { att ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Attachment,
                                    contentDescription = "Anexo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        att.filename,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.1f", att.fileSize / 1024f)} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedAttachment = att
                                        saveFileLauncher.launch(att.filename)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "Baixar",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        android.app.AlertDialog.Builder(context)
                                            .setTitle("Excluir anexo")
                                            .setMessage("Deseja realmente excluir este anexo?")
                                            .setPositiveButton("Excluir") { _, _ ->
                                                if (!readOnlyMode) {
                                                    attachmentManager.deleteAttachment(att.id)
                                                    loadAttachments()
                                                }
                                            }
                                            .setNegativeButton("Cancelar", null)
                                            .show()
                                    },
                                    enabled = !readOnlyMode
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Excluir",
                                        tint = Danger
                                    )
                                }
                            }
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

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
