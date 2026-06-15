package com.lyvox.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.crypto.KeyDerivation
import com.lyvox.vault.security.BiometricAuthManager
import com.lyvox.vault.ui.components.PasswordField
import com.lyvox.vault.ui.theme.*

/** Known plaintext used to verify the master password on unlock. */
private const val VERIFY_PLAINTEXT = "lyvox-vault-verify"

/**
 * Unlock screen — handles first-run create, subsequent unlock, and biometric auth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    onRecovery: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val settings = app.settingsRepository
    val session = app.sessionManager
    val isFirstRun = settings.isFirstRun()

    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showCreateForm by remember { mutableStateOf(isFirstRun) }

    // Biometric check
    val biometricManager = remember { BiometricAuthManager(context) }
    val biometricAvailable = remember { biometricManager.isBiometricAvailable() }
    val biometricEnabled = remember { biometricManager.isBiometricEnabled() }

    fun createVault() {
        errorMessage = null
        when {
            masterPassword.length < 8 -> errorMessage = "A senha deve ter no mínimo 8 caracteres."
            masterPassword != confirmPassword -> errorMessage = "As senhas não coincidem."
            else -> {
                isLoading = true
                try {
                    val salt = KeyDerivation.generateSalt()
                    val key = KeyDerivation.deriveKey(masterPassword, salt)
                    val saltHex = bytesToHex(salt)
                    val (verifyToken, verifyNonce) = CryptoManager.encryptField(key, VERIFY_PLAINTEXT)
                    val config = settings.loadConfig().copy(
                        salt = saltHex,
                        verifyToken = verifyToken,
                        verifyNonce = verifyNonce,
                        passwordGenLength = 24,
                        passwordGenUppercase = true,
                        passwordGenLowercase = true,
                        passwordGenNumbers = true,
                        passwordGenSpecial = true
                    )
                    settings.saveConfig(config)
                    session.unlock(key)
                    onUnlocked()
                } catch (e: Exception) {
                    errorMessage = "Erro ao criar cofre: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Auto-trigger biometric if available and enabled
    // Note: createBiometricPrompt already starts authentication internally
    LaunchedEffect(Unit) {
        if (biometricAvailable && biometricEnabled && !isFirstRun && !biometricManager.isBiometricSuspended() && context is FragmentActivity) {
            biometricManager.createBiometricPrompt(
                activity = context,
                title = "lyvox vault",
                subtitle = "Autentique-se para desbloquear o cofre",
                onSuccess = { key ->
                    biometricManager.incrementConsecutiveBiometricUsages()
                    session.unlock(key)
                    onUnlocked()
                },
                onError = { msg -> errorMessage = msg }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Logo / Icon
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "lyvox vault",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Gerenciador seguro de senhas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DangerMuted
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = Danger,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showCreateForm) {
            // ── Create Vault Form ─────────────────────────
            Text(
                text = "Crie sua senha mestra",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ela será usada para criptografar todos os seus dados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            PasswordField(
                value = masterPassword,
                onValueChange = { masterPassword = it; errorMessage = null },
                label = "Senha Mestra",
                enabled = !isLoading,
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(12.dp))

            PasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = "Confirme a Senha Mestra",
                enabled = !isLoading,
                imeAction = ImeAction.Done,
                onImeAction = { if (masterPassword.isNotEmpty() && confirmPassword.isNotEmpty()) createVault() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { createVault() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading && masterPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Criar Cofre", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            // ── Unlock Form ───────────────────────────────
            PasswordField(
                value = masterPassword,
                onValueChange = { masterPassword = it; errorMessage = null },
                label = "Senha Mestra",
                enabled = !isLoading,
                imeAction = ImeAction.Done,
                onImeAction = { /* handled by button */ }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (biometricAvailable && biometricEnabled && biometricManager.isBiometricSuspended()) {
                Text(
                    text = "Biometria suspensa por segurança. Digite a senha mestra para reativar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        errorMessage = null
                        val saltHex = settings.getSalt()
                        if (saltHex == null) {
                            errorMessage = "Cofre não encontrado."
                            return@Button
                        }
                        isLoading = true
                        try {
                            val salt = hexToBytes(saltHex)
                            val key = KeyDerivation.deriveKey(masterPassword, salt)

                            // Verify master password by decrypting the stored verification token
                            val verifyToken = settings.getVerifyToken()
                            val verifyNonce = settings.getVerifyNonce()
                            if (verifyToken != null && verifyNonce != null) {
                                val decrypted = CryptoManager.decryptField(key, verifyToken, verifyNonce)
                                if (decrypted != VERIFY_PLAINTEXT) {
                                    throw SecurityException("Senha incorreta.")
                                }
                            } else {
                                // Fallback for vaults created before verify token was implemented:
                                // try encrypting and decrypting empty string as a basic sanity check
                                val (testCt, testNonce) = CryptoManager.encryptField(key, "")
                                val testPlain = CryptoManager.decryptField(key, testCt, testNonce)
                                if (testPlain != "") {
                                    throw SecurityException("Senha incorreta.")
                                }
                            }

                            biometricManager.resetConsecutiveBiometricUsages()
                            session.unlock(key)
                            onUnlocked()
                        } catch (e: Exception) {
                            errorMessage = "Senha incorreta."
                            session.lock()
                        } finally {
                            isLoading = false
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading && masterPassword.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Desbloquear Cofre", style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (biometricAvailable && biometricEnabled && !biometricManager.isBiometricSuspended()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                biometricManager.createBiometricPrompt(
                                    activity = activity,
                                    title = "lyvox vault",
                                    subtitle = "Autentique-se para desbloquear o cofre",
                                    onSuccess = { key ->
                                        biometricManager.incrementConsecutiveBiometricUsages()
                                        session.unlock(key)
                                        onUnlocked()
                                    },
                                    onError = { msg -> errorMessage = msg }
                                )
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Desbloquear com digital",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recovery link
            TextButton(
                onClick = onRecovery,
                enabled = !isLoading
            ) {
                Text(
                    "Esqueci minha senha",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ─── Hex utils (same as BackupManager) ──────────────────────

private fun bytesToHex(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) sb.append(String.format("%02x", b))
    return sb.toString()
}

private fun hexToBytes(hex: String): ByteArray {
    val len = hex.length
    require(len % 2 == 0) { "Invalid hex" }
    return ByteArray(len / 2) {
        Integer.parseInt(hex.substring(it * 2, it * 2 + 2), 16).toByte()
    }
}
