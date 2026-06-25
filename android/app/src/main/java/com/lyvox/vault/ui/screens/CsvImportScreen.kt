package com.lyvox.vault.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.service.CsvImporter
import com.lyvox.vault.ui.theme.*

/**
 * CSV Import screen.
 *
 * Steps:
 * 1. User picks a .csv file
 * 2. Preview parsed entries
 * 3. Confirm import
 * 4. Feedback on result
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = LyvoxApp.instance
    val session = app.sessionManager
    val db = app.databaseHelper

    val importer = remember { CsvImporter(context) }

    var step by remember { mutableStateOf(0) }  // 0=pick, 1=preview, 2=done
    var parsedEntries by remember { mutableStateOf<List<CsvImporter.CsvEntry>>(emptyList()) }
    var importResult by remember { mutableStateOf<CsvImporter.ImportResult?>(null) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf("") }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Get display name
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) selectedFileName = it.getString(nameIdx)
                    }
                }
                val entries = importer.parseCsv(uri)
                if (entries.isEmpty()) {
                    Toast.makeText(context, "Nenhuma entrada encontrada no arquivo.", Toast.LENGTH_SHORT).show()
                } else {
                    parsedEntries = entries
                    step = 1
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao ler o arquivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar CSV") },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                // ── Step 0: Pick file ─────────────────────────────
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Info card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.TableChart, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Formatos suportados",
                                        style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                FormatRow("Google Chrome / Edge", "name, url, username, password")
                                Spacer(modifier = Modifier.height(6.dp))
                                FormatRow("Bitwarden", "name, login_uri, login_username, login_password")
                                Spacer(modifier = Modifier.height(6.dp))
                                FormatRow("1Password", "title, url, username, password")
                                Spacer(modifier = Modifier.height(6.dp))
                                FormatRow("Genérico", "Detecção automática de colunas")
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningMuted)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, tint = Warning)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "O arquivo é processado localmente. Nenhum dado é enviado para a nuvem.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Warning
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { fileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Selecionar arquivo CSV")
                        }
                    }
                }

                // ── Step 1: Preview ───────────────────────────────
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "$selectedFileName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${parsedEntries.size} entradas encontradas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = skipDuplicates,
                                    onCheckedChange = { skipDuplicates = it }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ignorar entradas duplicadas",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider()

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(parsedEntries) { entry ->
                                PreviewEntryCard(entry)
                            }
                        }

                        // Bottom actions
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { step = 0; parsedEntries = emptyList() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isImporting
                            ) {
                                Text("Cancelar")
                            }
                            Button(
                                onClick = {
                                    val key = session.getKey() ?: run {
                                        Toast.makeText(context, "Sessão expirada.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isImporting = true
                                    try {
                                        val result = importer.importEntries(parsedEntries, key, db, skipDuplicates)
                                        importResult = result
                                        step = 2
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro na importação: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isImporting = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isImporting
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text("Importar")
                                }
                            }
                        }
                    }
                }

                // ── Step 2: Result ────────────────────────────────
                2 -> {
                    val result = importResult
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Icon(
                            if ((result?.imported ?: 0) > 0) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = if ((result?.imported ?: 0) > 0) Success else Warning
                        )

                        Text(
                            "Importação concluída",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (result != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ResultRow("✅ Importadas", "${result.imported}")
                                    if (result.skipped > 0)
                                        ResultRow("⏭️ Ignoradas (duplicadas)", "${result.skipped}")
                                    if (result.errors.isNotEmpty())
                                        ResultRow("⚠️ Erros", "${result.errors.size}")
                                }
                            }

                            if (result.errors.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DangerMuted)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Detalhes dos erros:", style = MaterialTheme.typography.labelMedium,
                                            color = Danger)
                                        result.errors.take(5).forEach { err ->
                                            Text("• $err", style = MaterialTheme.typography.bodySmall,
                                                color = Danger, modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Concluir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(format: String, columns: String) {
    Row {
        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column {
            Text(format, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(columns, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewEntryCard(entry: CsvImporter.CsvEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        entry.serviceName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.serviceName.ifBlank { entry.url.ifBlank { "—" } },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.login.isNotBlank()) {
                    Text(
                        entry.login,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.password.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessMuted
                ) {
                    Text("✓", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = Success)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
