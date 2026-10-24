package com.omran.notizapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omran.notizapp.R
import com.omran.notizapp.data.local.CategoryEntity

/**
 * Reiner Auswahldialog. Der Aufrufer übergibt ausschließlich erlaubte Ziele
 * aus CategoryRepository.observeMoveTargets(movingCategory.id).
 * Der Repository-Check bei moveCategory() bleibt die Sicherheitsinstanz.
 */
@Composable
fun MoveCategoryDialog(
    movingCategory: CategoryEntity,
    targets: List<CategoryEntity>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    // Ausgangsziel ist der bisherige Elternordner.
    // Kein versehentliches Verschieben durch sofortiges Bestätigen.
    var selectedParentId by rememberSaveable(movingCategory.id) {
        mutableStateOf(movingCategory.parentId)
    }

    val targetPaths = remember(targets) {
        val byId = targets.associateBy { it.id }
        targets.associate { target ->
            val names = mutableListOf<String>()
            val visited = mutableSetOf<Long>()
            var current: CategoryEntity? = target
            while (current != null && visited.add(current.id)) {
                names.add(current.name)
                current = current.parentId?.let(byId::get)
            }
            target.id to names.asReversed().joinToString(" / ")
        }
    }
    val sortedTargets = remember(targets, targetPaths) {
        targets.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { targetPaths[it.id].orEmpty() }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.category_move_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.category_move_dialog_description,
                        movingCategory.name
                    )
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "root") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) {
                                    selectedParentId = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedParentId == null,
                                onClick = { selectedParentId = null },
                                enabled = !isBusy
                            )
                            Text(stringResource(R.string.category_move_root_label))
                        }
                    }
                    items(sortedTargets, key = { it.id }) { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) {
                                    selectedParentId = target.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedParentId == target.id,
                                onClick = { selectedParentId = target.id },
                                enabled = !isBusy
                            )
                            Text(targetPaths[target.id] ?: target.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(selectedParentId) },
                enabled = !isBusy && selectedParentId != movingCategory.parentId
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(R.string.action_move))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
