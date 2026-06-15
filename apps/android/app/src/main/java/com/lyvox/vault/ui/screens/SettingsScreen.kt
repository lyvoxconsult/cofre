package com.lyvox.vault.ui.screens

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.KeyDerivation
import com.lyvox.vault.crypto.RecoveryHasher
import com.lyvox.vault.data.model.AppConfig
import com.lyvox.vault.security.BiometricAuthManager
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurface
import com.lyvox.vault.ui.theme.Danger
import com.lyvox.vault.ui.theme.PremiumBlue
import com.lyvox.vault.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
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
    val biometricManager = remember { BiometricAuthManager(context) }
    val biometricAvailable = remember { biometricManager.isBiometricAvailable() }
    val recoveryStatus = remember { settings.getRecoveryStatus() }

    var config by remember { mutableStateOf(settings.loadConfig()) }
    var privacyMode by remember { mutableStateOf(settings.getPrivacyMode()) }
    var biometricEnabled by remember { mutableStateOf(biometricManager.isBiometricEnabled()) }
    var readOnlyMode by remember { mutableStateOf(settings.getReadOnlyMode()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showBiometricDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var masterPasswordConfirm by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var showReadOnlyUnlockDialog by remember { mutableStateOf(false) }

    fun verifyMasterPassword(password: String): Boolean {
        val activeKey = session.getKey()
        val saltHex = settings.getSalt()
        if (activeKey == null || saltHex == null) return false
        val salt = RecoveryHasher.hexToBytes(saltHex)
        val derivedKey = KeyDerivation.deriveKey(password, salt)
        return java.security.MessageDigest.isEqual(derivedKey, activeKey)
    }

    fun resetDialogState() {
        masterPassword = ""
        masterPasswordConfirm = ""
        dialogError = null
        isDeleting = false
    }

    if (showBiometricDialog) {
        PremiumConfirmDialog(
            title = "Remover biometria",
            message = "Digite a senha mestra para remover o desbloqueio por digital deste aparelho.",
            password = masterPassword,
            confirmPassword = null,
            error = dialogError,
            busy = false,
            actionText = "Remover",
            destructive = true,
            onPasswordChange = { masterPassword = it; dialogError = null },
            onConfirmPasswordChange = {},
            onDismiss = {
                showBiometricDialog = false
                resetDialogState()
            },
            onConfirm = {
                if (masterPassword.isBlank()) {
                    dialogError = "Digite a senha mestra."
                    return@PremiumConfirmDialog
                }
                try {
                    if (!verifyMasterPassword(masterPassword)) {
                        dialogError = "Senha mestra incorreta."
                        return@PremiumConfirmDialog
                    }
                    biometricManager.resetConsecutiveBiometricUsages()
                    biometricManager.disableBiometric()
                    biometricEnabled = false
                    showBiometricDialog = false
                    resetDialogState()
                    Toast.makeText(context, "Biometria removida.", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    dialogError = "Nao foi possivel validar a senha."
                }
            },
            biometricIcon = if (biometricAvailable && biometricEnabled) {
                {
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        biometricManager.createBiometricPrompt(
                            activity = activity,
                            title = "Remover biometria",
                            subtitle = "Confirme sua identidade",
                            onSuccess = { key ->
                                val activeKey = session.getKey()
                                if (activeKey != null && java.security.MessageDigest.isEqual(key, activeKey)) {
                                    biometricManager.resetConsecutiveBiometricUsages()
                                    biometricManager.disableBiometric()
                                    biometricEnabled = false
                                    showBiometricDialog = false
                                    resetDialogState()
                                    Toast.makeText(context, "Biometria removida.", Toast.LENGTH_SHORT).show()
                                } else {
                                    dialogError = "Chave biometrica invalida."
                                }
                            },
                            onError = { dialogError = it }
                        )
                    }
                }
            } else null
        )
    }

    if (showDeleteAllDialog) {
        PremiumConfirmDialog(
            title = "Excluir todos os dados",
            message = "Esta acao e irreversivel. Todas as senhas, notas, midias e configuracoes locais serao apagadas.",
            password = masterPassword,
            confirmPassword = masterPasswordConfirm,
            error = dialogError,
            busy = isDeleting,
            actionText = "Excluir",
            destructive = true,
            onPasswordChange = { masterPassword = it; dialogError = null },
            onConfirmPasswordChange = { masterPasswordConfirm = it; dialogError = null },
            onDismiss = {
                showDeleteAllDialog = false
                resetDialogState()
            },
            onConfirm = {
                if (readOnlyMode) {
                    dialogError = "Modo somente leitura ativo. Desative com a senha mestra antes de excluir dados."
                    return@PremiumConfirmDialog
                }
                if (masterPassword.isBlank() || masterPasswordConfirm.isBlank()) {
                    dialogError = "Digite e confirme a senha mestra."
                    return@PremiumConfirmDialog
                }
                if (masterPassword != masterPasswordConfirm) {
                    dialogError = "As senhas nao coincidem."
                    return@PremiumConfirmDialog
                }
                isDeleting = true
                try {
                    if (!verifyMasterPassword(masterPassword)) {
                        dialogError = "Senha mestra incorreta."
                        isDeleting = false
                        return@PremiumConfirmDialog
                    }
                    app.databaseHelper.clearAllData()
                    settings.saveConfig(AppConfig())
                    session.lock()
                    biometricManager.disableBiometric()
                    showDeleteAllDialog = false
                    resetDialogState()
                    Toast.makeText(context, "Dados excluidos.", Toast.LENGTH_LONG).show()
                    (context as? ComponentActivity)?.recreate()
                } catch (e: Exception) {
                    dialogError = "Erro ao excluir dados: ${e.message ?: "falha inesperada"}"
                    isDeleting = false
                }
            }
        )
    }

    if (showReadOnlyUnlockDialog) {
        PremiumConfirmDialog(
            title = "Desativar somente leitura",
            message = "Digite a senha mestra para permitir edicoes, exclusoes e importacoes novamente.",
            password = masterPassword,
            confirmPassword = null,
            error = dialogError,
            busy = false,
            actionText = "Desativar",
            destructive = false,
            onPasswordChange = { masterPassword = it; dialogError = null },
            onConfirmPasswordChange = {},
            onDismiss = {
                showReadOnlyUnlockDialog = false
                resetDialogState()
            },
            onConfirm = {
                if (masterPassword.isBlank()) {
                    dialogError = "Digite a senha mestra."
                    return@PremiumConfirmDialog
                }
                try {
                    if (!verifyMasterPassword(masterPassword)) {
                        dialogError = "Senha mestra incorreta."
                        return@PremiumConfirmDialog
                    }
                    readOnlyMode = false
                    settings.setReadOnlyMode(false)
                    showReadOnlyUnlockDialog = false
                    resetDialogState()
                    Toast.makeText(context, "Modo somente leitura desativado.", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    dialogError = "Nao foi possivel validar a senha."
                }
            }
        )
    }

    PremiumScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsMobileHeader(onBack = onBack)

            SettingsSection(title = "Seguranca", icon = Icons.Filled.Security) {
                PremiumOptionRow(
                    icon = Icons.Filled.LockClock,
                    title = "Bloqueio automatico",
                    subtitle = "${config.autoLockMinutes} min de inatividade"
                )
                SettingsTimeSelector(
                    selectedValue = config.autoLockMinutes,
                    options = listOf("1 min" to 1, "5 min" to 5, "15 min" to 15, "30 min" to 30, "Nunca" to 0),
                    onValueChange = {
                        config = config.copy(autoLockMinutes = it)
                        settings.setAutoLockMinutes(it)
                    }
                )
                PremiumOptionRow(
                    icon = Icons.Filled.Timer,
                    title = "Limpar clipboard",
                    subtitle = "${config.clipboardClearSeconds}s apos copiar"
                )
                SettingsTimeSelector(
                    selectedValue = config.clipboardClearSeconds,
                    options = listOf("15s" to 15, "30s" to 30, "60s" to 60, "120s" to 120, "Nunca" to 0),
                    onValueChange = {
                        config = config.copy(clipboardClearSeconds = it)
                        settings.setClipboardClearSeconds(it)
                    }
                )
                PremiumSwitchRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Modo privacidade",
                    subtitle = "Oculta logins e senhas nas listas",
                    checked = privacyMode,
                    onCheckedChange = {
                        privacyMode = it
                        settings.setPrivacyMode(it)
                    }
                )
                PremiumSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = "Modo somente leitura",
                    subtitle = "Bloqueia edicao, exclusao e importacao",
                    checked = readOnlyMode,
                    onCheckedChange = { enabled: Boolean ->
                        if (enabled) {
                            readOnlyMode = true
                            settings.setReadOnlyMode(true)
                            Toast.makeText(context, "Somente leitura ativado.", Toast.LENGTH_SHORT).show()
                        } else {
                            showReadOnlyUnlockDialog = true
                        }
                    }
                )
            }

            SettingsSection(title = "Aparencia", icon = Icons.Filled.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeChip("Claro", Icons.Filled.LightMode, config.theme == "light") {
                        val updated = config.copy(theme = "light")
                        config = updated
                        settings.setTheme("light")
                        app.themeConfig.value = updated
                    }
                    ThemeChip("Escuro", Icons.Filled.DarkMode, config.theme == "dark") {
                        val updated = config.copy(theme = "dark")
                        config = updated
                        settings.setTheme("dark")
                        app.themeConfig.value = updated
                    }
                    ThemeChip("Sistema", Icons.Filled.BrightnessAuto, config.theme == "system") {
                        val updated = config.copy(theme = "system")
                        config = updated
                        settings.setTheme("system")
                        app.themeConfig.value = updated
                    }
                }
            }

            SettingsSection(title = "Auditoria de Seguranca", icon = Icons.Filled.Shield) {
                PremiumNavRow(Icons.Filled.Shield, "Saude do cofre", "Senhas fracas, reutilizadas e antigas", onNavigateToAudit)
            }

            SettingsSection(title = "Importacao de Credenciais", icon = Icons.Filled.TableChart) {
                PremiumNavRow(Icons.Filled.TableChart, "Importar CSV", "Previa local e importacao criptografada", onNavigateToCsvImport)
            }

            SettingsSection(title = "Backup", icon = Icons.Filled.Backup) {
                PremiumNavRow(Icons.Filled.Backup, "Backup .vault", "Exportar, importar e validar integridade", onNavigateToBackup)
            }

            SettingsSection(title = "Sincronizacao Local", icon = Icons.Filled.Sync) {
                Text(
                    "Sincronize seu cofre entre este dispositivo movel e outro computador na sua rede local, com seguranca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SyncMethodCard(
                        icon = Icons.Filled.PhoneAndroid,
                        title = "Via QR Code\n(Rede Local)",
                        subtitle = "Escaneie o codigo QR do Desktop.",
                        onClick = onNavigateToSync,
                        modifier = Modifier.weight(1f)
                    )
                    SyncMethodCard(
                        icon = Icons.Filled.Usb,
                        title = "Via Cabo USB\n(ADB)",
                        subtitle = "Use cabo com modo desenvolvedor.",
                        onClick = onNavigateToSync,
                        modifier = Modifier.weight(1f)
                    )
                    SyncMethodCard(
                        icon = Icons.Filled.FolderOpen,
                        title = "Via Arquivo\n(.vaultsync)",
                        subtitle = "Importe ou exporte pacote local.",
                        onClick = onNavigateToBackup,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SettingsSection(title = "Recuperacao", icon = Icons.Filled.LockReset) {
                AlertNavRow(
                    icon = if (recoveryStatus.configured) Icons.Filled.LockReset else Icons.Filled.WarningAmber,
                    title = if (recoveryStatus.configured) "Recuperacao configurada" else "Recuperacao nao configurada",
                    subtitle = if (recoveryStatus.configured) "Reconfigure perguntas protegidas." else "Sem ela, nao sera possivel recuperar o acesso se voce esquecer a senha mestra.",
                    onClick = onNavigateToRecoverySetup,
                    color = if (recoveryStatus.configured) Success else Color(0xFFF59E0B)
                )
            }

            SettingsSection(title = "Excluir Dados", icon = Icons.Filled.DeleteForever) {
                AlertNavRow(
                    icon = Icons.Filled.DeleteForever,
                    title = "Remover permanentemente todas as entradas e notas do cofre.",
                    subtitle = "Esta acao nao pode ser desfeita.",
                    onClick = {
                        if (readOnlyMode) {
                            Toast.makeText(context, "Exclusao bloqueada pelo modo somente leitura.", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteAllDialog = true
                        }
                    },
                    color = Danger
                )
            }

            SettingsSection(title = "Biometria Android", icon = Icons.Filled.Fingerprint) {
                PremiumSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = "Desbloqueio por digital",
                    subtitle = if (biometricAvailable) "Fallback por senha mestra" else "Indisponivel neste dispositivo",
                    checked = biometricEnabled,
                    onCheckedChange = { enable: Boolean ->
                        if (!biometricAvailable) {
                            Toast.makeText(context, "Biometria indisponivel neste dispositivo.", Toast.LENGTH_SHORT).show()
                            return@PremiumSwitchRow
                        }
                        if (!enable) {
                            showBiometricDialog = true
                            return@PremiumSwitchRow
                        }
                        val activity = context as? FragmentActivity
                        val key = session.getKey()
                        if (activity == null || key == null) {
                            Toast.makeText(context, "Sessao invalida.", Toast.LENGTH_SHORT).show()
                            return@PremiumSwitchRow
                        }
                        biometricManager.enableBiometricPrompt(
                            activity = activity,
                            masterKey = key,
                            onSuccess = {
                                biometricEnabled = true
                                Toast.makeText(context, "Biometria ativada.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                biometricEnabled = false
                                Toast.makeText(context, "Erro na biometria: $error", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                )
                Text(
                    "No Desktop, o desbloqueio e feito apenas com a senha mestra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(title = "Sessao", icon = Icons.Filled.Lock) {
                PremiumNavRow(Icons.Filled.Lock, "Bloquear cofre", "Remove a chave ativa da sessao", {
                    session.lock()
                    Toast.makeText(context, "Cofre bloqueado.", Toast.LENGTH_SHORT).show()
                }, accentColor = Accent)
            }

            SettingsSection(title = "Sobre", icon = Icons.Filled.Security) {
                PremiumOptionRow(Icons.Filled.Security, "Lyvox Vault Next", "Local-first, sem banco de dados em nuvem")
                PremiumOptionRow(Icons.Filled.Lock, "Plataforma", "Android com SQLite local e AES-256-GCM")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsMobileHeader(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Accent, PremiumBlue)))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Lyvox ", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text("Vault", style = MaterialTheme.typography.titleLarge, color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                color = Color(0x661B2032),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .size(50.dp)
                    .border(1.dp, DarkBorder.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Configuracoes", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("Seguranca, backup e preferencias.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB3131828))
            .border(1.dp, DarkBorder.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(icon)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun SyncMethodCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(
            modifier = Modifier
                .height(146.dp)
                .background(Color(0x661B2032), RoundedCornerShape(16.dp))
                .border(1.dp, DarkBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(modifier = Modifier.size(36.dp), color = Accent.copy(alpha = 0.17f), shape = RoundedCornerShape(11.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    color: Color
) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.9f))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = color)
        }
    }
}

@Composable
private fun GradientIcon(icon: ImageVector, tint: Color = Color.White) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Accent, PremiumBlue))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PremiumOptionRow(icon: ImageVector, title: String, subtitle: String, accentColor: Color = Accent) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(34.dp),
            color = accentColor.copy(alpha = 0.18f),
            shape = RoundedCornerShape(11.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PremiumOptionRowInline(icon, title, subtitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Color(0xFF9CA3AF),
                uncheckedTrackColor = DarkSurface
            )
        )
    }
}

@Composable
private fun PremiumOptionRowInline(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(34.dp), color = Accent.copy(alpha = 0.16f), shape = RoundedCornerShape(11.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color = Accent
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Color(0x661B2032), RoundedCornerShape(16.dp))
                .border(1.dp, DarkBorder.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(38.dp), color = accentColor.copy(alpha = 0.17f), shape = RoundedCornerShape(12.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0x661B2032),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = Accent.copy(alpha = 0.24f),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DarkBorder.copy(alpha = 0.7f),
            selectedBorderColor = Accent.copy(alpha = 0.75f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimeSelector(
    selectedValue: Int,
    options: List<Pair<String, Int>>,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            val selected = selectedValue == value
            FilterChip(
                selected = selected,
                onClick = { onValueChange(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0x661B2032),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = Accent.copy(alpha = 0.24f),
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = DarkBorder.copy(alpha = 0.7f),
                    selectedBorderColor = Accent.copy(alpha = 0.75f)
                )
            )
        }
    }
}

@Composable
private fun PremiumConfirmDialog(
    title: String,
    message: String,
    password: String,
    confirmPassword: String?,
    error: String?,
    busy: Boolean,
    actionText: String,
    destructive: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    biometricIcon: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                if (error != null) Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Senha mestra") },
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        if (biometricIcon != null) {
                            IconButton(onClick = biometricIcon) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = "Confirmar com biometria", tint = Accent)
                            }
                        }
                    },
                    colors = dialogFieldColors()
                )
                if (confirmPassword != null) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        enabled = !busy,
                        singleLine = true,
                        label = { Text("Confirmar senha mestra") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = dialogFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) Danger else Accent,
                    contentColor = Color.White
                )
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = DarkBorder,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Accent,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)
