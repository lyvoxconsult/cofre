package com.lyvox.vault.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.crypto.CryptoManager
import com.lyvox.vault.data.model.VaultEntry
import com.lyvox.vault.ui.theme.*

// ─── Audit Models ───────────────────────────────────────────

enum class AuditIssueType {
    WEAK,       // Entropy score < threshold
    REUSED,     // Same password used for multiple services
    OLD,        // Not changed for > 90 days
    MISSING     // Empty password
}

data class AuditIssue(
    val entryId: String,
    val serviceName: String,
    val login: String,
    val issueType: AuditIssueType,
    val detail: String
)

// ─── Screen ─────────────────────────────────────────────────

/**
 * Security audit screen — surfaces password health issues.
 *
 * Checks:
 * - Missing passwords
 * - Weak passwords (too short, no variety)
 * - Reused passwords (same value across services)
 * - Old passwords (not changed in 90+ days)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(onBack: () -> Unit) {
    val app = LyvoxApp.instance
    val db = app.databaseHelper
    val session = app.sessionManager

    var issues by remember { mutableStateOf<List<AuditIssue>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var scannedCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val key = session.getKey()
        if (key == null) { isLoading = false; return@LaunchedEffect }

        val entries = db.listEntries()
        scannedCount = entries.size

        val found = mutableListOf<AuditIssue>()
        val passwordToEntries = mutableMapOf<String, MutableList<VaultEntry>>()

        for (entry in entries) {
            // Decrypt password
            val password = if (entry.encryptedPassword.isNotEmpty()) {
                try { CryptoManager.decryptField(key, entry.encryptedPassword, entry.passwordNonce) }
                catch (_: Exception) { null }
            } else null

            // Missing
            if (password.isNullOrEmpty()) {
                found.add(AuditIssue(entry.id, entry.serviceName, entry.login,
                    AuditIssueType.MISSING, "Nenhuma senha cadastrada"))
                continue
            }

            // Group by password for reuse check
            passwordToEntries.getOrPut(password) { mutableListOf() }.add(entry)

            // Weak password
            val strength = evaluateStrength(password)
            if (strength < 40) {
                val reason = when {
                    password.length < 8 -> "Senha muito curta (${password.length} caracteres)"
                    password.all { it.isDigit() } -> "Apenas números — muito previsível"
                    password.all { it.isLetter() } -> "Apenas letras — sem números ou símbolos"
                    !password.any { it.isUpperCase() } && !password.any { it.isDigit() } ->
                        "Sem maiúsculas ou números"
                    else -> "Senha fraca (pontuação: $strength/100)"
                }
                found.add(AuditIssue(entry.id, entry.serviceName, entry.login,
                    AuditIssueType.WEAK, reason))
            }

            // Old password (>90 days)
            try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                val updated = fmt.parse(entry.updatedAt)
                if (updated != null) {
                    val daysSince = ((System.currentTimeMillis() - updated.time) / (1000 * 60 * 60 * 24)).toInt()
                    if (daysSince >= 90) {
                        found.add(AuditIssue(entry.id, entry.serviceName, entry.login,
                            AuditIssueType.OLD, "Não atualizada há $daysSince dias"))
                    }
                }
            } catch (_: Exception) { }
        }

        // Reused passwords (after full scan)
        for ((_, group) in passwordToEntries) {
            if (group.size > 1) {
                val names = group.joinToString(", ") { it.serviceName }
                for (e in group) {
                    // Don't duplicate if already marked as weak or missing
                    if (found.none { it.entryId == e.id && it.issueType == AuditIssueType.REUSED }) {
                        found.add(AuditIssue(e.id, e.serviceName, e.login,
                            AuditIssueType.REUSED, "Reutilizada em: $names"))
                    }
                }
            }
        }

        // Sort: missing > reused > weak > old
        issues = found.sortedBy { it.issueType.ordinal }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auditoria de Segurança") },
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary card
                item {
                    AuditSummaryCard(
                        total = scannedCount,
                        issues = issues.size
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (issues.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = Success
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Nenhum problema encontrado!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Success
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Suas senhas estão em boa saúde.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // Group by issue type
                    val grouped = issues.groupBy { it.issueType }

                    for (type in AuditIssueType.values()) {
                        val group = grouped[type] ?: continue
                        item {
                            Text(
                                issueGroupTitle(type),
                                style = MaterialTheme.typography.titleSmall,
                                color = issueColor(type),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(group) { issue ->
                            AuditIssueCard(issue = issue)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSummaryCard(total: Int, issues: Int) {
    val healthy = total - issues
    val healthPct = if (total > 0) (healthy.toFloat() / total * 100).toInt() else 100
    val color = when {
        healthPct >= 90 -> Success
        healthPct >= 70 -> Warning
        else -> Danger
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Saúde do cofre",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$total senhas analisadas • $issues problemas encontrados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { healthPct / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "$healthPct% saudável",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "$healthPct%",
                style = MaterialTheme.typography.displaySmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AuditIssueCard(issue: AuditIssue) {
    val color = issueColor(issue.issueType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = issueIcon(issue.issueType),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = color
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    issue.serviceName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (issue.login.isNotBlank()) {
                    Text(
                        issue.login,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    issue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────

private fun evaluateStrength(password: String): Int {
    var score = 0

    // Length
    score += when {
        password.length >= 20 -> 30
        password.length >= 16 -> 25
        password.length >= 12 -> 20
        password.length >= 8  -> 10
        else -> 0
    }

    // Character variety
    if (password.any { it.isUpperCase() }) score += 15
    if (password.any { it.isLowerCase() }) score += 15
    if (password.any { it.isDigit() }) score += 15
    if (password.any { !it.isLetterOrDigit() }) score += 20

    // Entropy bonus
    val unique = password.toSet().size
    score += (unique * 5).coerceAtMost(20)

    return score.coerceIn(0, 100)
}

private fun issueGroupTitle(type: AuditIssueType) = when (type) {
    AuditIssueType.MISSING -> "⛔ Senha ausente"
    AuditIssueType.REUSED  -> "⚠️ Senhas reutilizadas"
    AuditIssueType.WEAK    -> "🔴 Senhas fracas"
    AuditIssueType.OLD     -> "🕐 Senhas antigas"
}

@Composable
private fun issueColor(type: AuditIssueType) = when (type) {
    AuditIssueType.MISSING -> Danger
    AuditIssueType.REUSED  -> Warning
    AuditIssueType.WEAK    -> Danger
    AuditIssueType.OLD     -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun issueIcon(type: AuditIssueType) = when (type) {
    AuditIssueType.MISSING -> Icons.Filled.Lock
    AuditIssueType.REUSED  -> Icons.Filled.ContentCopy
    AuditIssueType.WEAK    -> Icons.Filled.Warning
    AuditIssueType.OLD     -> Icons.Filled.Schedule
}
