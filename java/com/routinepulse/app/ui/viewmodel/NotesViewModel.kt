package com.routinepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.NoteRepository
import com.routinepulse.app.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val loading: Boolean = true,
    val notes: List<NoteDto> = emptyList(),
    val query: String = "",
    val error: ApiError? = null
)

class NotesViewModel(
    private val repo: NoteRepository,
    private val sync: SyncStatus? = null
) : ViewModel() {
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    init {
        // Reload when a background sync brings in notes from another device.
        sync?.let { s -> viewModelScope.launch { s.completed.collect { load() } } }
    }

    fun load() {
        _state.update { it.copy(loading = it.notes.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = repo.list()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, notes = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
}
