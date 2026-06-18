package com.lyvox.vault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.data.model.Category
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurface
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import com.lyvox.vault.ui.theme.Danger
import com.lyvox.vault.ui.theme.PremiumBlue
import com.lyvox.vault.ui.theme.Success
import com.lyvox.vault.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val readOnlyMode = remember { app.settingsRepository.getReadOnlyMode() }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Category?>(null) }
    
    // Form States
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#6366f1") }
    var selectedIcon by remember { mutableStateOf("folder") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val colorsList = listOf(
        "#6366f1", // Indigo
        "#f59e0b", // Dourado / Âmbar
        "#10b981", // Verde
        "#ef4444", // Vermelho
        "#8b5cf6", // Roxo
        "#d97706", // Cobre
        "#ec4899", // Rosa
        "#6b7280"  // Cinza
    )

    val iconsList = listOf(
        "folder" to Icons.Filled.Folder,
        "user" to Icons.Filled.Person,
        "briefcase" to Icons.Filled.Work,
        "landmark" to Icons.Filled.AccountBalance,
        "key" to Icons.Filled.Key,
        "star" to Icons.Filled.Star,
        "heart" to Icons.Filled.Favorite,
        "tag" to Icons.Filled.Label
    )

    fun loadCategories() {
        categories = db.listCategories()
    }

    LaunchedEffect(Unit) {
        loadCategories()
    }

    fun handleSave() {
        if (name.isBlank()) {
            errorMessage = "O nome é obrigatório."
            return
        }
        if (categories.any { it.name.equals(name, ignoreCase = true) && it.id != editingCategory?.id }) {
            errorMessage = "Já existe uma categoria com este nome."
            return
        }

        try {
            if (editingCategory != null) {
                db.updateCategory(editingCategory!!.id, name, selectedColor, selectedIcon)
                Toast.makeText(context, "Categoria atualizada.", Toast.LENGTH_SHORT).show()
            } else {
                db.createCategory(name, selectedColor, selectedIcon)
                Toast.makeText(context, "Categoria criada.", Toast.LENGTH_SHORT).show()
            }
            showDialog = false
            editingCategory = null
            name = ""
            selectedColor = "#6366f1"
            selectedIcon = "folder"
            errorMessage = null
            loadCategories()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Erro ao salvar."
        }
    }

    fun handleDelete(cat: Category) {
        if (readOnlyMode) {
            Toast.makeText(context, "Modo somente leitura ativo.", Toast.LENGTH_SHORT).show()
            return
        }
        // Evita excluir categorias do sistema "Pessoal", "Trabalho", "Bancos", "Outros" se forem IDs fixos "1", "2", "3", "4"
        if (cat.id == "1" || cat.id == "2" || cat.id == "3" || cat.id == "4") {
            Toast.makeText(context, "Não é possível excluir categorias padrão do sistema.", Toast.LENGTH_SHORT).show()
            return
        }

        db.deleteCategory(cat.id)
        Toast.makeText(context, "Categoria excluída.", Toast.LENGTH_SHORT).show()
        showDeleteConfirmDialog = null
        loadCategories()
    }

    PremiumScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBack,
                    color = Color(0x661B2032),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(50.dp)
                        .border(1.dp, DarkBorder.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Categorias", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("Gerencie suas categorias personalizadas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Add Category Button
            if (!readOnlyMode) {
                Button(
                    onClick = {
                        editingCategory = null
                        name = ""
                        selectedColor = "#6366f1"
                        selectedIcon = "folder"
                        errorMessage = null
                        showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Accent, PremiumBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Nova Categoria", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Categories List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories, key = { it.id }) { cat ->
                    val colorObj = try { Color(android.graphics.Color.parseColor(cat.color)) } catch (_: Exception) { Accent }
                    val iconObj = iconsList.find { it.first == cat.icon }?.second ?: Icons.Filled.Folder
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xB3131828)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Categoria Icon / Cor
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorObj.copy(alpha = 0.18f))
                                    .border(1.dp, colorObj.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(iconObj, contentDescription = null, tint = colorObj, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            
                            // Detalhes
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                if (cat.id == "1" || cat.id == "2" || cat.id == "3" || cat.id == "4") {
                                    Text("Padrão do sistema", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Personalizada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Ações
                            if (!readOnlyMode && cat.id != "1" && cat.id != "2" && cat.id != "3" && cat.id != "4") {
                                IconButton(onClick = {
                                    editingCategory = cat
                                    name = cat.name
                                    selectedColor = cat.color
                                    selectedIcon = cat.icon
                                    errorMessage = null
                                    showDialog = true
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { showDeleteConfirmDialog = cat }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingCategory != null) "Editar Categoria" else "Nova Categoria", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF131828),
            textContentColor = Color.White,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = Danger, style = MaterialTheme.typography.bodyMedium)
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Nome da Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedLabelColor = Accent
                        )
                    )

                    // Color Selector
                    Text("Selecione a Cor", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(colorsList) { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = colorHex }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Icon Selector
                    Text("Selecione o Ícone", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(iconsList) { iconPair ->
                            val iconKey = iconPair.first
                            val iconVector = iconPair.second
                            val isSelected = selectedIcon == iconKey
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Accent.copy(alpha = 0.25f) else DarkSurfaceElevated)
                                    .border(1.dp, if (isSelected) Accent else DarkBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedIcon = iconKey },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconVector, 
                                    contentDescription = null, 
                                    tint = if (isSelected) Accent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { handleSave() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Excluir Categoria?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente excluir a categoria '${showDeleteConfirmDialog!!.name}'? Senhas associadas a ela ficarão sem categoria.", color = Color.White) },
            containerColor = Color(0xFF131828),
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { handleDelete(showDeleteConfirmDialog!!) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
