package com.lyvox.vault.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@Composable
fun PermissionScreen(
    context: Context,
    onPermissionsGranted: () -> Unit
) {
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var showDeniedMessage by remember { mutableStateOf(false) }

    val hasAllPermissions = permissionsToRequest.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (hasAllPermissions) {
        LaunchedEffect(Unit) {
            onPermissionsGranted()
        }
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val granted = permissionsMap.values.all { it }
        if (granted) {
            onPermissionsGranted()
        } else {
            showDeniedMessage = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3A5F))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissões Necessárias",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "O Gravador de Voz (Cofre Seguro) precisa de permissão para acessar suas mídias. Isso é necessário para importar fotos e vídeos para o ambiente protegido.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (showDeniedMessage) {
            Text(
                text = "Permissão negada. Algumas funcionalidades podem estar indisponíveis. Você pode alterar isso nas configurações do aparelho.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = { onPermissionsGranted() }, // Avança mesmo negado, mas limitado
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Continuar sem permissões")
            }
        } else {
            Button(
                onClick = { permissionLauncher.launch(permissionsToRequest) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Conceder Permissões", color = Color(0xFF1E3A5F))
            }
        }
    }
}
