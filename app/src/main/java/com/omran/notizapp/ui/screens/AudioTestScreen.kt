
package com.omran.notizapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.omran.notizapp.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.omran.notizapp.audio.AudioPlayer
import com.omran.notizapp.audio.AudioRecorder
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTestScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val resources = LocalResources.current

    val recorder = remember(context) {
        AudioRecorder(context)
    }

    val player = remember {
        AudioPlayer()
    }

    var isRecording by remember {
        mutableStateOf(false)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var previewFile by remember {
        mutableStateOf<File?>(null)
    }

    var message by remember {
        mutableStateOf(resources.getString(R.string.audio_test_ready))
    }

    fun startRecording() {
        if (isRecording) return

        player.stop()
        isPlaying = false

        try {
            recorder.start()

            // Alte Testaufnahme erst nach
            // erfolgreichem Start entfernen.
            previewFile?.delete()
            previewFile = null

            isRecording = true
            message = resources.getString(R.string.audio_test_recording)

        } catch (exception: Exception) {
            message = resources.getString(R.string.audio_test_start_failed)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()
            } else {
                message = resources.getString(R.string.audio_test_permission_denied)
            }
        }

    fun stopRecording() {
        if (!isRecording) return

        try {
            previewFile = recorder.stop()
            message = resources.getString(R.string.audio_test_finished)
        } catch (exception: Exception) {
            message = resources.getString(R.string.audio_test_too_short)
        } finally {
            isRecording = false
        }
    }

    fun discardRecording() {
        if (isRecording) {
            runCatching { recorder.cancel() }
            isRecording = false
        }

        player.stop()
        isPlaying = false

        previewFile?.delete()
        previewFile = null

        message = resources.getString(R.string.audio_test_discarded)
    }

    fun requestBack() {
        if (isRecording) {
            message = resources.getString(R.string.audio_test_stop_first)
        } else {
            onBack()
        }
    }

    // Der aktuelle Dateiverweis wird auch bei
    // der späteren Entsorgung richtig berücksichtigt.
    val latestFile by rememberUpdatedState(previewFile)

    DisposableEffect(recorder, player) {
        onDispose {
            runCatching { recorder.cancel() }
            player.stop()
            latestFile?.delete()
        }
    }

    // In V1 zeichnen wir nur im Vordergrund auf.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (isRecording) {
            runCatching { recorder.cancel() }
            isRecording = false
            message = resources.getString(R.string.audio_background_canceled)
        }

        if (isPlaying) {
            player.stop()
            isPlaying = false
        }
    }

    BackHandler {
        requestBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_test_title)) },
                navigationIcon = {
                    TextButton(onClick = { requestBack() }) {
                        Text("← " + stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = if (isRecording) stringResource(R.string.audio_test_recording_status)
                    else stringResource(R.string.audio_test_audio_recording),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    enabled = !isRecording,
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
                    Text(stringResource(R.string.audio_test_start))
                }

                Button(
                    enabled = isRecording,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { stopRecording() }
                ) {
                    Text(stringResource(R.string.audio_test_stop_recording))
                }

                HorizontalDivider()

                OutlinedButton(
                    enabled = previewFile != null &&
                            !isRecording &&
                            !isPlaying,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val file = previewFile

                        if (file != null) {
                            try {
                                player.play(file) {
                                    isPlaying = false
                                }

                                isPlaying = true
                                message = resources.getString(R.string.audio_test_playing)

                            } catch (exception: Exception) {
                                isPlaying = false
                                message = resources.getString(R.string.audio_play_failed)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.audio_test_play))
                }

                OutlinedButton(
                    enabled = isPlaying,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        player.stop()
                        isPlaying = false
                        message = resources.getString(R.string.audio_test_play_stopped)
                    }
                ) {
                    Text(stringResource(R.string.audio_test_stop_playback))
                }

                TextButton(
                    enabled = isRecording || previewFile != null,
                    onClick = { discardRecording() }
                ) {
                    Text(stringResource(R.string.audio_test_discard_button))
                }

                Text(
                    text = stringResource(R.string.audio_test_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
