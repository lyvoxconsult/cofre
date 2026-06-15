package com.lyvox.vault.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.data.model.VaultEntry
import com.lyvox.vault.data.model.VaultEntryDecrypted
import com.lyvox.vault.ui.components.LyvoxMobileHeader
import com.lyvox.vault.ui.components.PremiumScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onEntryClick: (String) -> Unit,
    onAddEntry: () -> Unit
) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager

    var entries by remember { mutableStateOf<List<VaultEntryDecrypted>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val privacyMode by remember { mutableStateOf(app.settingsRepository.getPrivacyMode()) }
    val readOnlyMode by remember { mutableStateOf(app.settingsRepository.getReadOnlyMode()) }
    val visibleEntries = remember(entries, selectedCategoryId) {
        if (selectedCategoryId == null) entries else entries.filter { it.categoryId == selectedCategoryId }
    }

    LaunchedEffect(Unit) {
        val loadedCategories = db.listCategories()
        categories = loadedCategories
        reloadData(db, session, loadedCategories) { entries = it }
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            reloadData(db, session, categories) { entries = it }
        } else {
            val key = session.getKey() ?: return@LaunchedEffect
            val raw = db.searchEntries(searchQuery)
            entries = raw.mapNotNull { decryptEntry(it, key, categories) }
        }
    }

    PremiumScreen {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
            ) {
                LyvoxMobileHeader(
                    title = "Cofre",
                    subtitle = "Seus dados protegidos com criptografia de ponta a ponta",
                    modifier = Modifier.padding(top = 28.dp)
                )
                Spacer(Modifier.height(22.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar acessos...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = Color(0x99111624),
                        unfocusedContainerColor = Color(0x99111624)
                    )
                )
                Spacer(Modifier.height(16.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        VaultFilterChip(
                            label = "Todos",
                            selected = selectedCategoryId == null,
                            icon = Icons.Filled.Apps,
                            onClick = { selectedCategoryId = null }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        VaultFilterChip(
                            label = cat.name,
                            selected = selectedCategoryId == cat.id,
                            icon = categoryIcon(cat.name),
                            onClick = { selectedCategoryId = cat.id }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                when {
                    isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    visibleEntries.isEmpty() -> EmptyVault(searchQuery.isNotEmpty())
                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleEntries, key = { it.id }) { entry ->
                            EntryCard(
                                entry = entry,
                                onClick = { onEntryClick(entry.id) },
                                privacyMode = privacyMode
                            )
                        }
                    }
                }
            }

            if (!readOnlyMode) {
                FloatingActionButton(
                    onClick = onAddEntry,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(22.dp)
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar entrada")
                }
            }
        }
    }
}

@Composable
private fun VaultFilterChip(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun EmptyVault(hasSearch: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasSearch) "Nenhum resultado encontrado" else "Nenhuma entrada ainda",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Toque em + para adicionar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCard(
    entry: VaultEntryDecrypted,
    onClick: () -> Unit,
    privacyMode: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC141A29)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        serviceIcon(entry.serviceName),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = serviceTint(entry.serviceName)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (privacyMode) "••••••••" else entry.login,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.categoryName != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = entry.categoryName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Ações",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

private fun categoryIcon(name: String): ImageVector = when {
    name.contains("banco", ignoreCase = true) -> Icons.Filled.AccountBalance
    name.contains("trabalho", ignoreCase = true) -> Icons.Filled.Code
    else -> Icons.Filled.Key
}

private fun serviceIcon(serviceName: String): ImageVector = when {
    serviceName.contains("bank", true) || serviceName.contains("nubank", true) -> Icons.Filled.AccountBalance
    serviceName.contains("github", true) -> Icons.Filled.Code
    serviceName.contains("netflix", true) -> Icons.Filled.PlayArrow
    else -> Icons.Filled.Key
}

private fun serviceTint(serviceName: String): Color = when {
    serviceName.contains("netflix", true) -> Color(0xFFE50914)
    serviceName.contains("google", true) -> Color(0xFF4285F4)
    serviceName.contains("github", true) -> Color.Black
    serviceName.contains("nubank", true) -> Color(0xFF8A05BE)
    else -> Color(0xFF8B5CF6)
}

private fun decryptEntry(
    entry: VaultEntry,
    key: ByteArray,
    categories: List<Category>
): VaultEntryDecrypted? {
    return try {
        val password = if (entry.encryptedPassword.isEmpty()) ""
            else CryptoManager.decryptField(key, entry.encryptedPassword, entry.passwordNonce)
        val notes = if (entry.encryptedNotes.isEmpty()) ""
            else try {
                CryptoManager.decryptField(key, entry.encryptedNotes, entry.notesNonce ?: "")
            } catch (_: Exception) {
                ""
            }
        val categoryName = entry.categoryId?.let { id -> categories.find { it.id == id }?.name }

        VaultEntryDecrypted(
            id = entry.id,
            serviceName = entry.serviceName,
            login = entry.login,
            password = password,
            notes = notes,
            url = entry.url,
            categoryId = entry.categoryId,
            categoryName = categoryName,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt
        )
    } catch (_: Exception) {
        null
    }
}

private fun reloadData(
    db: com.lyvox.vault.data.database.DatabaseHelper,
    session: com.lyvox.vault.security.SessionManager,
    categories: List<Category>,
    onDecrypted: (List<VaultEntryDecrypted>) -> Unit
) {
    val key = session.getKey() ?: return
    val raw = db.listEntries()
    onDecrypted(raw.mapNotNull { decryptEntry(it, key, categories) })
}
