package com.lyvox.vault.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.service.BackupManager
import com.lyvox.vault.ui.components.PasswordField
import com.lyvox.vault.ui.theme.*

/**
 * Backup screen — export and import encrypted backups.
 *
 * Format: BackupEnvelope { app, version, created_at, encrypted_data, nonce, salt }
 * 100% compatible with the desktop lyvox vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val backupManager = remember { BackupManager(context) }

    var backupPassword by remember { mutableStateOf("") }
    var replaceData by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Export and Share
    fun shareBackup(password: String) {
        isLoading = true
        errorMessage = null
        successMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val json = backupManager.exportBackup(db, key, password)
            
            // Save to cache dir
            val backupsDir = java.io.File(context.cacheDir, "backups")
            if (!backupsDir.exists()) backupsDir.mkdirs()
            val backupFile = java.io.File(backupsDir, "lyvox-vault-backup.vault")
            backupFile.writeText(json)
            
            // Get URI using FileProvider
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )
            
            // Share intent
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartilhar Backup Lyvox Vault"))
            successMessage = "Backup pronto para compartilhar!"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao preparar backup para compartilhamento."
        } finally {
            isLoading = false
        }
    }

    // File open launcher
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        errorMessage = null
        successMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")

            val result = backupManager.importBackup(json, backupPassword, key, db, replaceData)
            successMessage = result
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao importar backup."
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Seus dados são criptografados com AES-256-GCM e protegidos por Argon2id.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DangerMuted),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(errorMessage!!, modifier = Modifier.padding(16.dp), color = Danger,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success
            if (successMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SuccessMuted),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Success)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(successMessage!!, color = Success, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Backup password
            PasswordField(
                value = backupPassword,
                onValueChange = { backupPassword = it; errorMessage = null; successMessage = null },
                label = "Senha de backup",
                enabled = !isLoading,
                imeAction = ImeAction.Done
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Replace/merge radio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = replaceData,
                    onClick = { replaceData = true },
                    label = { Text("Substituir dados") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !replaceData,
                    onClick = { replaceData = false },
                    label = { Text("Mesclar dados") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Export button
            Button(
                onClick = {
                    if (backupPassword.isBlank()) {
                        errorMessage = "Defina uma senha para o backup."
                        return@Button
                    }
                    shareBackup(backupPassword)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isLoading && backupPassword.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartilhar Backup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Import button
            OutlinedButton(
                onClick = {
                    if (backupPassword.isBlank()) {
                        errorMessage = "Digite a senha do backup."
                        return@OutlinedButton
                    }
                    openLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isLoading && backupPassword.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importar Backup")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Formato do backup", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Criptografia AES-256-GCM\n" +
                        "• Derivação de chave: Argon2id (64MB, 3 passes)\n" +
                        "• 100% compatível com o desktop lyvox vault\n" +
                        "• Contém: todas as entradas e notas seguras\n" +
                        "• Backup é um arquivo .vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
