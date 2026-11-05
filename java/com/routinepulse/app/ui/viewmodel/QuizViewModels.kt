package com.routinepulse.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.model.QuizDto
import com.routinepulse.app.data.model.SubmitResponse
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.NoteRepository
import com.routinepulse.app.data.repository.QuizRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- Quiz tab: list of quizzes + "generate from a note" ----------

data class QuizHubState(
    val loading: Boolean = true,
    val quizzes: List<QuizDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val generating: Boolean = false,
    val error: ApiError? = null,
    val readyQuizId: String? = null
)

class QuizHubViewModel(
    private val quizzes: QuizRepository,
    private val notes: NoteRepository
) : ViewModel() {
    private val _state = MutableStateFlow(QuizHubState())
    val state: StateFlow<QuizHubState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.quizzes.isEmpty(), error = null) }
        viewModelScope.launch {
            val quizResult = quizzes.list()
            val noteResult = notes.list()
            _state.update { s ->
                var next = s.copy(loading = false)
                if (quizResult is ApiResult.Success) next = next.copy(quizzes = quizResult.data)
                if (noteResult is ApiResult.Success) next = next.copy(notes = noteResult.data)
                val failure = (quizResult as? ApiResult.Failure) ?: (noteResult as? ApiResult.Failure)
                if (failure != null) next = next.copy(error = failure.error)
                next
            }
        }
    }

    fun generate(noteId: String) {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true, error = null) }
        viewModelScope.launch {
            when (val result = quizzes.generate(noteId)) {
                is ApiResult.Success -> _state.update { it.copy(generating = false, readyQuizId = result.data.id) }
                is ApiResult.Failure -> _state.update { it.copy(generating = false, error = result.error) }
            }
        }
    }

    fun consumeReady() = _state.update { it.copy(readyQuizId = null) }
}

// ---------- Taking a quiz ----------

data class QuizPlayState(
    val loading: Boolean = true,
    val quiz: QuizDto? = null,
    val selected: Map<Int, Int> = emptyMap(), // question index -> chosen option index
    val submitting: Boolean = false,
    val result: SubmitResponse? = null,
    val error: ApiError? = null
) {
    val questionCount: Int get() = quiz?.questions?.size ?: 0
    val canSubmit: Boolean get() = questionCount > 0 && selected.size == questionCount && !submitting
}

class QuizPlayViewModel(private val repo: QuizRepository) : ViewModel() {
    private val _state = MutableStateFlow(QuizPlayState())
    val state: StateFlow<QuizPlayState> = _state.asStateFlow()

    private var quizId: String? = null

    fun load(id: String) {
        if (quizId == id) return
        quizId = id
        _state.value = QuizPlayState(loading = true)
        viewModelScope.launch {
            when (val result = repo.get(id)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, quiz = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun select(question: Int, option: Int) {
        if (_state.value.result != null) return // locked after submitting
        _state.update { it.copy(selected = it.selected + (question to option)) }
    }

    fun submit() {
        val s = _state.value
        val id = quizId ?: return
        if (!s.canSubmit) return
        val answers = List(s.questionCount) { index -> s.selected.getValue(index) }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.submit(id, answers)) {
                is ApiResult.Success -> {
                    Log.i("QuizPlayViewModel", "Quiz submitted: ${result.data.score}/${result.data.total}")
                    _state.update { it.copy(submitting = false, result = result.data) }
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    fun retake() = _state.update { it.copy(selected = emptyMap(), result = null, error = null) }
}
