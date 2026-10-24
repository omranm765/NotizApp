package com.omran.notizapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omran.notizapp.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omran.notizapp.ui.viewmodel.NoteSaveStatus
import com.omran.notizapp.ui.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import com.omran.notizapp.ui.components.AudioAttachmentsSection
import com.omran.notizapp.ui.components.ImageAttachmentsSection
import com.omran.notizapp.data.repository.AudioAttachmentRepository
import kotlinx.coroutines.flow.map


private enum class CategoryAvailability {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    categoryId: Long,
    noteId: Long?,
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    audioRepository: AudioAttachmentRepository,
) {
    val state by viewModel.editorState
        .collectAsStateWithLifecycle()


    val availabilityFlow = remember(viewModel, categoryId) {
        viewModel.observeCategoryAvailability(categoryId)
            .map { available ->
                if (available) {
                    CategoryAvailability.AVAILABLE
                } else {
                    CategoryAvailability.UNAVAILABLE
                }
            }
    }


    var audioBlocking by remember(categoryId, noteId) {
        mutableStateOf(false)
    }

    var imageBlocking by remember(categoryId, noteId) {
        mutableStateOf(false)
    }

    var showAudioBusyDialog by remember(categoryId, noteId) {
        mutableStateOf(false)
    }


    val categoryAvailability by availabilityFlow
        .collectAsStateWithLifecycle(
            initialValue = CategoryAvailability.CHECKING
        )

    val categoryAvailable =
        categoryAvailability == CategoryAvailability.AVAILABLE

    val categoryUnavailable =
        categoryAvailability == CategoryAvailability.UNAVAILABLE


    val scope = rememberCoroutineScope()

    var leaving by remember(categoryId, noteId) {
        mutableStateOf(false)
    }

    var showSaveError by remember(categoryId, noteId) {
        mutableStateOf(false)
    }


    var showUnavailableDialog by remember(categoryId, noteId) {
        mutableStateOf(false)
    }


    // Die Editorsitzung einmal für dieses Navigationsziel öffnen.
    LaunchedEffect(categoryId, noteId) {
        viewModel.openEditor(
            categoryId = categoryId,
            noteId = noteId
        )
    }

    // Bei Wechsel der App in den Hintergrund
    // ausstehende Änderungen sofort speichern.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveImmediately()
    }

    val correctSession =
        state.categoryId == categoryId &&
                state.requestedNoteId == noteId

    val ready = correctSession && state.isReady

    val statusText = when {
        !correctSession -> stringResource(R.string.editor_preparing)
        state.isLoading -> stringResource(R.string.editor_note_loading)

        else -> when (state.saveStatus) {
            NoteSaveStatus.LOADING -> stringResource(R.string.editor_loading)
            NoteSaveStatus.EMPTY -> stringResource(R.string.editor_empty_note)
            NoteSaveStatus.PENDING -> stringResource(R.string.editor_unsaved)
            NoteSaveStatus.SAVING -> stringResource(R.string.editor_saving)
            NoteSaveStatus.SAVED -> stringResource(R.string.editor_saved)
            NoteSaveStatus.ERROR -> stringResource(R.string.editor_save_error)
        }
    }

    val statusColor = when (state.saveStatus) {
        NoteSaveStatus.ERROR ->
            MaterialTheme.colorScheme.error

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Kontrolliertes Zurücknavigieren.


    val requestBack: () -> Unit = {
        if (!leaving) {

            if (audioBlocking || imageBlocking) {
                showAudioBusyDialog = true

            } else if (ready && categoryUnavailable) {
                showUnavailableDialog = true

            } else {
                leaving = true

                scope.launch {
                    val canLeave = if (!ready) {
                        true
                    } else {
                        viewModel.flushBeforeLeaving()
                    }

                    if (canLeave) {
                        viewModel.finishEditing()
                        onNavigateBack()
                    } else {
                        leaving = false
                        showSaveError = true
                    }
                }
            }
        }
    }


    // Android-Zurück-Taste und Zurück-Geste abfangen.
    BackHandler {
        requestBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (noteId == null) {
                            stringResource(R.string.editor_new_note)
                        } else {
                            stringResource(R.string.editor_edit_note)
                        }
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = requestBack,
                        enabled = !leaving
                    ) {
                        Text("← " + stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = ready && categoryAvailable && !leaving,
                        onClick = {
                            viewModel.saveImmediately()
                        }
                    ) {
                        Text(stringResource(R.string.editor_save_now))
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    when {
                        leaving ||
                                (correctSession &&
                                        state.saveStatus == NoteSaveStatus.SAVING) -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        else -> {
                            Text(
                                text = when (state.saveStatus) {
                                    NoteSaveStatus.SAVED -> "✓"
                                    NoteSaveStatus.ERROR -> "!"
                                    else -> "●"
                                },
                                color = statusColor
                            )
                        }
                    }

                    Text(
                        text = if (leaving) {
                            stringResource(R.string.editor_finishing_save)
                        } else {
                            statusText
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }


                if (ready && categoryUnavailable) {
                    Text(
                        text = stringResource(R.string.editor_category_not_available),
                        color = MaterialTheme.colorScheme.error
                    )
                }



                if (
                    !correctSession ||
                    state.isLoading ||
                    (state.isReady &&
                            categoryAvailability == CategoryAvailability.CHECKING)
                ) {


                    CircularProgressIndicator()

                } else if (!state.isReady) {

                    Text(
                        text = stringResource(R.string.editor_load_failed),
                        color = MaterialTheme.colorScheme.error
                    )

                } else {

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        label = {
                            Text(stringResource(R.string.editor_title_optional))
                        },
                        enabled = !leaving && categoryAvailable,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.content,
                        onValueChange = viewModel::updateContent,
                        label = {
                            Text(stringResource(R.string.editor_note_label))
                        },
                        placeholder = {
                            Text(stringResource(R.string.editor_note_placeholder))
                        },
                        enabled = !leaving && categoryAvailable,
                        minLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                    )

                    if (state.saveStatus == NoteSaveStatus.ERROR) {

                        Text(
                            text = stringResource(R.string.editor_save_failed),
                            color = MaterialTheme.colorScheme.error
                        )

                        Button(
                            onClick = {
                                viewModel.saveImmediately()
                            }
                        ) {
                            Text(stringResource(R.string.editor_save_again))
                        }
                    }


                    if (
                        noteId != null &&
                        noteId > 0L &&
                        ready &&
                        categoryAvailable
                    ) {
                        AudioAttachmentsSection(
                            noteId = noteId,
                            categoryId = categoryId,
                            repository = audioRepository,
                            enabled = !leaving && !imageBlocking,
                            onBlockingStateChange = { blocked ->
                                audioBlocking = blocked
                            }
                        )

                        ImageAttachmentsSection(
                            noteId = noteId,
                            categoryId = categoryId,
                            enabled = !leaving && !audioBlocking,
                            onBlockingStateChange = { blocked ->
                                imageBlocking = blocked
                            }
                        )
                    }

                }
            }
        }
    }

    // Bei einem Speicherfehler nicht einfach navigieren.


// Bei einem Speicherfehler nicht einfach navigieren.
    if (showSaveError) {
        AlertDialog(
            onDismissRequest = {
                if (!leaving) showSaveError = false
            },
            title = {
                Text(stringResource(R.string.editor_save_failed_title))
            },
            text = {
                Text(
                    stringResource(R.string.editor_save_failed_dialog)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !leaving,
                    onClick = {
                        showSaveError = false
                        requestBack()
                    }
                ) {
                    Text(stringResource(R.string.editor_retry))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        enabled = !leaving,
                        onClick = {
                            showSaveError = false
                        }
                    ) {
                        Text(stringResource(R.string.editor_continue))
                    }

                    TextButton(
                        enabled = !leaving,
                        onClick = {
                            leaving = true
                            scope.launch {
                                viewModel.discardAndFinish()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.editor_discard),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

// Eine gelöschte Kategorie verhindert das Speichern.
    if (showUnavailableDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnavailableDialog = false
            },
            title = {
                Text(stringResource(R.string.editor_category_unavailable_title))
            },
            text = {
                Text(
                    stringResource(R.string.editor_category_unavailable_dialog)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !leaving,
                    onClick = {
                        showUnavailableDialog = false
                        leaving = true

                        scope.launch {
                            viewModel.discardAndFinish()
                            onNavigateBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.editor_discard_back))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnavailableDialog = false
                    }
                ) {
                    Text(stringResource(R.string.editor_stay))
                }
            }
        )
    }

// Eine laufende Audioaufnahme schützt vor Verlassen.
    if (showAudioBusyDialog) {
        AlertDialog(
            onDismissRequest = {
                showAudioBusyDialog = false
            },
            title = {
                Text(stringResource(R.string.editor_attachment_busy_title))
            },
            text = {
                Text(
                    stringResource(R.string.editor_attachment_busy)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAudioBusyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.editor_understood))
                }
            }
        )
    }
}