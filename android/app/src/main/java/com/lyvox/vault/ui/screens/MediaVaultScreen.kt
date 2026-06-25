package com.lyvox.vault.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lyvox.vault.service.MediaVaultManager
import com.lyvox.vault.data.model.MediaItemDecrypted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(mediaVaultManager: MediaVaultManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mediaItems by remember { mutableStateOf<List<MediaItemDecrypted>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var pendingMediaId by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportWarning by remember { mutableStateOf(false) }
    
    // Viewer State
    var selectedMedia by remember { mutableStateOf<MediaItemDecrypted?>(null) }
    var selectedMediaBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
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
                e.printStackTrace()
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadMedia()
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingMediaId = null
            loadMedia()
            showToast("Mídia original apagada. Cópia segura no cofre!")
        } else {
            pendingMediaId = null
            loadMedia()
            showToast("Cópia salva. O original continuará visível na galeria do sistema.")
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    
                    if (bytes != null) {
                        // Generate a simple thumbnail if it's an image
                        var thumbBytes: ByteArray? = null
                        if (mimeType.startsWith("image/")) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            val scale = Math.max(1, Math.min(opts.outWidth / 200, opts.outHeight / 200))
                            val opts2 = BitmapFactory.Options().apply { inSampleSize = scale }
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)
                            if (bmp != null) {
                                val baos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                                thumbBytes = baos.toByteArray()
                            }
                        }

                        val filename = "media_${System.currentTimeMillis()}"
                        val mediaId = mediaVaultManager.createMedia(filename, mimeType, bytes, thumbBytes)
                        
                        withContext(Dispatchers.Main) {
                            pendingMediaId = mediaId
                            
                            // Tenta excluir o arquivo original da galeria
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } else {
                                    val deleted = contentResolver.delete(uri, null, null)
                                    if (deleted > 0) {
                                        pendingMediaId = null
                                        loadMedia()
                                    } else {
                                        // Falha na exclusão
                                        coroutineScope.launch(Dispatchers.IO) {
                                            mediaVaultManager.deleteMedia(mediaId)
                                            withContext(Dispatchers.Main) { pendingMediaId = null; loadMedia() }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                                    deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                                } else {
                                    e.printStackTrace()
                                    // Rollback in case of failure
                                    coroutineScope.launch(Dispatchers.IO) {
                                        mediaVaultManager.deleteMedia(mediaId)
                                        withContext(Dispatchers.Main) { pendingMediaId = null; loadMedia() }
                                    }
                                }
                            }
                        }
                        
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    if (selectedMedia != null) {
        // Media Viewer Component
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (selectedMediaBytes == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else {
                val isVideo = selectedMedia!!.mimeType.startsWith("video/")
                if (isVideo) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "PrÃ©via de vÃ­deo desativada para evitar arquivo temporÃ¡rio em texto claro.",
                            color = Color.White,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    val bitmap = remember(selectedMediaBytes) {
                        BitmapFactory.decodeByteArray(selectedMediaBytes, 0, selectedMediaBytes!!.size).asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            // Viewer Top Bar
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { 
                        selectedMedia = null 
                        selectedMediaBytes = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Restaurar
                        val media = selectedMedia!!
                        val bytes = selectedMediaBytes
                        if (bytes != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, media.filename)
                                        put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, if (media.mimeType.startsWith("video/")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
                                        }
                                    }
                                    val collectionUri = if (media.mimeType.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    val resolver = context.contentResolver
                                    val uri = resolver.insert(collectionUri, contentValues)
                                    if (uri != null) {
                                        resolver.openOutputStream(uri)?.use { it.write(bytes) }
                                        mediaVaultManager.deleteMedia(media.id)
                                        withContext(Dispatchers.Main) {
                                            selectedMedia = null
                                            selectedMediaBytes = null
                                            loadMedia()
                                            showToast("Mídia restaurada para a galeria!")
                                        }
                                    }
                                } catch(e:Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) { showToast("Erro ao restaurar") }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restaurar", tint = Color.White)
                    }
                    IconButton(onClick = {
                        // Delete Permanente
                        val media = selectedMedia!!
                        coroutineScope.launch(Dispatchers.IO) {
                            mediaVaultManager.deleteMedia(media.id)
                            withContext(Dispatchers.Main) {
                                selectedMedia = null
                                selectedMediaBytes = null
                                loadMedia()
                                showToast("Apagado permanentemente do cofre.")
                            }
                        }
                    }) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Apagar Permanentemente", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    } else {
        // Vault Grid
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CenterAlignedTopAppBar(
                    title = { Text("Cofre de Mídias") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (mediaItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Nenhuma mídia no cofre",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(mediaItems, key = { it.id }) { item ->
                            MediaCard(
                                item = item,
                                onClick = {
                                    selectedMedia = item
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val bytes = mediaVaultManager.getMediaData(item.id)
                                            withContext(Dispatchers.Main) {
                                                selectedMediaBytes = bytes
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { 
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) 
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Importar Mídia")
            }
        }
    }
}

@Composable
fun MediaCard(
    item: MediaItemDecrypted,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
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
                Image(
                    bitmap = bitmap,
                    contentDescription = item.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp),
                    tint = Color.White
                )
            }
        }
    }
}
