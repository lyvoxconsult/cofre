package com.lyvox.vault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
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
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = LyvoxApp.instance

    var syncStatus by remember { mutableStateOf("Aguardando sincronização...") }
    var isSyncing by remember { mutableStateOf(false) }
    var qrPayload by remember { mutableStateOf("") }

    fun handleQrCode(qrContent: String) {
        if (isSyncing) return
        val qrData = try {
            Gson().fromJson(qrContent, Map::class.java)
        } catch (_: Exception) {
            syncStatus = "QR Code inválido. Use o QR gerado pelo Lyvox Desktop."
            return
        }

        if (qrData["protocol"] != "lyvox-sync") {
            syncStatus = "QR Code inválido. Use o QR gerado pelo Lyvox Desktop."
            return
        }

        val endpoint = qrData["endpoint"] as? String
        val sessionId = qrData["session_id"] as? String
        val syncPassword = qrData["sync_password"] as? String
        if (endpoint.isNullOrBlank() || sessionId.isNullOrBlank() || syncPassword.isNullOrBlank()) {
            syncStatus = "QR Code incompleto."
            return
        }

        isSyncing = true
        syncStatus = "Conectando..."
        coroutineScope.launch {
            try {
                val sessionKey = app.sessionManager.getKey()
                if (sessionKey == null) {
                    syncStatus = "Cofre bloqueado."
                    return@launch
                }
                val syncManager = com.lyvox.vault.service.SyncManager(context)
                val androidSyncData = withContext(Dispatchers.IO) {
                    syncManager.exportSyncPackage(app.databaseHelper, sessionKey, syncPassword)
                }
                val response = SyncNetworkManager.sendSyncData(endpoint, sessionId, syncPassword, androidSyncData)
                if (response?.success == true && !response.sync_data.isNullOrEmpty()) {
                    val message = withContext(Dispatchers.IO) {
                        syncManager.importSyncPackage(response.sync_data, syncPassword, sessionKey, app.databaseHelper)
                    }
                    syncStatus = "Sincronização concluída."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } else {
                    syncStatus = response?.message ?: "Falha na rede local."
                }
            } catch (e: Exception) {
                syncStatus = "Erro na sincronização: ${e.message ?: "falha inesperada"}"
            } finally {
                isSyncing = false
            }
        }
    }

    fun startQrScan() {
        if (isSyncing) return
        syncStatus = "Abrindo leitor de QR..."
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val payload = barcode.rawValue.orEmpty()
                qrPayload = payload
                if (payload.isBlank()) {
                    syncStatus = "QR Code sem payload."
                } else {
                    handleQrCode(payload)
                }
            }
            .addOnCanceledListener {
                syncStatus = "Leitura de QR cancelada."
            }
            .addOnFailureListener { e ->
                syncStatus = "Falha ao abrir leitor de QR: ${e.message ?: "módulo indisponível"}"
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
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.width(80.dp).height(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(syncStatus, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))

            if (isSyncing) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { startQrScan() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR Code")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = qrPayload,
                    onValueChange = { qrPayload = it },
                    label = { Text("Payload do QR Code Lyvox") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { handleQrCode(qrPayload) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conectar e sincronizar")
                }
            }
        }
    }
}
