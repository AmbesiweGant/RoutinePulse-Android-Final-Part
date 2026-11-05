package com.routinepulse.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.BadgeTracker
import com.routinepulse.app.data.repository.HabitRepository
import com.routinepulse.app.data.sync.SyncStatus
import com.routinepulse.app.domain.Achievements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val habits: List<HabitDto> = emptyList(),
    val error: ApiError? = null,        // full-screen load error
    val actionError: ApiError? = null,  // error from a tap (shown in a snackbar)
    val busyIds: Set<String> = emptySet(),
    val pending: Int = 0,               // changes saved on the phone that are waiting to sync
    val newBadges: Int = 0              // badges unlocked by the last action (shown once)
)

/**
 * Dashboard logic. The habit list comes from the phone's own database, so it works offline; [sync] (optional)
 * triggers background syncing and tells us when to reload, [badges] (optional) celebrates new achievements.
 */
class HomeViewModel(
    private val repo: HabitRepository,
    private val sync: SyncStatus? = null,
    private val badges: BadgeTracker? = null,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        sync?.let { s ->
            s.request() // opening the dashboard is a good moment to sync
            viewModelScope.launch { s.completed.collect { load() } }
            viewModelScope.launch { s.pending.collect { n -> _state.update { it.copy(pending = n) } } }
        }
    }

    fun load() {
        // Only show the big spinner on the very first load; later refreshes keep the list on screen.
        _state.update { it.copy(loading = it.habits.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = repo.list(today().toString())) {
                is ApiResult.Success -> {
                    Log.d("HomeViewModel", "Loaded ${result.data.size} habits")
                    _state.update { it.copy(loading = false, habits = result.data, error = null) }
                    checkBadges(result.data)
                }
                is ApiResult.Failure -> {
                    Log.w("HomeViewModel", "Load failed: ${result.error.kind}")
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }

    /** Ticks or un-ticks a habit for today, then swaps in the updated habit (with its new streak). */
    fun toggle(habit: HabitDto) {
        if (habit.id in _state.value.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + habit.id, actionError = null) }
        viewModelScope.launch {
            when (val result = repo.setCompleted(habit.id, today().toString(), !habit.completedToday)) {
                is ApiResult.Success -> {
                    _state.update { s ->
                        s.copy(
                            habits = s.habits.map { if (it.id == habit.id) result.data else it },
                            busyIds = s.busyIds - habit.id
                        )
                    }
                    checkBadges(_state.value.habits)
                }
                is ApiResult.Failure -> _state.update { it.copy(busyIds = it.busyIds - habit.id, actionError = result.error) }
            }
        }
    }

    fun delete(habit: HabitDto) {
        viewModelScope.launch {
            when (val result = repo.delete(habit.id)) {
                is ApiResult.Success -> _state.update { s -> s.copy(habits = s.habits.filter { it.id != habit.id }) }
                is ApiResult.Failure -> _state.update { it.copy(actionError = result.error) }
            }
        }
    }

    private suspend fun checkBadges(habits: List<HabitDto>) {
        val tracker = badges ?: return
        val result = tracker.evaluate(Achievements.statsFrom(habits, noteCount = 0, quizzes = null, today = today()))
        if (result.newlyEarned.isNotEmpty()) _state.update { it.copy(newBadges = result.newlyEarned.size) }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
    fun consumeNewBadges() = _state.update { it.copy(newBadges = 0) }
}
