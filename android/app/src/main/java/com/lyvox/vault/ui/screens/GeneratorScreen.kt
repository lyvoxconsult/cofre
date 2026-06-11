package com.lyvox.vault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.data.model.PasswordGenConfig
import com.lyvox.vault.data.model.PasswordGenResult
import com.lyvox.vault.data.model.PasswordGenerator
import com.lyvox.vault.ui.components.CopyButton
import com.lyvox.vault.ui.components.StrengthBar
import com.lyvox.vault.ui.theme.*

/**
 * Password generator screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen() {
    val app = LyvoxApp.instance
    val settings = app.settingsRepository

    val appConfig = remember { mutableStateOf(settings.loadConfig()) }

    var length by remember { mutableIntStateOf(appConfig.value.passwordGenLength) }
    var useUppercase by remember { mutableStateOf(appConfig.value.passwordGenUppercase) }
    var useLowercase by remember { mutableStateOf(appConfig.value.passwordGenLowercase) }
    var useNumbers by remember { mutableStateOf(appConfig.value.passwordGenNumbers) }
    var useSpecial by remember { mutableStateOf(appConfig.value.passwordGenSpecial) }
    var excludeAmbiguous by remember { mutableStateOf(appConfig.value.passwordGenExcludeAmbiguous) }
    var specialChars by remember { mutableStateOf(appConfig.value.passwordGenSpecialChars) }

    var generatedPassword by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PasswordGenResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun generate() {
        errorMessage = null
        try {
            val config = PasswordGenConfig(
                length = length,
                useUppercase = useUppercase,
                useLowercase = useLowercase,
                useNumbers = useNumbers,
                useSpecial = useSpecial,
                specialChars = specialChars,
                excludeAmbiguous = excludeAmbiguous
            )
            val genResult = PasswordGenerator.generate(config)
            generatedPassword = genResult.password
            result = genResult

            // Save settings
            appConfig.value = appConfig.value.copy(
                passwordGenLength = length,
                passwordGenUppercase = useUppercase,
                passwordGenLowercase = useLowercase,
                passwordGenNumbers = useNumbers,
                passwordGenSpecial = useSpecial,
                passwordGenSpecialChars = specialChars,
                passwordGenExcludeAmbiguous = excludeAmbiguous
            )
            settings.saveConfig(appConfig.value)
        } catch (e: IllegalArgumentException) {
            errorMessage = e.message
            result = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Cabeçalho Editorial
        Text(
            text = "Gerador de Senhas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Crie senhas fortes e seguras em poucos segundos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card da senha gerada (Sempre Visível)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (generatedPassword.isNotEmpty()) {
                        Text(
                            text = generatedPassword,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CopyButton(
                            textToCopy = generatedPassword,
                            clipboardClearSeconds = appConfig.value.clipboardClearSeconds
                        )
                    } else {
                        Text(
                            text = "Clique em gerar para criar uma senha",
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Força da senha
                if (generatedPassword.isNotEmpty() && result != null) {
                    StrengthBar(
                        entropyBits = result!!.entropyBits,
                        label = result!!.strengthLabel
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${result!!.entropyBits.toInt()} bits de entropia",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Estado inativo para a barra de força
                    StrengthBar(
                        entropyBits = 0.0,
                        label = "—"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exibição de mensagem de erro
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DangerMuted),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = Danger.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = Danger,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Card de Configurações
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Comprimento da senha
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comprimento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = length.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt() },
                    valueRange = 4f..128f,
                    steps = 123,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Opções de caracteres
                Text(
                    text = "Incluir caracteres",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Chips organizados em 2 colunas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CharTypeChip(
                        label = "Maiúsculas (A-Z)",
                        selected = useUppercase,
                        onSelectedChange = { useUppercase = it },
                        modifier = Modifier.weight(1f)
                    )
                    CharTypeChip(
                        label = "Minúsculas (a-z)",
                        selected = useLowercase,
                        onSelectedChange = { useLowercase = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CharTypeChip(
                        label = "Números (0-9)",
                        selected = useNumbers,
                        onSelectedChange = { useNumbers = it },
                        modifier = Modifier.weight(1f)
                    )
                    CharTypeChip(
                        label = "Símbolos (!@#$)",
                        selected = useSpecial,
                        onSelectedChange = { useSpecial = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                // Excluir caracteres ambíguos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Excluir ambíguos",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Evita caracteres semelhantes como l, 1, o, 0, O, I",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = excludeAmbiguous,
                        onCheckedChange = { excludeAmbiguous = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botão Gerar Senha
        Button(
            onClick = { generate() },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 6.dp
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Gerar senha segura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharTypeChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(44.dp)
    )
}
