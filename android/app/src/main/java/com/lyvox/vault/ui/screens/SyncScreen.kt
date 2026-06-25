package com.lyvox.vault.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lyvox.vault.LyvoxApp
import com.lyvox.vault.network.SyncNetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = LyvoxApp.instance

    var syncStatus by remember { mutableStateOf("Aguardando sincronização...") }
    var isSyncing by remember { mutableStateOf(false) }

    // Configura o GMS Barcode Scanner (UI nativa do Google Play Services)
    // Usa a câmera através do sistema operacional — sem conflito com o pipeline MIUI
    val gmsOptions = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    val scanner = remember { GmsBarcodeScanning.getClient(context, gmsOptions) }

    fun handleQrCode(qrContent: String) {
        if (isSyncing) return

        try {
            val qrData = com.google.gson.Gson().fromJson(qrContent, Map::class.java)
            if (qrData["protocol"] == "lyvox-sync") {
                isSyncing = true
                syncStatus = "Conectando..."

                val endpoint = qrData["endpoint"] as? String ?: return
                val sessionId = qrData["session_id"] as? String ?: return
                val syncPassword = qrData["sync_password"] as? String ?: return

                coroutineScope.launch {
                    try {
                        syncStatus = "Preparando dados do Android..."
                        val sessionKey = app.sessionManager.getKey()
                        if (sessionKey == null) {
                            syncStatus = "Erro: Cofre bloqueado."
                            isSyncing = false
                            return@launch
                        }

                        val syncManager = com.lyvox.vault.service.SyncManager(context)
                        val androidSyncData = withContext(Dispatchers.IO) {
                            syncManager.exportSyncPackage(app.databaseHelper, sessionKey, syncPassword)
                        }

                        syncStatus = "Enviando dados para o PC..."
                        val response = SyncNetworkManager.sendSyncData(endpoint, sessionId, syncPassword, androidSyncData)

                        if (response != null && response.success) {
                            val desktopSyncData = response.sync_data
                            if (!desktopSyncData.isNullOrEmpty()) {
                                syncStatus = "Importando dados do PC..."
                                val msg = withContext(Dispatchers.IO) {
                                    syncManager.importSyncPackage(desktopSyncData, syncPassword, sessionKey, app.databaseHelper)
                                }
                                syncStatus = "Sincronização concluída!"
                                Toast.makeText(context, "Sincronização concluída: $msg", Toast.LENGTH_LONG).show()
                            } else {
                                syncStatus = "Sincronização parcial (Sem dados do PC)"
                                Toast.makeText(context, "Android exportado, mas o PC não enviou dados.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            syncStatus = "Erro na sincronização: ${response?.message ?: "Falha na rede"}"
                            Toast.makeText(context, "Falha ao comunicar com o PC", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        syncStatus = "Erro: ${e.message}"
                        Log.e("SyncScreen", "Erro no sync", e)
                    } finally {
                        isSyncing = false
                    }
                }
            } else {
                syncStatus = "QR Code inválido. Use o QR gerado pelo Lyvox Desktop."
                Toast.makeText(context, "QR Code não reconhecido como Lyvox Sync", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            syncStatus = "QR Code inválido. Use o QR gerado pelo Lyvox Desktop."
        }
    }

    fun startQrScan() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue != null) {
                    handleQrCode(rawValue)
                } else {
                    syncStatus = "QR Code não pôde ser lido. Tente novamente."
                }
            }
            .addOnCanceledListener {
                syncStatus = "Leitura cancelada."
            }
            .addOnFailureListener { e ->
                Log.e("SyncScreen", "Falha ao escanear QR", e)
                syncStatus = "Erro ao abrir câmera: ${e.message}"
                Toast.makeText(context, "Erro ao escanear: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sincronizar com Desktop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (isSyncing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aguarde...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = { startQrScan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR Code do Desktop")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Abra o Lyvox no PC, vá em Configurações → Sync e exiba o QR Code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
