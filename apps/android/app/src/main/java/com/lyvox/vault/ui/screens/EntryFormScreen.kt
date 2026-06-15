package com.lyvox.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.data.model.PasswordGenConfig
import com.lyvox.vault.data.model.PasswordGenerator
import com.lyvox.vault.ui.components.PasswordField
import com.lyvox.vault.ui.components.PremiumButtonBrush
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import com.lyvox.vault.ui.theme.Danger
import com.lyvox.vault.ui.theme.Warning

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun EntryFormScreen(
    entryId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val isEditing = entryId != null
    val readOnlyMode = remember { app.settingsRepository.getReadOnlyMode() }

    var serviceName by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        categories = db.listCategories()
        if (isEditing && entryId != null) {
            val key = session.getKey() ?: return@LaunchedEffect
            val raw = db.getEntry(entryId)
            if (raw != null) {
                serviceName = raw.serviceName
                login = raw.login
                url = raw.url
                isFavorite = raw.isFavorite
                selectedCategoryId = raw.categoryId
                password = if (raw.encryptedPassword.isNotEmpty()) {
                    CryptoManager.decryptField(key, raw.encryptedPassword, raw.passwordNonce)
                } else {
                    ""
                }
                notes = if (raw.encryptedNotes.isNotEmpty()) {
                    try { CryptoManager.decryptField(key, raw.encryptedNotes, raw.notesNonce ?: "") } catch (_: Exception) { "" }
                } else {
                    ""
                }
            }
        }
        isLoading = false
    }

    fun saveEntry() {
        if (readOnlyMode) {
            errorMessage = "Modo somente leitura ativo."
            return
        }
        if (serviceName.isBlank()) {
            errorMessage = "Preencha o nome do serviço."
            return
        }
        if (login.isBlank()) {
            errorMessage = "Preencha o login."
            return
        }

        isSaving = true
        errorMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val (encPwd, pwdNonce) = if (password.isEmpty()) Pair("", "") else CryptoManager.encryptField(key, password)
            val (encNotes, notesNonce) = if (notes.isEmpty()) Pair("", "") else CryptoManager.encryptField(key, notes)
            val normalizedUrl = if (url.isNotBlank() && !url.startsWith("http://", true) && !url.startsWith("https://", true)) "https://$url" else url

            if (isEditing && entryId != null) {
                db.updateEntry(
                    id = entryId,
                    serviceName = serviceName,
                    login = login,
                    encryptedPassword = encPwd,
                    passwordNonce = pwdNonce,
                    encryptedNotes = encNotes,
                    notesNonce = notesNonce.ifEmpty { null },
                    url = normalizedUrl,
                    categoryId = selectedCategoryId,
                    isFavorite = isFavorite
                )
            } else {
                db.createEntry(
                    serviceName = serviceName,
                    login = login,
                    encryptedPassword = encPwd,
                    passwordNonce = pwdNonce,
                    encryptedNotes = encNotes,
                    notesNonce = notesNonce.ifEmpty { null },
                    url = normalizedUrl,
                    categoryId = selectedCategoryId,
                    isFavorite = isFavorite
                )
            }
            onSaved()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao salvar."
        } finally {
            isSaving = false
        }
    }

    PremiumScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xF2181D2C))
                    .border(1.dp, DarkBorder.copy(alpha = 0.92f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(54.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(DarkBorder)
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PremiumButtonBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isEditing) "Editar acesso" else "Novo acesso", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Dados salvos com criptografia local.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (!readOnlyMode) isFavorite = !isFavorite }, enabled = !readOnlyMode) {
                        Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline, contentDescription = "Favorito", tint = if (isFavorite) Warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(20.dp))
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                } else {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Danger, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    PremiumTextField(serviceName, { serviceName = it }, "Nome do serviço")
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = "Login / E-mail",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PasswordField(value = password, onValueChange = { password = it }, modifier = Modifier.weight(1f), label = "Senha")
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(
                            enabled = !readOnlyMode,
                            onClick = {
                                val cfg = app.settingsRepository.loadConfig()
                                password = PasswordGenerator.generate(
                                    PasswordGenConfig(
                                        length = cfg.passwordGenLength,
                                        useUppercase = cfg.passwordGenUppercase,
                                        useLowercase = cfg.passwordGenLowercase,
                                        useNumbers = cfg.passwordGenNumbers,
                                        useSpecial = cfg.passwordGenSpecial,
                                        specialChars = cfg.passwordGenSpecialChars,
                                        excludeAmbiguous = cfg.passwordGenExcludeAmbiguous
                                    )
                                ).password
                            }
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Gerar senha")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = "URL / link",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = categories.find { it.id == selectedCategoryId }?.name ?: "Sem categoria",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            label = { Text("Categoria") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            shape = RoundedCornerShape(16.dp),
                            colors = premiumFieldColors()
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            DropdownMenuItem(text = { Text("Sem categoria") }, onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            })
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                    selectedCategoryId = cat.id
                                    categoryExpanded = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PremiumTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Notas",
                        minHeight = 120
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Cancelar") }
                        Button(
                            onClick = { saveEntry() },
                            enabled = !readOnlyMode && !isSaving && serviceName.isNotBlank() && login.isNotBlank(),
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PremiumButtonBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White) else Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Salvar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minHeight: Int = 0
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier),
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        singleLine = minHeight == 0,
        shape = RoundedCornerShape(16.dp),
        colors = premiumFieldColors()
    )
}

@Composable
private fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = DarkBorder,
    focusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.74f),
    unfocusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.50f),
    focusedLabelColor = Accent
)
