package com.lyvox.vault.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lyvox.vault.data.model.MediaItemDecrypted
import com.lyvox.vault.service.MediaVaultManager
import com.lyvox.vault.ui.components.LyvoxMobileHeader
import com.lyvox.vault.ui.components.PremiumButtonBrush
import com.lyvox.vault.ui.components.PremiumScreen
import com.lyvox.vault.ui.theme.Accent
import com.lyvox.vault.ui.theme.AccentMuted
import com.lyvox.vault.ui.theme.DarkBorder
import com.lyvox.vault.ui.theme.DarkSurfaceElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun MediaVaultScreen(mediaVaultManager: MediaVaultManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val readOnlyMode = remember { com.lyvox.vault.LyvoxApp.instance.settingsRepository.getReadOnlyMode() }
    var mediaItems by remember { mutableStateOf<List<MediaItemDecrypted>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("Tudo") }
    var selectedMedia by remember { mutableStateOf<MediaItemDecrypted?>(null) }
    var selectedMediaBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun loadMedia() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val items = mediaVaultManager.listMedia()
                withContext(Dispatchers.Main) {
                    mediaItems = items
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showToast(e.message ?: "Erro ao carregar mídia.")
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) {
            showToast("Importação cancelada.")
            return@rememberLauncherForActivityResult
        }
        if (readOnlyMode) {
            showToast("Modo somente leitura ativo.")
            return@rememberLauncherForActivityResult
        }
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Não foi possível ler o arquivo.")
                val thumbBytes = if (mimeType.startsWith("image/")) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    val scale = maxOf(1, minOf(opts.outWidth / 360, opts.outHeight / 360))
                    val opts2 = BitmapFactory.Options().apply { inSampleSize = scale }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)?.let { bmp ->
                        ByteArrayOutputStream().use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
                            out.toByteArray()
                        }
                    }
                } else {
                    null
                }
                mediaVaultManager.createMedia("media_${System.currentTimeMillis()}", mimeType, bytes, thumbBytes)
                withContext(Dispatchers.Main) {
                    showToast("Mídia importada e criptografada.")
                    loadMedia()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    showToast(e.message ?: "Erro ao importar mídia.")
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadMedia() }

    if (selectedMedia != null) {
        MediaViewer(
            media = selectedMedia!!,
            bytes = selectedMediaBytes,
            onClose = {
                selectedMedia = null
                selectedMediaBytes = null
            },
            onExport = {
                val media = selectedMedia ?: return@MediaViewer
                val bytes = selectedMediaBytes ?: return@MediaViewer
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, media.filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    if (media.mimeType.startsWith("video/")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                                )
                            }
                        }
                        val collection = if (media.mimeType.startsWith("video/")) {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                        context.contentResolver.insert(collection, values)?.let { outUri ->
                            context.contentResolver.openOutputStream(outUri)?.use { it.write(bytes) }
                        } ?: throw IllegalStateException("Falha ao exportar.")
                        withContext(Dispatchers.Main) { showToast("Exportado para a galeria. O arquivo saiu do cofre.") }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { showToast(e.message ?: "Erro ao exportar.") }
                    }
                }
            },
            onDelete = {
                val media = selectedMedia ?: return@MediaViewer
                if (readOnlyMode) {
                    showToast("Modo somente leitura ativo.")
                    return@MediaViewer
                }
                coroutineScope.launch(Dispatchers.IO) {
                    mediaVaultManager.deleteMedia(media.id)
                    withContext(Dispatchers.Main) {
                        selectedMedia = null
                        selectedMediaBytes = null
                        showToast("Apagado permanentemente do cofre.")
                        loadMedia()
                    }
                }
            }
        )
        return
    }

    val filteredMedia = mediaItems.filter {
        when (selectedFilter) {
            "Fotos" -> it.mimeType.startsWith("image/")
            "Vídeos" -> it.mimeType.startsWith("video/")
            else -> true
        }
    }

    PremiumScreen {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 18.dp)
            ) {
                LyvoxMobileHeader(
                    title = "Mídia",
                    subtitle = "Suas fotos e vídeos protegidos com criptografia de ponta a ponta."
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Tudo" to Icons.Filled.GridView, "Fotos" to Icons.Filled.Image, "Vídeos" to Icons.Filled.SmartDisplay, "Álbuns" to Icons.Filled.Folder).forEach { (label, icon) ->
                        FilterChip(
                            selected = selectedFilter == label,
                            onClick = { selectedFilter = label },
                            label = { Text(label) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentMuted,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceElevated.copy(alpha = 0.42f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == label,
                                borderColor = if (selectedFilter == label) Accent else DarkBorder,
                                selectedBorderColor = Accent
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                MediaUsageCard(total = mediaItems.size)
                Spacer(Modifier.height(18.dp))
                when {
                    isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }

                    filteredMedia.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Accent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Nenhuma mídia no cofre", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Adicione fotos ou vídeos para criptografar localmente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 128.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMedia, key = { it.id }) { item ->
                            MediaCard(
                                item = item,
                                onClick = {
                                    selectedMedia = item
                                    selectedMediaBytes = null
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val bytes = mediaVaultManager.getMediaData(item.id)
                                        withContext(Dispatchers.Main) { selectedMediaBytes = bytes }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Button(
                onClick = {
                    if (readOnlyMode) {
                        showToast("Modo somente leitura ativo.")
                    } else {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .height(64.dp)
                    .width(250.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(32.dp),
                enabled = !readOnlyMode
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)).background(PremiumButtonBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar mídia", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaUsageCard(total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB3181D2C))
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AccentMuted),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Security, contentDescription = null, tint = Accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("$total itens", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Armazenamento criptografado", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier.width(116.dp).height(7.dp).clip(RoundedCornerShape(99.dp)).background(DarkBorder.copy(alpha = 0.55f))
        ) {
            Box(Modifier.fillMaxWidth(0.62f).height(7.dp).background(PremiumButtonBrush))
        }
    }
}

@Composable
private fun MediaViewer(
    media: MediaItemDecrypted,
    bytes: ByteArray?,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (bytes == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (media.mimeType.startsWith("video/")) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(86.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Vídeo protegido", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Exporte para reproduzir fora do cofre.", color = Color.White.copy(alpha = 0.68f))
            }
        } else {
            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap() }
            Image(bitmap = bitmap, contentDescription = media.filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExport) { Icon(Icons.Filled.Restore, contentDescription = "Exportar", tint = Color.White) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteForever, contentDescription = "Excluir", tint = Color.Red) }
        }
    }
}

@Composable
fun MediaCard(
    item: MediaItemDecrypted,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.thumbnailBytes != null) {
                val bitmap = remember(item.thumbnailBytes) {
                    BitmapFactory.decodeByteArray(item.thumbnailBytes, 0, item.thumbnailBytes.size).asImageBitmap()
                }
                Image(bitmap = bitmap, contentDescription = item.filename, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.mimeType.startsWith("video/")) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(26.dp),
                    tint = Color.White
                )
            }
        }
    }
}
