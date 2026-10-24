package com.omran.notizapp.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.omran.notizapp.R
import com.omran.notizapp.ui.util.localizedTechnicalError
import com.omran.notizapp.data.backup.BackupExporter
import com.omran.notizapp.data.backup.BackupImporter
import com.omran.notizapp.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hauptmenü: Papierkorb, Backup und App-Sprache. */
@Composable
fun BackupExportButton(
    onOpenTrash: () -> Unit,
    onMessage: (String) -> Unit,
    enabled: Boolean = true
) {
    // Kontext für Backup und Datenbank; Ressourcen für Sprachwechsel.
    val context = LocalContext.current
    val resources = LocalResources.current
    val appContext = context.applicationContext
    val database = remember(appContext) { AppDatabase.getInstance(appContext) }
    val exporter = remember(appContext, database) { BackupExporter(appContext, database) }
    val importer = remember(appContext, database) { BackupImporter(appContext, database) }
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importCandidate by remember { mutableStateOf<Uri?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val moreDescription = stringResource(R.string.menu_more)

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    exporter.exportTo(uri)
                    onMessage(resources.getString(R.string.backup_export_success))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onMessage(
                        resources.getString(
                            R.string.backup_export_failure,
                            localizedTechnicalError(resources, e)
                        )
                    )
                } finally {
                    busy = false
                }
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) importCandidate = uri
    }

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled && !busy,
            modifier = Modifier.semantics { contentDescription = moreDescription }
        ) {
            if (busy) {
                CircularProgressIndicator()
            } else {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
        }

        DropdownMenu(
            expanded = expanded && enabled && !busy,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_trash)) },
                onClick = {
                    expanded = false
                    onOpenTrash()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_backup_export)) },
                onClick = {
                    expanded = false
                    val stamp = SimpleDateFormat(
                        "yyyy-MM-dd_HH-mm", Locale.ROOT
                    ).format(Date())
                    exportPicker.launch("NotizApp_Backup_$stamp.zip")
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_backup_import)) },
                onClick = {
                    expanded = false
                    importPicker.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_language)) },
                onClick = {
                    expanded = false
                    showLanguageDialog = true
                }
            )
        }
    }

    if (showLanguageDialog) {
        // Bei leerer Locale-Liste folgt die App automatisch dem System.
        val selected = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore('-')
            .lowercase(Locale.ROOT)

        val selectLanguage: (String) -> Unit = { tag ->
            showLanguageDialog = false
            val locales = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            if (AppCompatDelegate.getApplicationLocales() != locales) {
                // Führt bei einem echten Wechsel i.d.R. zur Activity-Neuerstellung.
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.menu_language)) },
            text = {
                Column {
                    AppLanguageRow(
                        label = stringResource(R.string.language_system),
                        selected = selected.isEmpty(),
                        onClick = { selectLanguage("") }
                    )
                    AppLanguageRow(
                        label = stringResource(R.string.language_german),
                        selected = selected == "de",
                        onClick = { selectLanguage("de") }
                    )
                    AppLanguageRow(
                        label = stringResource(R.string.language_english),
                        selected = selected == "en",
                        onClick = { selectLanguage("en") }
                    )
                    AppLanguageRow(
                        label = stringResource(R.string.language_arabic),
                        selected = selected == "ar",
                        onClick = { selectLanguage("ar") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (importCandidate != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) importCandidate = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_description)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val uri = importCandidate ?: return@TextButton
                        importCandidate = null
                        busy = true
                        scope.launch {
                            try {
                                val result = importer.importFrom(uri)
                                onMessage(
                                    resources.getString(
                                        R.string.backup_import_success,
                                        result.categories,
                                        result.notes,
                                        result.audio,
                                        result.images,
                                        result.folderName
                                    )
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                onMessage(
                                    resources.getString(
                                        R.string.backup_import_failure,
                                        localizedTechnicalError(resources, e)
                                    )
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_import_action))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { importCandidate = null }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AppLanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
