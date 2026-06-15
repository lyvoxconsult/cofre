package com.lyvox.vault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.PasswordGenConfig
import com.lyvox.vault.data.model.PasswordGenResult
import com.lyvox.vault.data.model.PasswordGenerator
import com.lyvox.vault.ui.components.LyvoxMobileHeader
import com.lyvox.vault.ui.components.PremiumButtonBrush
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import com.lyvox.vault.ui.theme.Danger
import com.lyvox.vault.ui.theme.PremiumBlue
import com.lyvox.vault.ui.theme.Success

@Composable
fun GeneratorScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val app = LyvoxApp.instance
    val settings = app.settingsRepository
    val appConfig = remember { mutableStateOf(settings.loadConfig()) }
    val readOnlyMode = remember { settings.getReadOnlyMode() }

    var length by remember { mutableIntStateOf(appConfig.value.passwordGenLength) }
    var useUppercase by remember { mutableStateOf(appConfig.value.passwordGenUppercase) }
    var useLowercase by remember { mutableStateOf(appConfig.value.passwordGenLowercase) }
    var useNumbers by remember { mutableStateOf(appConfig.value.passwordGenNumbers) }
    var useSpecial by remember { mutableStateOf(appConfig.value.passwordGenSpecial) }
    var excludeAmbiguous by remember { mutableStateOf(appConfig.value.passwordGenExcludeAmbiguous) }
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
                specialChars = appConfig.value.passwordGenSpecialChars,
                excludeAmbiguous = excludeAmbiguous
            )
            val genResult = PasswordGenerator.generate(config)
            generatedPassword = genResult.password
            result = genResult
            appConfig.value = appConfig.value.copy(
                passwordGenLength = length,
                passwordGenUppercase = useUppercase,
                passwordGenLowercase = useLowercase,
                passwordGenNumbers = useNumbers,
                passwordGenSpecial = useSpecial,
                passwordGenExcludeAmbiguous = excludeAmbiguous
            )
            settings.saveConfig(appConfig.value)
        } catch (e: IllegalArgumentException) {
            errorMessage = e.message ?: "Ative pelo menos um tipo de caractere."
            result = null
        }
    }

    fun saveToVault() {
        if (readOnlyMode) {
            Toast.makeText(context, "Modo somente leitura ativo.", Toast.LENGTH_SHORT).show()
            return
        }
        val key = app.sessionManager.getKey()
        if (key == null) {
            Toast.makeText(context, "Sessão expirada.", Toast.LENGTH_SHORT).show()
            return
        }
        if (generatedPassword.isBlank()) {
            Toast.makeText(context, "Gere uma senha antes de salvar.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val (encryptedPassword, passwordNonce) = CryptoManager.encryptField(key, generatedPassword)
            app.databaseHelper.createEntry(
                serviceName = "Senha gerada",
                login = "gerador",
                encryptedPassword = encryptedPassword,
                passwordNonce = passwordNonce,
                encryptedNotes = "",
                notesNonce = null,
                url = "",
                categoryId = null,
                isFavorite = false
            )
            Toast.makeText(context, "Senha salva no cofre.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Erro ao salvar no cofre.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) { generate() }

    PremiumScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp)
        ) {
            LyvoxMobileHeader(
                title = "Gerador",
                subtitle = "Gere senhas fortes e seguras em segundos"
            )

            Spacer(Modifier.height(24.dp))

            PremiumPanel {
                Text(
                    "SUA SENHA GERADA",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0x332B1B71), Color(0x22111D2E))))
                        .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = generatedPassword.ifBlank { "Clique em gerar" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart).padding(end = 40.dp)
                    )
                    Icon(Icons.Filled.Security, contentDescription = null, tint = PremiumBlue, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(result?.strengthLabel ?: "Pronta para gerar", color = Accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val filledBars = ((result?.entropyBits ?: 0.0) / 24.0).toInt().coerceIn(1, 5)
                    repeat(5) { index: Int ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (index < filledBars) PremiumButtonBrush
                                    else Brush.horizontalGradient(listOf(DarkBorder.copy(alpha = 0.45f), DarkBorder.copy(alpha = 0.45f)))
                                )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage ?: "Excelente. Sua senha é resistente a ataques offline.",
                    color = if (errorMessage == null) MaterialTheme.colorScheme.onSurfaceVariant else Danger,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(18.dp))

            PremiumPanel {
                Text(
                    "PERSONALIZAR",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(18.dp))
                Text("Tamanho da senha: $length", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("4", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.toInt() },
                        valueRange = 4f..64f,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                    Text("64", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GeneratorOption("Letras maiúsculas (A-Z)", useUppercase, 6, { useUppercase = it })
                GeneratorOption("Letras minúsculas (a-z)", useLowercase, 6, { useLowercase = it })
                GeneratorOption("Números (0-9)", useNumbers, 4, { useNumbers = it })
                GeneratorOption("Símbolos (!@#\$%)", useSpecial, 4, { useSpecial = it })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Evitar caracteres semelhantes",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = excludeAmbiguous,
                        onCheckedChange = { excludeAmbiguous = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PremiumButtonBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Gerar nova senha", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (generatedPassword.isNotBlank()) {
                            clipboard.setText(AnnotatedString(generatedPassword))
                            Toast.makeText(context, "Senha copiada.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copiar")
                }
                OutlinedButton(
                    onClick = { saveToVault() },
                    modifier = Modifier.weight(1f).height(58.dp),
                    enabled = !readOnlyMode,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvar")
                }
            }
        }
    }
}

@Composable
private fun PremiumPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB3181D2C))
            .border(1.dp, DarkBorder.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun GeneratorOption(
    label: String,
    checked: Boolean,
    count: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(1.dp, DarkBorder.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = DarkBorder)
        )
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(onClick = {}) { Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(count.toString(), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White) }
    }
}
