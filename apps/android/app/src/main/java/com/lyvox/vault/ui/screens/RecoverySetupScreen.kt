package com.lyvox.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.service.RecoveryService
import com.lyvox.vault.ui.theme.*

/**
 * Recovery setup screen — configure 3 security questions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverySetupScreen(
    onConfigured: () -> Unit
) {
    val app = LyvoxApp.instance
    val recoveryService = remember {
        RecoveryService(app.settingsRepository, app.databaseHelper)
    }
    val session = app.sessionManager
    val allQuestions = remember { recoveryService.getQuestions() }

    // Select 3 questions from the pool
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var answers by remember { mutableStateOf<MutableMap<Int, String>>(mutableMapOf()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar Recuperação") },
                navigationIcon = {
                    IconButton(onClick = onConfigured) {
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
            Spacer(modifier = Modifier.height(16.dp))

            if (isSuccess) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Success
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Recuperação configurada com sucesso!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Guarde bem suas respostas. Elas serão a única forma de recuperar o acesso caso esqueça a senha mestra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onConfigured,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Concluir")
                }
            } else {
                Text(
                    "Escolha 3 perguntas de segurança",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Suas respostas serão usadas para recuperar o acesso ao cofre.",
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

                // Question selection
                allQuestions.forEachIndexed { idx, question ->
                    val isSelected = idx in selectedIndices
                    val isMaxReached = selectedIndices.size >= 3 && !isSelected

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (isSelected) {
                                selectedIndices = selectedIndices - idx
                            } else if (!isMaxReached) {
                                selectedIndices = selectedIndices + idx
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pergunta #${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) {
                                            selectedIndices = selectedIndices - idx
                                        } else if (!isMaxReached) {
                                            selectedIndices = selectedIndices + idx
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Text(
                                question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = answers[idx] ?: "",
                                    onValueChange = { ans ->
                                        answers = answers.toMutableMap().apply { put(idx, ans) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Sua resposta") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        if (selectedIndices.size != 3) {
                            errorMessage = "Selecione exatamente 3 perguntas."
                            return@Button
                        }
                        val filledAnswers = selectedIndices.map { idx ->
                            val answer = answers[idx]
                            if (answer.isNullOrBlank()) {
                                errorMessage = "Preencha todas as respostas."
                                return@Button
                            }
                            idx to answer
                        }
                        if (errorMessage != null) return@Button

                        isLoading = true
                        try {
                            val key = session.getKey()
                                ?: throw IllegalStateException("Cofre bloqueado.")
                            recoveryService.setupRecovery(filledAnswers, key)
                            isSuccess = true
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Erro ao configurar recuperação."
                        } finally {
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isLoading && selectedIndices.size == 3,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salvar Configuração")
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
