package com.lyvox.vault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.security.BiometricAuthManager
import com.lyvox.vault.ui.theme.*


/**
 * Settings screen — theme, security, biometric, recovery, backup, about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToRecoverySetup: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToCsvImport: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val settings = app.settingsRepository
    val session = app.sessionManager

    var config by remember { mutableStateOf(settings.loadConfig()) }
    var showBiometricDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var privacyMode by remember { mutableStateOf(settings.getPrivacyMode()) }
    var confirmMasterPassword by remember { mutableStateOf("") }
    var confirmMasterPassword2 by remember { mutableStateOf("") }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var biometricPassword by remember { mutableStateOf("") }
    var biometricErrorMessage by remember { mutableStateOf<String?>(null) }

    val biometricManager = remember { BiometricAuthManager(context) }
    val biometricAvailable = remember { biometricManager.isBiometricAvailable() }
    var biometricEnabled by remember { mutableStateOf(biometricManager.isBiometricEnabled()) }
    val recoveryStatus = remember { settings.getRecoveryStatus() }

    // Biometric remove confirmation
    if (showBiometricDialog) {
        AlertDialog(
            onDismissRequest = {
                showBiometricDialog = false
                biometricPassword = ""
                biometricErrorMessage = null
            },
            title = { Text("Remover acesso biométrico") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tem certeza que deseja remover o acesso por digital deste app? Para confirmar, digite sua senha mestra.")
                    
                    if (biometricErrorMessage != null) {
                        Text(biometricErrorMessage!!, color = Danger, style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(
                        value = biometricPassword,
                        onValueChange = { biometricPassword = it; biometricErrorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha Mestra") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (biometricAvailable && biometricEnabled) {
                                IconButton(onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        biometricManager.createBiometricPrompt(
                                            activity = activity,
                                            title = "Remover digital",
                                            subtitle = "Autentique-se para confirmar a remoção da biometria",
                                            onSuccess = { key ->
                                                val activeKey = session.getKey()
                                                if (activeKey != null && java.security.MessageDigest.isEqual(key, activeKey)) {
                                                    biometricManager.resetConsecutiveBiometricUsages()
                                                    biometricManager.disableBiometric()
                                                    biometricEnabled = false
                                                    showBiometricDialog = false
                                                    biometricPassword = ""
                                                    biometricErrorMessage = null
                                                    Toast.makeText(context, "Acesso biométrico removido.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    biometricErrorMessage = "Chave biométrica inválida."
                                                }
                                            },
                                            onError = { msg -> biometricErrorMessage = msg }
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "Confirmar com digital",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (biometricPassword.isEmpty()) {
                        biometricErrorMessage = "Digite a senha."
                        return@TextButton
                    }
                    val activeKey = session.getKey()
                    val saltHex = settings.getSalt()
                    if (activeKey == null || saltHex == null) {
                        biometricErrorMessage = "Sessão inválida."
                        return@TextButton
                    }

                    try {
                        val salt = com.lyvox.vault.crypto.RecoveryHasher.hexToBytes(saltHex)
                        val derivedKey = com.lyvox.vault.crypto.KeyDerivation.deriveKey(biometricPassword, salt)
                        if (!java.security.MessageDigest.isEqual(derivedKey, activeKey)) {
                            biometricErrorMessage = "Senha mestra incorreta."
                            return@TextButton
                        }

                        biometricManager.resetConsecutiveBiometricUsages()
                        biometricManager.disableBiometric()
                        biometricEnabled = false
                        showBiometricDialog = false
                        biometricPassword = ""
                        biometricErrorMessage = null
                        Toast.makeText(context, "Acesso biométrico removido.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        biometricErrorMessage = "Erro ao validar senha."
                    }
                }) { Text("Remover", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricDialog = false
                    biometricPassword = ""
                    biometricErrorMessage = null
                }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteAllDialog = false
                confirmMasterPassword = ""
                confirmMasterPassword2 = ""
                deleteErrorMessage = null
            },
            title = { Text("Excluir TODOS os dados", color = Danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Esta ação é IRREVERSÍVEL. Todas as suas senhas, notas e configurações de recuperação serão apagadas permanentemente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (deleteErrorMessage != null) {
                        Text(
                            deleteErrorMessage!!,
                            color = Danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = confirmMasterPassword,
                        onValueChange = { confirmMasterPassword = it; deleteErrorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha Mestra") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isDeleting,
                        trailingIcon = {
                            if (biometricAvailable && biometricEnabled && !isDeleting) {
                                IconButton(onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        biometricManager.createBiometricPrompt(
                                            activity = activity,
                                            title = "Confirmar exclusão",
                                            subtitle = "Autentique-se para confirmar a exclusão de todos os dados",
                                            onSuccess = { key ->
                                                val activeKey = session.getKey()
                                                if (activeKey != null && java.security.MessageDigest.isEqual(key, activeKey)) {
                                                    isDeleting = true
                                                    try {
                                                        biometricManager.resetConsecutiveBiometricUsages()
                                                        app.databaseHelper.clearAllData()
                                                        settings.saveConfig(com.lyvox.vault.data.model.AppConfig())
                                                        session.lock()
                                                        biometricManager.disableBiometric()
                                                        showDeleteAllDialog = false
                                                        Toast.makeText(context, "Todos os dados foram excluídos permanentemente.", Toast.LENGTH_LONG).show()
                                                        (context as? androidx.activity.ComponentActivity)?.recreate()
                                                    } catch (e: Exception) {
                                                        deleteErrorMessage = "Erro ao excluir dados: ${e.message}"
                                                        isDeleting = false
                                                    }
                                                } else {
                                                    deleteErrorMessage = "Chave biométrica inválida."
                                                }
                                            },
                                            onError = { msg -> deleteErrorMessage = msg }
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "Confirmar com digital",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = confirmMasterPassword2,
                        onValueChange = { confirmMasterPassword2 = it; deleteErrorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirme a Senha Mestra") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isDeleting
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (confirmMasterPassword != confirmMasterPassword2) {
                            deleteErrorMessage = "As senhas não coincidem."
                            return@Button
                        }
                        if (confirmMasterPassword.isEmpty()) {
                            deleteErrorMessage = "Digite a senha."
                            return@Button
                        }

                        isDeleting = true
                        try {
                            val activeKey = session.getKey()
                            val saltHex = settings.getSalt()
                            if (activeKey == null || saltHex == null) {
                                deleteErrorMessage = "Sessão ou cofre inválido."
                                return@Button
                            }

                            val salt = com.lyvox.vault.crypto.RecoveryHasher.hexToBytes(saltHex)
                            val derivedKey = com.lyvox.vault.crypto.KeyDerivation.deriveKey(confirmMasterPassword, salt)

                            if (!java.security.MessageDigest.isEqual(derivedKey, activeKey)) {
                                deleteErrorMessage = "Senha mestra incorreta."
                                return@Button
                            }

                            // Limpa banco de dados, config, sessão e biometria
                            app.databaseHelper.clearAllData()
                            settings.saveConfig(com.lyvox.vault.data.model.AppConfig())
                            session.lock()
                            biometricManager.disableBiometric()

                            showDeleteAllDialog = false
                            Toast.makeText(context, "Todos os dados foram excluídos permanentemente.", Toast.LENGTH_LONG).show()
                            (context as? androidx.activity.ComponentActivity)?.recreate()
                        } catch (e: Exception) {
                            deleteErrorMessage = "Erro ao excluir dados: ${e.message}"
                        } finally {
                            isDeleting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = MaterialTheme.colorScheme.onError),
                    enabled = !isDeleting && confirmMasterPassword.isNotEmpty() && confirmMasterPassword2.isNotEmpty()
                ) {
                    Text("Excluir Permanentemente")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        confirmMasterPassword = ""
                        confirmMasterPassword2 = ""
                        deleteErrorMessage = null
                    },
                    enabled = !isDeleting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Appearance ────────────────────────────────────
        SectionHeader("Aparência")

        // Theme selector
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tema", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip(
                        label = "Claro",
                        icon = Icons.Filled.LightMode,
                        selected = config.theme == "light",
                        onClick = {
                            val newConfig = config.copy(theme = "light")
                            config = newConfig
                            settings.setTheme("light")
                            app.themeConfig.value = newConfig
                        }
                    )
                    ThemeChip(
                        label = "Escuro",
                        icon = Icons.Filled.DarkMode,
                        selected = config.theme == "dark",
                        onClick = {
                            val newConfig = config.copy(theme = "dark")
                            config = newConfig
                            settings.setTheme("dark")
                            app.themeConfig.value = newConfig
                        }
                    )
                    ThemeChip(
                        label = "Sistema",
                        icon = Icons.Filled.SettingsBrightness,
                        selected = config.theme == "system",
                        onClick = {
                            val newConfig = config.copy(theme = "system")
                            config = newConfig
                            settings.setTheme("system")
                            app.themeConfig.value = newConfig
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Security ──────────────────────────────────────
        SectionHeader("Segurança")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Auto-lock
                SettingsTimeSelector(
                    label = "Bloqueio automático",
                    description = "Bloqueia o cofre após o tempo selecionado sem atividade.",
                    selectedValue = config.autoLockMinutes,
                    options = listOf(
                        "1 min" to 1,
                        "5 min" to 5,
                        "10 min" to 10,
                        "15 min" to 15,
                        "30 min" to 30
                    ),
                    onValueChange = {
                        config = config.copy(autoLockMinutes = it)
                        settings.setAutoLockMinutes(it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Clipboard clear
                SettingsTimeSelector(
                    label = "Limpeza do clipboard",
                    description = "Remove automaticamente senhas copiadas após o tempo selecionado.",
                    selectedValue = config.clipboardClearSeconds,
                    options = listOf(
                        "10s" to 10,
                        "30s" to 30,
                        "1 min" to 60,
                        "2 min" to 120,
                        "5 min" to 300
                    ),
                    onValueChange = {
                        config = config.copy(clipboardClearSeconds = it)
                        settings.setClipboardClearSeconds(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Biometric ─────────────────────────────────────
        if (biometricAvailable) {
            SectionHeader("Biometria")

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Acesso por digital",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (biometricEnabled) "Ativado" else "Desativado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val activity = context as? FragmentActivity
                                val key = session.getKey()
                                if (activity != null && key != null) {
                                    biometricManager.enableBiometricPrompt(
                                        activity = activity,
                                        masterKey = key,
                                        onSuccess = {
                                            biometricEnabled = true
                                            Toast.makeText(context, "Biometria ativada.", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            biometricEnabled = false
                                            Toast.makeText(context, "Erro ao ativar biometria: $error", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    biometricEnabled = false
                                    Toast.makeText(context, "Erro ao ativar biometria: Contexto inválido", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                showBiometricDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Recovery ──────────────────────────────────────
        SectionHeader("Recuperação")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = onNavigateToRecoverySetup,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.LockReset,
                    contentDescription = null,
                    tint = if (recoveryStatus.configured) Success else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Perguntas de segurança",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (recoveryStatus.configured) "Configurado" else "Não configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Segurança Avançada ─────────────────────────────
        SectionHeader("Segurança Avançada")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Audit
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auditoria de senhas",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Identifica senhas fracas, reutilizadas e antigas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToAudit) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // CSV Import
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Importar CSV",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Chrome, Bitwarden, 1Password e outros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToCsvImport) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Privacy Mode
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Privacidade",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Oculta logins e senhas nas listas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = privacyMode,
                        onCheckedChange = {
                            privacyMode = it
                            settings.setPrivacyMode(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Backup ─────────────────────────────────────────
        SectionHeader("Backup")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = onNavigateToBackup,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Exportar / Importar", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Fazer backup criptografado dos dados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Sincronização ──────────────────────────────────
        SectionHeader("Sincronização")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = onNavigateToSync,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sincronizar com Desktop", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Conectar com Lyvox Vault no PC",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Lock ──────────────────────────────────────────
        SectionHeader("Sessão")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = {
                session.lock()
                Toast.makeText(context, "Cofre bloqueado.", Toast.LENGTH_SHORT).show()
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Danger)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Bloquear cofre", style = MaterialTheme.typography.titleMedium,
                    color = Danger, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Danger)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── About ─────────────────────────────────────────
        SectionHeader("Sobre")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("lyvox vault", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Versão 1.0.0", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Zona de Perigo ────────────────────────────────
        SectionHeader("Zona de Perigo")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = {
                showDeleteAllDialog = true
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Danger)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Excluir todos os dados", style = MaterialTheme.typography.titleMedium,
                        color = Danger)
                    Text("Apagar permanentemente todas as senhas, notas e configurações",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Danger)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        shape = RoundedCornerShape(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimeSelector(
    label: String,
    description: String,
    selectedValue: Int,
    options: List<Pair<String, Int>>,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (title, value) ->
                val isSelected = selectedValue == value
                FilterChip(
                    selected = isSelected,
                    onClick = { onValueChange(value) },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

