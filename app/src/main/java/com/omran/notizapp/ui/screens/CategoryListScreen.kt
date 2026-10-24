package com.omran.notizapp.ui.screens

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omran.notizapp.R
import com.omran.notizapp.data.local.CategoryEntity
import com.omran.notizapp.data.local.NoteEntity
import com.omran.notizapp.data.repository.MoveCategoryResult
import com.omran.notizapp.data.repository.TrashResult
import com.omran.notizapp.ui.components.CategoryCard
import com.omran.notizapp.ui.components.BackupExportButton
import com.omran.notizapp.ui.components.MoveCategoryDialog
import com.omran.notizapp.ui.viewmodel.CategoryViewModel
import com.omran.notizapp.ui.viewmodel.NoteViewModel
import com.omran.notizapp.ui.viewmodel.TrashViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class DeleteRequest(
    val id: Long,
    val name: String,
    val isCategory: Boolean
)

private sealed interface MoveTargetsLoadState {
    data object Loading : MoveTargetsLoadState
    data class Ready(val targets: List<CategoryEntity>) : MoveTargetsLoadState
}

private fun moveCategoryResultMessage(result: MoveCategoryResult, resources: Resources): String =
    resources.getString(
        when (result) {
            MoveCategoryResult.SUCCESS -> R.string.category_move_success
            MoveCategoryResult.NOT_FOUND -> R.string.category_move_not_found
            MoveCategoryResult.SOURCE_UNAVAILABLE -> R.string.category_move_source_unavailable
            MoveCategoryResult.TARGET_UNAVAILABLE -> R.string.category_move_target_unavailable
            MoveCategoryResult.INVALID_TARGET -> R.string.category_move_invalid_target
            MoveCategoryResult.ALREADY_THERE -> R.string.category_move_already_there
            MoveCategoryResult.NAME_CONFLICT -> R.string.category_move_name_conflict
        }
    )

private fun trashCategoryResultMessage(result: TrashResult, resources: Resources): String =
    resources.getString(
        when (result) {
            TrashResult.SUCCESS -> R.string.category_trash_success
            TrashResult.NOT_FOUND -> R.string.category_trash_not_found
            TrashResult.ALREADY_IN_TRASH -> R.string.category_trash_already
            TrashResult.NOT_IN_TRASH -> R.string.category_trash_not_in_trash
            TrashResult.PARENT_UNAVAILABLE -> R.string.category_trash_parent_unavailable
            TrashResult.NAME_CONFLICT -> R.string.category_trash_name_conflict
        }
    )

// Erst wenn alle drei Room-Flows ihren ersten Wert geliefert haben,
// werden Kategorie und Notizen gemeinsam angezeigt.
private sealed interface CategoryListLoadState {
    data object Loading : CategoryListLoadState
    data object Unavailable : CategoryListLoadState

    data class Ready(
        val parent: CategoryEntity?,
        val categories: List<CategoryEntity>,
        val notes: List<NoteEntity>
    ) : CategoryListLoadState
}

@Composable
fun CategoryListScreen(
    parentId: Long?,
    viewModel: CategoryViewModel,
    noteViewModel: NoteViewModel,
    trashViewModel: TrashViewModel,
    onOpenCategory: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAudioTest: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true
) {
    val isRoot = parentId == null
    val resources = LocalResources.current

    // Keine anfänglichen emptyList()/null-Werte als fertige DB-Daten
    // interpretieren. combine liefert erst nach allen drei Quellen.
    val screenFlow: Flow<CategoryListLoadState> =
        remember(viewModel, noteViewModel, parentId) {
            val categoriesSource = viewModel.observeCategories(parentId)
            val notesSource: Flow<List<NoteEntity>> =
                if (isRoot) flowOf(emptyList())
                else noteViewModel.observeNotes(parentId!!)

            val parentSource: Flow<CategoryEntity?> =
                if (isRoot) flowOf(null)
                else viewModel.observeCategory(parentId!!)

            combine(
                categoriesSource,
                notesSource,
                parentSource
            ) { categoryItems, noteItems, parent ->
                val result: CategoryListLoadState =
                    if (!isRoot && parent == null) {
                        CategoryListLoadState.Unavailable
                    } else {
                        CategoryListLoadState.Ready(
                            parent = parent,
                            categories = categoryItems,
                            notes = noteItems
                        )
                    }
                result
            }
        }

    // key sorgt dafür, dass beim Wechsel der Kategorie keine
    // veralteten Daten eines anderen Navigationsziels erscheinen.
    val screenState by key(parentId) {
        screenFlow.collectAsStateWithLifecycle(
            initialValue = CategoryListLoadState.Loading
        )
    }

    val readyState = screenState as? CategoryListLoadState.Ready
    val categories = readyState?.categories.orEmpty()
    val notes = readyState?.notes.orEmpty()
    val parentCategory = readyState?.parent

    val title = if (isRoot) {
        stringResource(R.string.category_home_title)
    } else {
        parentCategory?.name ?: stringResource(R.string.category_generic_name)
    }

    var showDialog by rememberSaveable(parentId) {
        mutableStateOf(false)
    }

    var newName by rememberSaveable(parentId) {
        mutableStateOf("")
    }

    var pendingDelete by remember {
        mutableStateOf<DeleteRequest?>(null)
    }

    // Geöffnetes Kategorienmenü und aktuelle Verschiebeauswahl.
    var openedMenuId by remember(parentId) {
        mutableStateOf<Long?>(null)
    }
    var pendingMove by remember(parentId) {
        mutableStateOf<CategoryEntity?>(null)
    }

    var isBusy by remember {
        mutableStateOf(false)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val duplicate = categories.any {
        it.name.equals(newName.trim(), ignoreCase = true)
    }

    fun closeDialog() {
        showDialog = false
        newName = ""
    }

    // Blockiert die Android-Zurück-Geste
    // solange eine Löschaktion läuft.
    BackHandler(enabled = isBusy) {}


    // Kein falscher Fehlerbildschirm während der DB-Abfrage.
    // Auch Kategorien und Notizen werden erst nach ihrer ersten
    // echten Flow-Emission als leer oder gefüllt angezeigt.
    if (screenState !is CategoryListLoadState.Ready) {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                when (screenState) {
                    CategoryListLoadState.Loading -> {
                        CircularProgressIndicator()
                    }

                    CategoryListLoadState.Unavailable -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .padding(24.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.category_unavailable_title),
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Text(
                                text = stringResource(R.string.category_unavailable_description)
                            )

                            if (onBack != null) {
                                TextButton(
                                    onClick = onBack,
                                    enabled = backEnabled && !isBusy
                                ) {
                                    Text(stringResource(R.string.action_back))
                                }
                            }
                        }
                    }

                    is CategoryListLoadState.Ready -> Unit
                }
            }
        }
        return
    }


    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isBusy) showDialog = true
                },
                icon = {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Text(
                        if (isRoot) stringResource(R.string.category_create_root)
                        else stringResource(R.string.category_create_child)
                    )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 110.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                if (!isRoot && onBack != null) {
                    item {
                        TextButton(
                            onClick = onBack,
                            enabled = backEnabled && !isBusy
                        ) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }

                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.weight(1f)
                            )

                            if (isRoot) {
                                BackupExportButton(
                                    onOpenTrash = onOpenTrash,
                                    enabled = !isBusy,
                                    onMessage = { message ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(message)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            if (isRoot) {
                                stringResource(R.string.category_summary_root, categories.size)
                            } else {
                                stringResource(
                                    R.string.category_summary_children,
                                    categories.size,
                                    notes.size
                                )
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isRoot) {
                    item {
                        FilledTonalButton(
                            onClick = onCreateNote,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.note_create))
                        }
                    }
                }

                if (
                    categories.isEmpty() &&
                    (isRoot || notes.isEmpty())
                ) {
                    item {
                        Text(
                            if (isRoot) {
                                stringResource(R.string.category_empty_root)
                            } else {
                                stringResource(R.string.category_empty_child)
                            },
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(
                    items = categories,
                    key = { "category-${it.id}" }
                ) { category ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryCard(
                                name = category.name,
                                onClick = {
                                    if (!isBusy) {
                                        onOpenCategory(category.id)
                                    }
                                },
                                onDelete = {
                                    if (!isBusy) {
                                        openedMenuId = null
                                        pendingDelete = DeleteRequest(
                                            id = category.id,
                                            name = category.name,
                                            isCategory = true
                                        )
                                    }
                                }
                            )
                        }

                        Box {
                            IconButton(
                                enabled = !isBusy,
                                modifier = Modifier.semantics {
                                    contentDescription = resources.getString(R.string.category_actions_for, category.name)
                                },
                                onClick = {
                                    openedMenuId = category.id
                                }
                            ) {
                                Text(
                                    text = "⋮",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            DropdownMenu(
                                expanded = openedMenuId == category.id && !isBusy,
                                onDismissRequest = { openedMenuId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_move_menu)) },
                                    onClick = {
                                        openedMenuId = null
                                        if (!isBusy) {
                                            pendingMove = category
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isRoot && notes.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.notes_section),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(
                        items = notes,
                        key = { "note-${it.id}" }
                    ) { note ->

                        NoteListCard(
                            note = note,
                            onClick = {
                                if (!isBusy) onOpenNote(note.id)
                            },
                            onDelete = {
                                if (!isBusy) {
                                    pendingDelete = DeleteRequest(
                                        id = note.id,
                                        name = note.title.ifBlank {
                                            resources.getString(R.string.note_untitled)
                                        },
                                        isCategory = false
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { closeDialog() },
            title = {
                Text(
                    if (isRoot) stringResource(R.string.category_new_root)
                    else stringResource(R.string.category_new_child)
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it.take(50)
                        },
                        label = { Text(stringResource(R.string.field_name)) },
                        singleLine = true,
                        isError = duplicate,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (duplicate) {
                        Text(
                            stringResource(R.string.category_name_duplicate),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank() &&
                            !duplicate &&
                            !isBusy,
                    onClick = {
                        viewModel.createCategory(
                            name = newName,
                            parentId = parentId
                        )
                        closeDialog()
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { closeDialog() }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val moveRequest = pendingMove
    if (moveRequest != null) {
        // Erst die echte erste Datenbankantwort abwarten. Ein leeres
        // Initialergebnis würde ansonsten fälschlich wie eine leere
        // Zielliste aussehen.
        val targetsFlow: Flow<MoveTargetsLoadState> =
            remember(viewModel, moveRequest.id) {
                viewModel.observeMoveTargets(moveRequest.id).map { targets ->
                    MoveTargetsLoadState.Ready(targets)
                }
            }
        val targetsState by targetsFlow.collectAsStateWithLifecycle(
            initialValue = MoveTargetsLoadState.Loading
        )
        val targetList = (targetsState as? MoveTargetsLoadState.Ready)
            ?.targets.orEmpty()
        val targetsLoading = targetsState is MoveTargetsLoadState.Loading

        MoveCategoryDialog(
            movingCategory = moveRequest,
            targets = targetList,
            isBusy = isBusy || targetsLoading,
            onDismiss = {
                if (!isBusy) pendingMove = null
            },
            onMove = { newParentId ->
                if (!isBusy && !targetsLoading) {
                    isBusy = true
                    scope.launch {
                        val message = try {
                            val result = viewModel.moveCategory(
                                categoryId = moveRequest.id,
                                newParentId = newParentId
                            )
                            moveCategoryResultMessage(result, resources)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            resources.getString(R.string.category_move_failure)
                        } finally {
                            isBusy = false
                            pendingMove = null
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }

    val request = pendingDelete

    if (request != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) pendingDelete = null
            },
            title = {
                Text(stringResource(R.string.category_trash_title))
            },
            text = {
                Text(
                    if (request.isCategory) {
                        stringResource(R.string.category_trash_category_confirm, request.name)
                    } else {
                        stringResource(R.string.category_trash_note_confirm, request.name)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        if (!isBusy) {
                            isBusy = true
                            pendingDelete = null

                            scope.launch {
                                val message = try {
                                    val result =
                                        if (request.isCategory) {
                                            trashViewModel.trashCategory(
                                                request.id
                                            )
                                        } else {
                                            trashViewModel.trashNote(
                                                request.id
                                            )
                                        }

                                    trashCategoryResultMessage(result, resources)
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    resources.getString(R.string.category_trash_failure)
                                } finally {
                                    isBusy = false
                                }

                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_move))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun NoteListCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val resources = LocalResources.current
    val untitledLabel = stringResource(R.string.note_untitled)
    val locale: Locale = resources.configuration.locales[0]
    val formattedDate = remember(note.updatedAt, locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            locale
        ).format(Date(note.updatedAt))
    }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = note.title.ifBlank { untitledLabel },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.note_last_edited, formattedDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onDelete
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

