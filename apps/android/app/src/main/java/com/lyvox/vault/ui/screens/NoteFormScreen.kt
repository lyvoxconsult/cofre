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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.ui.components.PremiumButtonBrush
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import com.lyvox.vault.ui.theme.Danger

@Composable
fun NoteFormScreen(
    noteId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val isEditing = noteId != null
    val readOnlyMode = remember { app.settingsRepository.getReadOnlyMode() }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (isEditing && noteId != null) {
            val key = session.getKey() ?: return@LaunchedEffect
            val raw = db.getNote(noteId)
            if (raw != null) {
                title = raw.title
                category = raw.category
                content = if (raw.encryptedContent.isNotEmpty()) {
                    try { CryptoManager.decryptField(key, raw.encryptedContent, raw.contentNonce) } catch (_: Exception) { "" }
                } else {
                    ""
                }
            }
        }
        isLoading = false
    }

    fun saveNote() {
        if (readOnlyMode) {
            errorMessage = "Modo somente leitura ativo."
            return
        }
        if (title.isBlank() && content.isBlank()) {
            errorMessage = "Preencha o título ou o conteúdo."
            return
        }
        isSaving = true
        errorMessage = null
        try {
            val key = session.getKey() ?: throw IllegalStateException("Sessão expirada.")
            val (encContent, contentNonce) = if (content.isEmpty()) Pair("", "") else CryptoManager.encryptField(key, content)
            if (isEditing && noteId != null) {
                db.updateNote(noteId, title, encContent, contentNonce, category)
            } else {
                db.createNote(title, encContent, contentNonce, category)
            }
            onSaved()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao salvar."
        } finally {
            isSaving = false
        }
    }

    if (showDeleteDialog && noteId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir nota") },
            text = { Text("Esta nota será removida do cofre.") },
            confirmButton = {
                TextButton(onClick = {
                    if (readOnlyMode) return@TextButton
                    db.deleteNote(noteId)
                    showDeleteDialog = false
                    onSaved()
                }) { Text("Excluir", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
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
                        Icon(Icons.Filled.Description, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isEditing) "Editar nota" else "Nova nota", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Crie uma nova nota protegida com criptografia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !readOnlyMode) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Danger)
                        }
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
                    PremiumNoteField(title, { title = it }, "Título")
                    Spacer(Modifier.height(12.dp))
                    PremiumNoteField(category, { category = it }, "Categoria ou tag")
                    Spacer(Modifier.height(12.dp))
                    PremiumNoteField(content, { content = it }, "Conteúdo da nota", minHeight = 150)
                    Text(
                        "${content.length}/5000",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Cancelar") }
                        Button(
                            onClick = { saveNote() },
                            enabled = !readOnlyMode && !isSaving,
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
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun PremiumNoteField(value: String, onValueChange: (String) -> Unit, label: String, minHeight: Int = 0) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier),
        label = { Text(label) },
        singleLine = minHeight == 0,
        maxLines = if (minHeight > 0) 12 else 1,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = DarkBorder,
            focusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.74f),
            unfocusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.50f),
            focusedLabelColor = Accent
        )
    )
}
