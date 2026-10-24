package com.omran.notizapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.omran.notizapp.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omran.notizapp.data.repository.TrashResult
import com.omran.notizapp.ui.viewmodel.TrashViewModel
import com.omran.notizapp.ui.viewmodel.trashResultMessage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private data class PermanentDeleteRequest(
    val id: Long,
    val title: String,
    val isCategory: Boolean
)

@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBack: () -> Unit,
    backEnabled: Boolean = true
) {
    val categories by viewModel.trashedCategories
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val notes by viewModel.trashedNotes
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }

    var pendingDelete by remember {
        mutableStateOf<PermanentDeleteRequest?>(null)
    }


    var pendingBulkAction by remember {
        mutableStateOf<BulkDeleteAction?>(null)
    }

    var bulkConfirmation by remember {
        mutableStateOf("")
    }


    fun performAction(
        successMessage: String,
        operation: suspend () -> TrashResult
    ) {
        if (isBusy) return

        isBusy = true

        scope.launch {
            val message = try {
                trashResultMessage(
                    operation(),
                    successMessage,
                    resources
                )
            } catch (exception: Exception) {
                resources.getString(R.string.trash_action_failure)
            } finally {
                isBusy = false
            }

            snackbarHostState.showSnackbar(message)
        }
    }

    // Während einer Datenbankaktion nicht
    // versehentlich den Bildschirm verlassen.
    BackHandler(enabled = isBusy) {}

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                item {
                    TextButton(
                        onClick = onBack,
                        enabled = backEnabled && !isBusy
                    ) {
                        Text("← " + stringResource(R.string.action_back))
                    }

                    Text(
                        stringResource(R.string.menu_trash),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        stringResource(R.string.trash_intro),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.trash_manage),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(
                            onClick = {
                                bulkConfirmation = ""
                                pendingBulkAction =
                                    BulkDeleteAction.EMPTY_TRASH
                            },
                            enabled = !isBusy &&
                                    (categories.isNotEmpty() || notes.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.trash_empty_button))
                        }

                        OutlinedButton(
                            onClick = {
                                bulkConfirmation = ""
                                pendingBulkAction =
                                    BulkDeleteAction.ALL_NOTES
                            },
                            enabled = !isBusy && notes.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.trash_delete_all_notes))
                        }

                        OutlinedButton(
                            onClick = {
                                bulkConfirmation = ""
                                pendingBulkAction =
                                    BulkDeleteAction.ALL_CATEGORIES
                            },
                            enabled = !isBusy && categories.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.trash_delete_all_categories))
                        }
                    }
                }


                if (categories.isEmpty() && notes.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.trash_empty_label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.trash_categories_heading),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(
                        items = categories,
                        key = { "trash-category-${it.id}" }
                    ) { category ->

                        TrashItemCard(
                            title = category.name,
                            type = stringResource(R.string.trash_category_type),
                            deletedAt = category.deletedAt,
                            isBusy = isBusy,

                            onRestore = {
                                performAction(
                                    resources.getString(R.string.trash_restored_category)
                                ) {
                                    viewModel.restoreCategory(category.id)
                                }
                            },

                            onDelete = {
                                pendingDelete = PermanentDeleteRequest(
                                    id = category.id,
                                    title = category.name,
                                    isCategory = true
                                )
                            }
                        )
                    }
                }

                if (notes.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.trash_notes_heading),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(
                        items = notes,
                        key = { "trash-note-${it.id}" }
                    ) { note ->

                        TrashItemCard(
                            title = note.title.ifBlank {
                                resources.getString(R.string.note_untitled)
                            },
                            type = stringResource(R.string.trash_note_type),
                            deletedAt = note.deletedAt,
                            isBusy = isBusy,

                            onRestore = {
                                performAction(
                                    resources.getString(R.string.trash_restored_note)
                                ) {
                                    viewModel.restoreNote(note.id)
                                }
                            },

                            onDelete = {
                                pendingDelete = PermanentDeleteRequest(
                                    id = note.id,
                                    title = note.title.ifBlank {
                                        resources.getString(R.string.note_untitled)
                                    },
                                    isCategory = false
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val request = pendingDelete

    if (request != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) pendingDelete = null
            },
            title = {
                Text(stringResource(R.string.trash_permanent_title))
            },
            text = {
                Text(
                    if (request.isCategory) {
                        stringResource(R.string.trash_permanent_category_description, request.title)
                    } else {
                        stringResource(R.string.trash_permanent_note_description, request.title)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        pendingDelete = null

                        performAction(resources.getString(R.string.trash_permanent_success)) {
                            if (request.isCategory) {
                                viewModel.permanentlyDeleteCategory(
                                    request.id
                                )
                            } else {
                                viewModel.permanentlyDeleteNote(
                                    request.id
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.trash_permanent_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val bulkAction = pendingBulkAction

    if (bulkAction != null) {

        val dialogTitle = when (bulkAction) {
            BulkDeleteAction.EMPTY_TRASH ->
                stringResource(R.string.trash_bulk_all_title)

            BulkDeleteAction.ALL_NOTES ->
                stringResource(R.string.trash_bulk_notes_title)

            BulkDeleteAction.ALL_CATEGORIES ->
                stringResource(R.string.trash_bulk_categories_title)
        }

        val warningText = when (bulkAction) {

            BulkDeleteAction.EMPTY_TRASH ->
                stringResource(R.string.trash_bulk_all_description, notes.size, categories.size)

            BulkDeleteAction.ALL_NOTES ->
                stringResource(R.string.trash_bulk_notes_description, notes.size)

            BulkDeleteAction.ALL_CATEGORIES ->
                stringResource(R.string.trash_bulk_categories_description, categories.size)
        }

        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    pendingBulkAction = null
                    bulkConfirmation = ""
                }
            },

            title = {
                Text(dialogTitle)
            },

            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    Text(warningText)

                    Text(
                        stringResource(R.string.trash_irreversible),
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(stringResource(R.string.trash_type_confirm))

                    OutlinedTextField(
                        value = bulkConfirmation,
                        onValueChange = {
                            bulkConfirmation = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.trash_confirmation_field)) },
                        singleLine = true
                    )
                }
            },

            confirmButton = {
                TextButton(
                    enabled = !isBusy &&

                            bulkConfirmation.trim()
                                .equals("LOESCHEN", ignoreCase = true),


                    onClick = {
                        pendingBulkAction = null
                        bulkConfirmation = ""

                        val successMessage = when (bulkAction) {
                            BulkDeleteAction.EMPTY_TRASH ->
                                resources.getString(R.string.trash_emptied)

                            BulkDeleteAction.ALL_NOTES ->
                                resources.getString(R.string.trash_notes_removed)

                            BulkDeleteAction.ALL_CATEGORIES ->
                                resources.getString(R.string.trash_categories_removed)
                        }

                        performAction(successMessage) {
                            when (bulkAction) {
                                BulkDeleteAction.EMPTY_TRASH ->
                                    viewModel.emptyTrash()

                                BulkDeleteAction.ALL_NOTES ->
                                    viewModel.deleteAllTrashedNotes()

                                BulkDeleteAction.ALL_CATEGORIES ->
                                    viewModel.deleteAllTrashedCategories()
                            }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.trash_permanent_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        pendingBulkAction = null
                        bulkConfirmation = ""
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

}


private enum class BulkDeleteAction {
    EMPTY_TRASH,
    ALL_NOTES,
    ALL_CATEGORIES
}


@Composable
private fun TrashItemCard(
    title: String,
    type: String,
    deletedAt: Long?,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val locale = LocalResources.current.configuration.locales[0]
    val unknownDate = stringResource(R.string.trash_date_unknown)
    val date = remember(deletedAt, locale, unknownDate) {
        deletedAt?.let {
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                locale
            ).format(Date(it))
        } ?: unknownDate
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                stringResource(R.string.trash_item_deleted_at, type, date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            // Untereinander bleibt auch bei schmalen
            // Displays genug Platz für die Beschriftungen.
            OutlinedButton(
                onClick = onRestore,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.trash_restore))
            }

            TextButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.trash_permanent_button),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
