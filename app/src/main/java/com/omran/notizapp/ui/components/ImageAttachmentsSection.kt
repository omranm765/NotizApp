package com.omran.notizapp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.omran.notizapp.R
import com.omran.notizapp.ui.util.localizedTechnicalError
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omran.notizapp.data.local.AppDatabase
import com.omran.notizapp.data.local.ImageAttachmentEntity
import com.omran.notizapp.data.repository.ImageAttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Standardmäßig nur eine kompakte Zeile. Galerie und Bilder werden erst
 * nach Antippen angezeigt. Bestehende Text- und Audiofunktionen bleiben frei.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAttachmentsSection(
    noteId: Long,
    categoryId: Long,
    enabled: Boolean,
    onBlockingStateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current
    val repository = remember(context) {
        ImageAttachmentRepository(context, AppDatabase.getInstance(context))
    }
    val imageFlow = remember(repository, noteId) { repository.observe(noteId) }
    val images by imageFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var showGallery by remember(noteId) { mutableStateOf(false) }
    var selectedImage by remember(noteId) { mutableStateOf<ImageAttachmentEntity?>(null) }
    var deletionRequested by remember(noteId) { mutableStateOf<ImageAttachmentEntity?>(null) }
    var busy by remember(noteId) { mutableStateOf(false) }
    var message by remember(noteId) { mutableStateOf<String?>(null) }

    // Verwaiste Bilder nach Abstürzen später automatisch entfernen (24h Schonfrist).
    LaunchedEffect(repository) {
        runCatching { repository.cleanupOrphanedFiles() }
    }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty() && !busy && enabled) {
            busy = true
            onBlockingStateChange(true)
            scope.launch {
                var added = 0
                try {
                    for (uri in uris.take(12)) {
                        repository.addFromUri(noteId, categoryId, uri)
                        added++
                    }
                    message = resources.getString(R.string.images_added, added)
                    if (uris.size > 12) {
                        message = resources.getString(R.string.images_limit)
                    }
                } catch (error: Exception) {
                    message = resources.getString(R.string.images_partial_fail, added,
                        localizedTechnicalError(resources, error))
                } finally {
                    busy = false
                    onBlockingStateChange(false)
                }
            }
        }
    }

    val openPicker = {
        if (enabled && !busy) {
            pickImages.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy) { showGallery = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.images_count, images.size), style = MaterialTheme.typography.titleMedium)
            Text(
                if (images.isEmpty()) stringResource(R.string.images_none_attached)
                else stringResource(R.string.images_tap_gallery),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = openPicker, enabled = enabled && !busy) {
            Text(stringResource(R.string.images_add))
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.images_saving))
        }
    }
    if (message != null) {
        Text(
            message ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showGallery) {
        ModalBottomSheet(onDismissRequest = { if (!busy) showGallery = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.images_count, images.size), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = openPicker, enabled = enabled && !busy) {
                        Text(stringResource(R.string.images_add))
                    }
                }
                if (images.isEmpty()) {
                    Text(stringResource(R.string.images_empty))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images, key = { it.id }) { image ->
                            Surface(
                                onClick = { selectedImage = image },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                tonalElevation = 1.dp
                            ) {
                                ImageFromFile(
                                    file = repository.fileFor(image),
                                    maxDimension = 360,
                                    modifier = Modifier.fillMaxSize(),
                                    scale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    val preview = selectedImage
    if (preview != null) {
        Dialog(
            onDismissRequest = { selectedImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedImage = null }) {
                        Text("← " + stringResource(R.string.images_close), color = Color.White)
                    }
                    TextButton(
                        onClick = { deletionRequested = preview },
                        enabled = enabled && !busy
                    ) {
                        Text(stringResource(R.string.action_delete), color = Color.White)
                    }
                }
                var zoom by remember(preview.id) { mutableFloatStateOf(1f) }
                var translation by remember(preview.id) { mutableStateOf(Offset.Zero) }
                ImageFromFile(
                    file = repository.fileFor(preview),
                    maxDimension = 2200,
                    scale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth().weight(1f)
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = translation.x,
                            translationY = translation.y
                        )
                        .pointerInput(preview.id) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                translation = if (zoom == 1f) Offset.Zero
                                else translation + pan
                            }
                        }
                )
            }
        }
    }

    val toDelete = deletionRequested
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) deletionRequested = null },
            title = { Text(stringResource(R.string.images_remove_title)) },
            text = {
                Text(stringResource(R.string.images_remove_description))
            },
            confirmButton = {
                TextButton(enabled = enabled && !busy, onClick = {
                    busy = true
                    onBlockingStateChange(true)
                    scope.launch {
                        try {
                            repository.delete(toDelete.id, noteId)
                            selectedImage = null
                            deletionRequested = null
                            message = resources.getString(R.string.images_removed)
                        } catch (error: Exception) {
                            message = resources.getString(R.string.images_remove_failed)
                        } finally {
                            busy = false
                            onBlockingStateChange(false)
                        }
                    }
                }) { Text(stringResource(R.string.images_remove)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deletionRequested = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ImageFromFile(
    file: File,
    maxDimension: Int,
    modifier: Modifier = Modifier,
    scale: ContentScale
) {
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath, maxDimension) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeReducedBitmap(file, maxDimension)?.asImageBitmap() }.getOrNull()
        }
    }
    val decoded = bitmap
    if (decoded != null) {
        Image(
            bitmap = decoded,
            contentDescription = stringResource(R.string.images_attached_description),
            modifier = modifier,
            contentScale = scale
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.images_loading), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun decodeReducedBitmap(file: File, maxDimension: Int): Bitmap? {
    if (!file.isFile) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val factor = minOf(1f, maxDimension.toFloat() / max(width, height))
            decoder.setTargetSize(
                max(1, (width * factor).toInt()),
                max(1, (height * factor).toInt())
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth < 1 || options.outHeight < 1) return null
        var sample = 1
        while (max(options.outWidth, options.outHeight) / sample > maxDimension) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
