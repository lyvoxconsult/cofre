package com.lyvox.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.data.model.VaultEntry
import com.lyvox.vault.data.model.PasswordGenConfig
import com.lyvox.vault.data.model.PasswordGenerator
import com.lyvox.vault.ui.components.PasswordField
import com.lyvox.vault.ui.theme.*

/**
 * Entry form screen — create or edit a vault entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    entryId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val isEditing = entryId != null

    var serviceName by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
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
                try {
                    password = if (raw.encryptedPassword.isNotEmpty())
                        CryptoManager.decryptField(key, raw.encryptedPassword, raw.passwordNonce)
                    else ""
                    notes = if (raw.encryptedNotes.isNotEmpty())
                        try { CryptoManager.decryptField(key, raw.encryptedNotes, raw.notesNonce ?: "") }
                        catch (_: Exception) { "" }
                    else ""
                } catch (_: Exception) { }
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Entrada" else "Nova Entrada") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            contentDescription = "Favoritar",
                            tint = if (isFavorite) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
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

                // Service name
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do serviço") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Login
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Login / E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.weight(1f),
                        label = "Senha"
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Generate password button
                    FilledTonalIconButton(
                        onClick = {
                            val cfg = app.settingsRepository.loadConfig()
                            val config = PasswordGenConfig(
                                length = cfg.passwordGenLength,
                                useUppercase = cfg.passwordGenUppercase,
                                useLowercase = cfg.passwordGenLowercase,
                                useNumbers = cfg.passwordGenNumbers,
                                useSpecial = cfg.passwordGenSpecial,
                                specialChars = cfg.passwordGenSpecialChars,
                                excludeAmbiguous = cfg.passwordGenExcludeAmbiguous
                            )
                            password = PasswordGenerator.generate(config).password
                        }
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Gerar senha")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "Selecione uma categoria",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    label = { Text("Notas (opcional)") },
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (serviceName.isBlank()) {
                            errorMessage = "Preencha o nome do serviço."
                            return@Button
                        }
                        if (login.isBlank()) {
                            errorMessage = "Preencha o login."
                            return@Button
                        }

                        isSaving = true
                        errorMessage = null
                        try {
                            val key = session.getKey()
                                ?: throw IllegalStateException("Sessão expirada.")

                            val (encPwd, pwdNonce) = if (password.isEmpty())
                                Pair("", "")
                            else
                                CryptoManager.encryptField(key, password)

                            val (encNotes, notesNonce) = if (notes.isEmpty())
                                Pair("", "")
                            else
                                CryptoManager.encryptField(key, notes)

                            val normalizedUrl = if (url.isNotBlank() && !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                                "https://$url"
                            } else {
                                url
                            }

                            if (isEditing && entryId != null) {
                                db.updateEntry(
                                    id = entryId,
                                    serviceName = serviceName,
                                    login = login,
                                    encryptedPassword = encPwd,
                                    passwordNonce = pwdNonce,
                                    encryptedNotes = encNotes,
                                    notesNonce = if (notesNonce.isEmpty()) null else notesNonce,
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
                                    notesNonce = if (notesNonce.isEmpty()) null else notesNonce,
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
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isSaving && serviceName.isNotBlank() && login.isNotBlank(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Salvar")
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
