package com.routinepulse.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.NoteRequest
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.NoteRepository
import com.routinepulse.app.data.repository.QuizRepository
import com.routinepulse.app.domain.FieldError
import com.routinepulse.app.domain.NoteLogic
import com.routinepulse.app.domain.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteFormState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val generating: Boolean = false,
    val isEdit: Boolean = false,
    val subject: String = "",
    val title: String = "",
    val tagsText: String = "",
    val content: String = "",
    val preview: Boolean = false,
    val subjectError: FieldError? = null,
    val titleError: FieldError? = null,
    val contentError: FieldError? = null,
    val error: ApiError? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val quizReadyId: String? = null
)

/** Note editor (Mockup 4) including the single-tap "Generate quiz" action (REQ-06). */
class NoteEditViewModel(
    private val notes: NoteRepository,
    private val quizzes: QuizRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NoteFormState())
    val state: StateFlow<NoteFormState> = _state.asStateFlow()

    private var noteId: String? = null
    private var initialised = false

    fun load(id: String?) {
        if (initialised) return
        initialised = true
        noteId = id
        if (id == null) return
        _state.update { it.copy(loading = true, isEdit = true) }
        viewModelScope.launch {
            when (val result = notes.get(id)) {
                is ApiResult.Success -> _state.update {
                    val n = result.data
                    it.copy(
                        loading = false, subject = n.subject, title = n.title,
                        tagsText = (n.tags ?: emptyList()).joinToString(", "), content = n.content
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun onSubject(v: String) = _state.update { it.copy(subject = v, subjectError = null, error = null) }
    fun onTitle(v: String) = _state.update { it.copy(title = v, titleError = null, error = null) }
    fun onTags(v: String) = _state.update { it.copy(tagsText = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v, contentError = null, error = null) }
    fun togglePreview() = _state.update { it.copy(preview = !it.preview) }
    fun consumeQuizReady() = _state.update { it.copy(quizReadyId = null) }

    /** Validates, then creates or updates the note. Returns the saved note id via [onDone] (null on failure). */
    private fun validateAndSave(onDone: (String?) -> Unit) {
        val s = _state.value
        val subjectError = Validators.noteSubject(s.subject)
        val titleError = Validators.noteTitle(s.title)
        val contentError = Validators.noteContent(s.content)
        if (subjectError != null || titleError != null || contentError != null) {
            _state.update { it.copy(subjectError = subjectError, titleError = titleError, contentError = contentError) }
            onDone(null)
            return
        }
        val request = NoteRequest(s.subject.trim(), s.title.trim(), s.content, NoteLogic.parseTags(s.tagsText))
        viewModelScope.launch {
            val id = noteId
            val result = if (id == null) notes.create(request) else notes.update(id, request)
            when (result) {
                is ApiResult.Success -> {
                    noteId = result.data.id
                    onDone(result.data.id)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(saving = false, generating = false, error = result.error) }
                    onDone(null)
                }
            }
        }
    }

    fun save() {
        if (_state.value.saving || _state.value.generating) return
        _state.update { it.copy(saving = true, error = null) }
        validateAndSave { id ->
            _state.update { it.copy(saving = false, saved = id != null) }
        }
    }

    fun delete() {
        val id = noteId ?: return
        viewModelScope.launch {
            when (val result = notes.delete(id)) {
                is ApiResult.Success -> _state.update { it.copy(deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
            }
        }
    }

    /** Saves the note first (so the server quizzes the latest text), then generates the quiz. */
    fun saveAndGenerateQuiz() {
        if (_state.value.saving || _state.value.generating) return
        _state.update { it.copy(generating = true, error = null) }
        validateAndSave { id ->
            if (id == null) {
                _state.update { it.copy(generating = false) }
            } else {
                viewModelScope.launch {
                    when (val result = quizzes.generate(id)) {
                        is ApiResult.Success -> {
                            Log.i("NoteEditViewModel", "Quiz generated (source=${result.data.source})")
                            _state.update { it.copy(generating = false, quizReadyId = result.data.id) }
                        }
                        is ApiResult.Failure -> _state.update { it.copy(generating = false, error = result.error) }
                    }
                }
            }
        }
    }
}
