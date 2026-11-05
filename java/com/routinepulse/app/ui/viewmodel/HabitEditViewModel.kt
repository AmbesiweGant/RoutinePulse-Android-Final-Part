package com.routinepulse.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.model.Frequency
import com.routinepulse.app.data.model.HabitRequest
import com.routinepulse.app.data.model.Priority
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.HabitRepository
import com.routinepulse.app.domain.FieldError
import com.routinepulse.app.domain.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitFormState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val isEdit: Boolean = false,
    val title: String = "",
    val frequency: Frequency = Frequency.DAILY,
    val customDays: Set<Int> = emptySet(), // ISO weekdays 1 (Mon) .. 7 (Sun)
    val reminderTime: String? = "07:30",
    val priority: Priority = Priority.MEDIUM,
    val titleError: FieldError? = null,
    val daysError: FieldError? = null,
    val error: ApiError? = null,
    val saved: Boolean = false
)

/** Create / edit form for a habit (Mockup 3). */
class HabitEditViewModel(private val repo: HabitRepository) : ViewModel() {
    private val _state = MutableStateFlow(HabitFormState())
    val state: StateFlow<HabitFormState> = _state.asStateFlow()

    private var habitId: String? = null
    private var initialised = false

    /** Call once with the habit id to edit, or null for a new habit. */
    fun load(id: String?) {
        if (initialised) return
        initialised = true
        habitId = id
        if (id == null) return
        _state.update { it.copy(loading = true, isEdit = true) }
        viewModelScope.launch {
            when (val result = repo.get(id, LocalDate.now().toString())) {
                is ApiResult.Success -> {
                    val h = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            title = h.title,
                            frequency = Frequency.fromWire(h.frequency),
                            customDays = (h.customDays ?: emptyList()).toSet(),
                            reminderTime = h.reminderTime,
                            priority = Priority.fromWire(h.priority)
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v, titleError = null, error = null) }
    fun onFrequency(v: Frequency) = _state.update { it.copy(frequency = v, daysError = null) }
    fun onPriority(v: Priority) = _state.update { it.copy(priority = v) }
    fun onReminder(v: String?) = _state.update { it.copy(reminderTime = v) }
    fun toggleDay(day: Int) = _state.update {
        val days = if (day in it.customDays) it.customDays - day else it.customDays + day
        it.copy(customDays = days, daysError = null)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val titleError = Validators.habitTitle(s.title)
        val daysError = Validators.customDays(s.frequency == Frequency.CUSTOM, s.customDays)
        if (titleError != null || daysError != null) {
            _state.update { it.copy(titleError = titleError, daysError = daysError) }
            return
        }
        val request = HabitRequest(
            title = s.title.trim(),
            frequency = s.frequency.wire,
            customDays = if (s.frequency == Frequency.CUSTOM) s.customDays.sorted() else emptyList(),
            reminderTime = s.reminderTime,
            priority = s.priority.wire
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val date = LocalDate.now().toString()
            val id = habitId
            val result = if (id == null) repo.create(request, date) else repo.update(id, request, date)
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }
}
