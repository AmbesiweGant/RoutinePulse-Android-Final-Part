package com.routinepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.BadgeTracker
import com.routinepulse.app.data.repository.HabitRepository
import com.routinepulse.app.data.repository.NoteRepository
import com.routinepulse.app.data.repository.QuizRepository
import com.routinepulse.app.domain.Achievements
import com.routinepulse.app.domain.Badge
import com.routinepulse.app.domain.ProgressStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ProgressTab { BOARD, BADGES }

data class ProgressUiState(
    val loading: Boolean = true,
    val tab: ProgressTab = ProgressTab.BOARD,
    val habits: List<HabitDto> = emptyList(),
    val stats: ProgressStats = ProgressStats(),
    val earned: Set<Badge> = emptySet(),
    val newlyEarned: List<Badge> = emptyList()
)

/** Streak board (feature 5) and badges (feature 4). Habits and notes are local; quizzes need the network. */
class ProgressViewModel(
    private val habits: HabitRepository,
    private val notes: NoteRepository,
    private val quizzes: QuizRepository,
    private val badges: BadgeTracker,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val habitList = (habits.list(today().toString()) as? ApiResult.Success)?.data ?: emptyList()
            val noteCount = (notes.list() as? ApiResult.Success)?.data?.size ?: 0
            // Offline? Then quiz badges simply are not re-evaluated now (already earned ones are remembered).
            val quizList = (quizzes.list() as? ApiResult.Success)?.data

            val stats = Achievements.statsFrom(habitList, noteCount, quizList, today())
            val result = badges.evaluate(stats)
            _state.update {
                it.copy(
                    loading = false, habits = habitList, stats = stats,
                    earned = result.earned, newlyEarned = result.newlyEarned
                )
            }
        }
    }

    fun selectTab(tab: ProgressTab) = _state.update { it.copy(tab = tab) }
    fun dismissNewBadges() = _state.update { it.copy(newlyEarned = emptyList()) }
}
