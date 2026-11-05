package com.routinepulse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.routinepulse.app.AppContainer
import com.routinepulse.app.ui.viewmodel.*

/** Creates ViewModels with their repositories (we use a hand-written factory instead of Hilt). */
class ViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(c.authRepository)
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(c.habitRepository, c.syncCoordinator, c.badgeTracker)
        modelClass.isAssignableFrom(HabitEditViewModel::class.java) -> HabitEditViewModel(c.habitRepository)
        modelClass.isAssignableFrom(NotesViewModel::class.java) -> NotesViewModel(c.noteRepository, c.syncCoordinator)
        modelClass.isAssignableFrom(NoteEditViewModel::class.java) -> NoteEditViewModel(c.noteRepository, c.quizRepository)
        modelClass.isAssignableFrom(QuizHubViewModel::class.java) -> QuizHubViewModel(c.quizRepository, c.noteRepository)
        modelClass.isAssignableFrom(QuizPlayViewModel::class.java) -> QuizPlayViewModel(c.quizRepository)
        modelClass.isAssignableFrom(ProgressViewModel::class.java) ->
            ProgressViewModel(c.habitRepository, c.noteRepository, c.quizRepository, c.badgeTracker)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(c.settingsStore, c.authRepository, c.cloudRepository, c.syncCoordinator, c.pushManager)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
