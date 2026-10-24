package com.omran.notizapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omran.notizapp.data.local.NoteEntity
import com.omran.notizapp.data.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NoteSaveStatus {
    LOADING,
    EMPTY,
    PENDING,
    SAVING,
    SAVED,
    ERROR
}

data class NoteEditorUiState(
    val categoryId: Long? = null,
    val requestedNoteId: Long? = null,
    val currentNoteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val saveStatus: NoteSaveStatus = NoteSaveStatus.EMPTY,
    val errorMessage: String? = null
)

class NoteViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    private val _editorState =
        MutableStateFlow(NoteEditorUiState())

    val editorState = _editorState.asStateFlow()

    private var activeSession: Pair<Long, Long?>? = null
    private var sessionGeneration = 0L

    private var currentNoteId: Long? = null

    private var savedTitle = ""
    private var savedContent = ""

    private var debounceJob: Job? = null
    private var writeJob: Job? = null

    companion object {
        private const val AUTOSAVE_DELAY = 700L

        fun factory(repository: NoteRepository) =
            viewModelFactory {
                initializer {
                    NoteViewModel(repository)
                }
            }
    }

    // Wird weiterhin von der Kategorienansicht benutzt.
    fun observeNotes(
        categoryId: Long
    ): Flow<List<NoteEntity>> {
        return repository.observeNotes(categoryId)
    }

    /**
     * Startet eine Editorsitzung.
     *
     * Bei einer Bildschirmrotation bleibt das ViewModel
     * erhalten. Die laufende Sitzung wird nicht neu geladen.
     */
    fun openEditor(
        categoryId: Long,
        noteId: Long?
    ) {
        val key = categoryId to noteId

        if (activeSession == key) return

        debounceJob?.cancel()

        activeSession = key
        sessionGeneration++

        val generation = sessionGeneration

        currentNoteId = noteId
        savedTitle = ""
        savedContent = ""

        _editorState.value = NoteEditorUiState(
            categoryId = categoryId,
            requestedNoteId = noteId,
            currentNoteId = noteId,
            isLoading = noteId != null,
            isReady = noteId == null,
            saveStatus = if (noteId == null) {
                NoteSaveStatus.EMPTY
            } else {
                NoteSaveStatus.LOADING
            }
        )

        if (noteId == null) return

        viewModelScope.launch {
            val note = try {
                repository.getNote(noteId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(
                    "NoteViewModel",
                    "Fehler beim Laden",
                    exception
                )
                null
            }

            // Alte Ladeergebnisse ignorieren.
            if (generation != sessionGeneration) {
                return@launch
            }

            if (note == null ||
                note.categoryId != categoryId
            ) {
                _editorState.update {
                    it.copy(
                        isLoading = false,
                        isReady = false,
                        saveStatus = NoteSaveStatus.ERROR,
                        errorMessage =
                            "Notiz konnte nicht geladen werden."
                    )
                }
                return@launch
            }

            currentNoteId = note.id
            savedTitle = note.title
            savedContent = note.content

            _editorState.value = NoteEditorUiState(
                categoryId = categoryId,
                requestedNoteId = noteId,
                currentNoteId = note.id,
                title = note.title,
                content = note.content,
                isReady = true,
                saveStatus = NoteSaveStatus.SAVED
            )
        }
    }

    fun updateTitle(value: String) {
        if (!_editorState.value.isReady) return

        _editorState.update {
            it.copy(
                title = value,
                saveStatus = NoteSaveStatus.PENDING,
                errorMessage = null
            )
        }

        scheduleAutosave()
    }

    fun updateContent(value: String) {
        if (!_editorState.value.isReady) return

        _editorState.update {
            it.copy(
                content = value,
                saveStatus = NoteSaveStatus.PENDING,
                errorMessage = null
            )
        }

        scheduleAutosave()
    }

    private fun hasUnsavedChanges(): Boolean {
        val state = _editorState.value

        return state.isReady &&
                (
                        state.title != savedTitle ||
                                state.content != savedContent
                        )
    }

    private fun scheduleAutosave() {
        // Nur die Wartezeit abbrechen.
        // Einen gestarteten Datenbank-Write NICHT abbrechen.
        debounceJob?.cancel()

        debounceJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY)
            startWrite()
        }
    }

    /**
     * Es darf höchstens einen aktiven Writer geben.
     *
     * Wenn während eines Schreibvorgangs neue Änderungen
     * eintreffen, speichert die Schleife anschließend
     * den neuesten Stand.
     */
    private fun startWrite(): Job {
        val running = writeJob

        if (running != null && !running.isCompleted) {
            return running
        }

        val generation = sessionGeneration

        val job = viewModelScope.launch {

            while (generation == sessionGeneration) {

                val snapshot = _editorState.value

                if (!snapshot.isReady) break

                if (!hasUnsavedChanges()) {
                    _editorState.update {
                        it.copy(
                            saveStatus =
                                if (currentNoteId == null) {
                                    NoteSaveStatus.EMPTY
                                } else {
                                    NoteSaveStatus.SAVED
                                }
                        )
                    }
                    break
                }

                // Keine leere neue Notiz anlegen.
                if (currentNoteId == null &&
                    snapshot.title.isBlank() &&
                    snapshot.content.isBlank()
                ) {
                    _editorState.update {
                        it.copy(
                            saveStatus = NoteSaveStatus.EMPTY
                        )
                    }
                    break
                }

                _editorState.update {
                    it.copy(
                        saveStatus = NoteSaveStatus.SAVING,
                        errorMessage = null
                    )
                }

                val savedId = try {
                    repository.saveNote(
                        categoryId = snapshot.categoryId!!,
                        noteId = currentNoteId,
                        title = snapshot.title,
                        content = snapshot.content
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(
                        "NoteViewModel",
                        "Autosave fehlgeschlagen",
                        exception
                    )
                    null
                }

                if (generation != sessionGeneration) {
                    break
                }

                if (savedId == null) {
                    _editorState.update {
                        it.copy(
                            saveStatus = NoteSaveStatus.ERROR,
                            errorMessage =
                                "Speichern fehlgeschlagen."
                        )
                    }
                    break
                }

                // Ab jetzt ausschließlich diese ID verwenden.
                currentNoteId = savedId

                savedTitle = snapshot.title
                savedContent = snapshot.content

                val hasNewerChanges = hasUnsavedChanges()

                _editorState.update {
                    it.copy(
                        currentNoteId = savedId,
                        saveStatus =
                            if (hasNewerChanges) {
                                NoteSaveStatus.PENDING
                            } else {
                                NoteSaveStatus.SAVED
                            },
                        errorMessage = null
                    )
                }

                // Falls weitergetippt wurde: erneut speichern.
                if (!hasNewerChanges) break
            }
        }

        writeJob = job
        return job
    }

    /**
     * Sofort speichern, z.B. beim Hintergrundwechsel.
     * Wartet nicht synchron auf die Datenbank.
     */
    fun saveImmediately() {
        debounceJob?.cancel()

        if (!_editorState.value.isReady) return

        if (hasUnsavedChanges()) {
            startWrite()
        }
    }

    /**
     * Vor dem Zurücknavigieren aufrufen.
     *
     * true  = alles gespeichert oder neue Notiz leer
     * false = Speichern fehlgeschlagen
     */
    suspend fun flushBeforeLeaving(): Boolean {
        debounceJob?.cancel()

        if (!_editorState.value.isReady) {
            return false
        }

        while (true) {

            // Laufende Inserts/Updates zuerst abschließen.
            val running = writeJob

            if (running != null && !running.isCompleted) {
                running.join()
                continue
            }

            if (!hasUnsavedChanges()) {
                return true
            }

            val state = _editorState.value

            // Leere, nie gespeicherte Notiz verwerfen.
            if (currentNoteId == null &&
                state.title.isBlank() &&
                state.content.isBlank()
            ) {
                return true
            }

            val job = startWrite()
            job.join()

            if (_editorState.value.saveStatus ==
                NoteSaveStatus.ERROR
            ) {
                return false
            }
        }
    }


    suspend fun discardAndFinish() {
        debounceJob?.cancel()
        writeJob?.join()
        finishEditing()
    }


    /**
     * Nur aufrufen, nachdem kein Write mehr läuft.
     */
    fun finishEditing() {
        debounceJob?.cancel()

        sessionGeneration++
        activeSession = null

        currentNoteId = null
        savedTitle = ""
        savedContent = ""

        _editorState.value = NoteEditorUiState()
    }


    fun observeCategoryAvailability(
        categoryId: Long
    ): Flow<Boolean> {
        return repository.observeCategoryAvailability(categoryId)
    }

}
