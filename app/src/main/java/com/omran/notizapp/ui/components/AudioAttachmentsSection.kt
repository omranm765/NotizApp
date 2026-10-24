
package com.omran.notizapp.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.omran.notizapp.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omran.notizapp.audio.AudioPlayer
import com.omran.notizapp.audio.AudioRecorder
import com.omran.notizapp.data.local.AudioAttachmentEntity
import com.omran.notizapp.data.repository.AudioAttachmentRepository
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioAttachmentsSection(
    noteId: Long,
    categoryId: Long,
    repository: AudioAttachmentRepository,
    enabled: Boolean,
    onBlockingStateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val recorder = remember(context) { AudioRecorder(context) }
    val player = remember { AudioPlayer() }

    val attachmentFlow = remember(repository, noteId) {
        repository.observe(noteId)
    }

    val attachments by attachmentFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var isRecording by remember(noteId) { mutableStateOf(false) }
    var isSaving by remember(noteId) { mutableStateOf(false) }
    var pendingFile by remember(noteId) { mutableStateOf<File?>(null) }
    var pendingDuration by remember(noteId) { mutableLongStateOf(0L) }
    var startedAt by remember(noteId) { mutableLongStateOf(0L) }
    var playingId by remember(noteId) { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<AudioAttachmentEntity?>(null) }
    var message by remember(noteId) { mutableStateOf<String?>(null) }

    val blocked = isRecording || isSaving || pendingFile != null

    SideEffect {
        onBlockingStateChange(blocked)
    }

    DisposableEffect(recorder, player) {
        onDispose {
            runCatching { recorder.cancel() }
            player.stop()
            onBlockingStateChange(false)
        }
    }

    fun startRecording() {
        if (!enabled || blocked) return

        player.stop()
        playingId = null

        try {
            recorder.start()
            startedAt = SystemClock.elapsedRealtime()
            isRecording = true
            message = resources.getString(R.string.audio_recording_msg)
        } catch (exception: Exception) {
            message = resources.getString(R.string.audio_start_failed)
        }
    }

    val latestEnabled by rememberUpdatedState(enabled)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && latestEnabled) {
            startRecording()
        } else if (!granted) {
            message = resources.getString(R.string.audio_permission_missing)
        }
    }

    fun savePending(file: File, duration: Long) {
        isSaving = true
        message = resources.getString(R.string.audio_saving_msg)

        scope.launch {
            try {
                repository.attach(
                    noteId = noteId,
                    categoryId = categoryId,
                    recordedFile = file,
                    durationMs = duration
                )
                pendingFile = null
                message = resources.getString(R.string.audio_saved_msg)
            } catch (exception: Exception) {
                message = resources.getString(R.string.audio_save_failed)
            } finally {
                isSaving = false
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            val finished = recorder.stop()

            pendingDuration =
                SystemClock.elapsedRealtime() - startedAt

            pendingFile = finished
            isRecording = false

            savePending(finished, pendingDuration)

        } catch (exception: Exception) {
            isRecording = false
            message = resources.getString(R.string.audio_finish_failed)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (isRecording) {
            runCatching { recorder.cancel() }
            isRecording = false
            message = resources.getString(R.string.audio_background_canceled)
        }

        player.stop()
        playingId = null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider()

        Text(
            text = stringResource(R.string.audio_title),
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(R.string.audio_count, attachments.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isRecording) {
            Text(
                text = stringResource(R.string.audio_recording_indicator),
                color = MaterialTheme.colorScheme.error
            )

            Button(
                onClick = { stopRecording() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.audio_stop_save))
            }
        } else {
            Button(
                enabled = enabled && !blocked,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (
                        context.checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecording()
                    } else {
                        permissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.audio_new))
            }
        }

        if (isSaving) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        pendingFile?.let { file ->
            if (!isSaving) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        savePending(file, pendingDuration)
                    }
                ) {
                    Text(stringResource(R.string.audio_retry_save))
                }

                TextButton(
                    onClick = {
                        file.delete()
                        pendingFile = null
                        message = resources.getString(R.string.audio_discarded)
                    }
                ) {
                    Text(
                        stringResource(R.string.audio_discard_unsaved),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        attachments.forEachIndexed { index, attachment ->
            key(attachment.id) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audio_index, index + 1),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = formatAudioDuration(
                            attachment.durationMs
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = enabled && !isRecording,
                            onClick = {
                                if (playingId == attachment.id) {
                                    player.stop()
                                    playingId = null
                                } else {
                                    val file = repository.fileFor(attachment)

                                    if (!file.isFile) {
                                        message = resources.getString(R.string.audio_file_missing)
                                    } else {
                                        try {
                                            player.play(file) {
                                                playingId = null
                                            }
                                            playingId = attachment.id
                                            message = null
                                        } catch (exception: Exception) {
                                            playingId = null
                                            message = resources.getString(R.string.audio_play_failed)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (playingId == attachment.id) {
                                    stringResource(R.string.audio_stop)
                                } else {
                                    stringResource(R.string.audio_play)
                                }
                            )
                        }

                        TextButton(
                            enabled = enabled && !blocked,
                            onClick = {
                                deleteTarget = attachment
                            }
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }

                HorizontalDivider()
            }
        }
    }

    deleteTarget?.let { attachment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.audio_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.audio_delete_description)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        if (playingId == attachment.id) {
                            player.stop()
                            playingId = null
                        }

                        scope.launch {
                            try {
                                repository.delete(
                                    attachment.id,
                                    noteId
                                )
                                message = resources.getString(R.string.audio_deleted_msg)
                            } catch (exception: Exception) {
                                message = resources.getString(R.string.audio_delete_failed)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.audio_delete_permanent))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun formatAudioDuration(durationMs: Long): String {
    val seconds = durationMs / 1000L
    val minutes = seconds / 60L
    return "%02d:%02d".format(
        minutes,
        seconds % 60L
    )
}
