package com.lyvox.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.SecureNoteDecrypted
import com.lyvox.vault.ui.components.LyvoxMobileHeader
import com.lyvox.vault.ui.components.PremiumButtonBrush
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.AccentMuted
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import com.lyvox.vault.ui.theme.PremiumBlue

@Composable
fun NotesScreen(
    onNoteClick: (String) -> Unit,
    onAddNote: () -> Unit
) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager
    val readOnlyMode by remember { mutableStateOf(app.settingsRepository.getReadOnlyMode()) }

    var notes by remember { mutableStateOf<List<SecureNoteDecrypted>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todas as notas") }
    var isLoading by remember { mutableStateOf(true) }

    fun loadNotes() {
        val key = session.getKey() ?: return
        val raw = if (searchQuery.isBlank()) db.listNotes() else db.searchNotes(searchQuery)
        notes = raw.mapNotNull { note ->
            try {
                val content = if (note.encryptedContent.isEmpty()) {
                    ""
                } else {
                    CryptoManager.decryptField(key, note.encryptedContent, note.contentNonce)
                }
                SecureNoteDecrypted(
                    id = note.id,
                    title = note.title,
                    content = content,
                    category = note.category,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt
                )
            } catch (_: Exception) {
                null
            }
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { loadNotes() }
    LaunchedEffect(searchQuery) { loadNotes() }

    val categories = remember(notes) {
        listOf("Todas as notas") + notes.map { it.category.ifBlank { "Sem categoria" } }.distinct()
    }
    val visibleNotes = notes.filter {
        selectedCategory == "Todas as notas" || it.category.ifBlank { "Sem categoria" } == selectedCategory
    }

    PremiumScreen {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    LyvoxMobileHeader(
                        title = "Notas",
                        subtitle = "Suas notas privadas protegidas com criptografia de ponta a ponta"
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar notas...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.72f),
                            unfocusedContainerColor = DarkSurfaceElevated.copy(alpha = 0.54f)
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        categories.forEach { category ->
                            PremiumFilterChip(
                                text = category,
                                selected = selectedCategory == category,
                                icon = if (category == "Todas as notas") Icons.Filled.Description else Icons.Filled.Tag,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                when {
                    isLoading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Accent) }
                    }

                    visibleNotes.isEmpty() -> item {
                        PremiumEmptyState(
                            icon = Icons.Filled.Description,
                            title = if (searchQuery.isBlank()) "Nenhuma nota ainda" else "Nenhum resultado",
                            body = "Crie uma nota protegida para guardar textos, ideias e documentos sensíveis."
                        )
                    }

                    else -> items(visibleNotes, key = { it.id }) { note ->
                        PremiumNoteCard(note = note, onClick = { onNoteClick(note.id) })
                    }
                }
            }

            if (!readOnlyMode) {
                FloatingActionButton(
                    onClick = onAddNote,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .navigationBarsPadding(),
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(PremiumButtonBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Nova nota", modifier = Modifier.size(34.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumFilterChip(
    text: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentMuted,
            selectedLabelColor = Color.White,
            containerColor = DarkSurfaceElevated.copy(alpha = 0.42f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Accent else DarkBorder,
            selectedBorderColor = Accent
        ),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun PremiumNoteCard(note: SecureNoteDecrypted, onClick: () -> Unit) {
    val icon = when {
        note.category.contains("ide", ignoreCase = true) -> Icons.Filled.Lightbulb
        note.category.contains("seg", ignoreCase = true) -> Icons.Filled.Security
        else -> Icons.Filled.Description
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB3181D2C))
            .border(1.dp, DarkBorder.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33111A2D))
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { "Sem título" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = note.content.replace("\n", " ").ifBlank { "Nota vazia" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (note.category.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0x332D7DFF)) {
                    Text(
                        text = note.category,
                        color = PremiumBlue,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Ações", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PremiumEmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x99181D2C))
            .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
