package com.lyvox.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.data.model.QuestionWithOptions
import com.lyvox.vault.data.model.RecoveryStatus
import com.lyvox.vault.service.RecoveryService
import com.lyvox.vault.ui.theme.*

/**
 * Recovery screen — answer security questions to regain access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    onRecovered: () -> Unit,
    onBack: () -> Unit
) {
    val app = LyvoxApp.instance
    val recoveryService = remember {
        RecoveryService(app.settingsRepository, app.databaseHelper)
    }
    val session = app.sessionManager

    var step by remember { mutableIntStateOf(0) } // 0 = status check, 1 = answers, 2 = reset password
    var questions by remember { mutableStateOf<List<QuestionWithOptions>>(emptyList()) }
    var selectedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var recoveryStatus by remember { mutableStateOf<RecoveryStatus?>(null) }

    // Reset password fields
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val status = recoveryService.getRecoveryStatus()
            recoveryStatus = status
            if (status.configured && !status.blocked) {
                val qs = recoveryService.getRecoveryQuestions()
                questions = qs
                step = 1
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao carregar recuperação."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar Acesso") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                0 -> {
                    // Loading / status check
                    if (recoveryStatus == null) {
                        CircularProgressIndicator()
                    } else if (!recoveryStatus!!.configured) {
                        Icon(
                            Icons.Filled.LockReset,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Recuperação não configurada.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else if (recoveryStatus!!.blocked) {
                        val remainingSecs = remember { mutableLongStateOf(recoveryStatus!!.blockedRemainingSecs ?: 0) }

                        // Live countdown timer — updates every second
                        LaunchedEffect(Unit) {
                            while (remainingSecs.longValue > 0) {
                                kotlinx.coroutines.delay(1000)
                                remainingSecs.longValue--
                            }
                            // Reload status when timer reaches zero
                            recoveryStatus = recoveryService.getRecoveryStatus()
                            step = 0
                        }

                        BlockedView(remainingSecs = remainingSecs.longValue)
                    }
                }

                1 -> {
                    // Answer questions
                    Text(
                        "Responda às perguntas de segurança",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Selecione uma resposta para cada pergunta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DangerMuted),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                errorMessage!!,
                                modifier = Modifier.padding(16.dp),
                                color = Danger,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    questions.forEachIndexed { idx, q ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Pergunta ${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    q.question,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                q.options.forEach { option ->
                                    val isSelected = selectedAnswers[q.index] == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedAnswers = selectedAnswers + (q.index to option)
                                        },
                                        label = { Text(option) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (selectedAnswers.size < 3) {
                                errorMessage = "Responda todas as 3 perguntas."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            try {
                                val answers = selectedAnswers.toSortedMap().values.toList()
                                val masterKey = recoveryService.verifyAnswers(answers)
                                session.unlock(masterKey)
                                step = 2 // go to password reset
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Erro na verificação."
                                // Reload status (might be blocked now)
                                recoveryStatus = recoveryService.getRecoveryStatus()
                                if (recoveryStatus?.blocked == true) {
                                    step = 0
                                }
                            } finally {
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading && selectedAnswers.size == 3,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verificar Respostas")
                        }
                    }
                }

                2 -> {
                    // Reset master password
                    Text(
                        "Redefinir Senha Mestra",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DangerMuted),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                errorMessage!!,
                                modifier = Modifier.padding(16.dp),
                                color = Danger,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nova Senha Mestra") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirme a Nova Senha") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            when {
                                newPassword.length < 8 -> errorMessage = "A senha deve ter no mínimo 8 caracteres."
                                newPassword != confirmPassword -> errorMessage = "As senhas não coincidem."
                                else -> {
                                    isLoading = true
                                    try {
                                        val oldKey = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
                                        val newKey = recoveryService.resetMasterPassword(
                                            newPassword = newPassword,
                                            newAnswers = null, // keep existing recovery answers
                                            oldSessionKey = oldKey
                                        )
                                        session.unlock(newKey)
                                        onRecovered()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Erro ao redefinir senha."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading && newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Redefinir Senha")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun formatBlockTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return when {
        minutes > 0 -> "${minutes}min ${secs}s"
        else -> "${secs}s"
    }
}

@Composable
private fun BlockedView(remainingSecs: Long) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.LockReset,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Warning
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Recuperação temporariamente bloqueada.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Aguarde ${formatBlockTime(remainingSecs)} antes de tentar novamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
