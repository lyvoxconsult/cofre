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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.lyvox.vault.service.SyncManager
import com.lyvox.vault.ui.components.PasswordField
import com.lyvox.vault.ui.theme.*

/**
 * Backup and Sync Screen — export/import encrypted backups and sync packages.
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
    val readOnlyMode = remember { app.settingsRepository.getReadOnlyMode() }
    val backupManager = remember { BackupManager(context) }
    val syncManager = remember { SyncManager(context) }

    var activeTab by remember { mutableStateOf(0) } // 0: Backup, 1: Sync
    var backupPassword by remember { mutableStateOf("") }
    var syncPassword by remember { mutableStateOf("") }
    var replaceData by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Export and Share Backup
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

    // Export and Share Sync Package
    fun shareSyncPackage(password: String) {
        isLoading = true
        errorMessage = null
        successMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val json = syncManager.exportSyncPackage(db, key, password)
            
            // Save to cache dir
            val syncsDir = java.io.File(context.cacheDir, "syncs")
            if (!syncsDir.exists()) syncsDir.mkdirs()
            val syncFile = java.io.File(syncsDir, "lyvox-vault-sync.vaultsync")
            syncFile.writeText(json)
            
            // Get URI using FileProvider
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                syncFile
            )
            
            // Share intent
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartilhar Pacote de Sincronização"))
            successMessage = "Pacote de sincronização pronto para compartilhar!"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao preparar sincronização."
        } finally {
            isLoading = false
        }
    }

    // File open launcher for Backup
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (readOnlyMode) {
            errorMessage = "Modo somente leitura ativo."
            return@rememberLauncherForActivityResult
        }
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

    // File open launcher for Sync
    val openSyncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (readOnlyMode) {
            errorMessage = "Modo somente leitura ativo."
            return@rememberLauncherForActivityResult
        }
        isLoading = true
        errorMessage = null
        successMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")

            val result = syncManager.importSyncPackage(json, syncPassword, key, db)
            successMessage = result
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao realizar a sincronização."
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (activeTab == 0) "Backup" else "Sincronização") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0; errorMessage = null; successMessage = null },
                    text = { Text("Backup Completo") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1; errorMessage = null; successMessage = null },
                    text = { Text("Sincronização Local") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                if (activeTab == 0) {
                    // ABA DE BACKUP
                    Text(
                        "Seus dados completos serão salvos criptografados. Útil para guardar cópias de segurança fora do dispositivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PasswordField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it; errorMessage = null; successMessage = null },
                        label = "Senha de backup",
                        enabled = !isLoading,
                        imeAction = ImeAction.Done
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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

                    OutlinedButton(
                        onClick = {
                            if (backupPassword.isBlank()) {
                                errorMessage = "Digite a senha do backup."
                                return@OutlinedButton
                            }
                            openLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !readOnlyMode && !isLoading && backupPassword.isNotBlank(),
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Formato do backup", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• Criptografia AES-256-GCM\n" +
                                "• Derivação: Argon2id (64MB, 3 passes)\n" +
                                "• 100% compatível com a versão Desktop\n" +
                                "• Salva todas as senhas e notas ativas\n" +
                                "• Arquivo com extensão .vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                } else {
                    // ABA DE SINCRONIZAÇÃO
                    Text(
                        "Sincronize localmente seus dados de forma bidirecional. O processo faz o merge das alterações baseando-se na data de modificação de cada registro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PasswordField(
                        value = syncPassword,
                        onValueChange = { syncPassword = it; errorMessage = null; successMessage = null },
                        label = "Senha de sincronização",
                        enabled = !isLoading,
                        imeAction = ImeAction.Done
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (syncPassword.isBlank()) {
                                errorMessage = "Defina uma senha para a sincronização."
                                return@Button
                            }
                            shareSyncPackage(syncPassword)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading && syncPassword.isNotBlank(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportar e Compartilhar Pacote")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (syncPassword.isBlank()) {
                                errorMessage = "Digite a senha de sincronização."
                                return@OutlinedButton
                            }
                            openSyncLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !readOnlyMode && !isLoading && syncPassword.isNotBlank(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.SyncAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Selecionar e Sincronizar Pacote")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Como funciona o Sync?", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "• O merge é feito de forma segura em memória\n" +
                                "• Preserva a versão mais recente com base no updatedAt\n" +
                                "• Sincroniza exclusões (Soft Delete) de forma automática\n" +
                                "• 100% compatível com a versão Desktop\n" +
                                "• O arquivo de sync tem extensão .vaultsync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
