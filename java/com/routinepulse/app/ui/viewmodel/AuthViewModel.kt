package com.routinepulse.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.repository.AuthRepository
import com.routinepulse.app.domain.FieldError
import com.routinepulse.app.domain.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the Google button could not complete (the UI turns these into messages). */
enum class GoogleProblem { NOT_CONFIGURED, FAILED }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val registerMode: Boolean = false,
    val loading: Boolean = false,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val nameError: FieldError? = null,
    val apiError: ApiError? = null,
    val googleProblem: GoogleProblem? = null
)

/**
 * Handles both login and registration. On success the repository stores the session token;
 * the navigation layer notices the token and moves to the dashboard.
 */
class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, emailError = null, apiError = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, apiError = null) }
    fun onNameChange(v: String) = _state.update { it.copy(displayName = v, nameError = null, apiError = null) }

    fun toggleMode() = _state.update {
        it.copy(
            registerMode = !it.registerMode,
            emailError = null, passwordError = null, nameError = null, apiError = null, googleProblem = null
        )
    }

    /** Called with the Google ID token; the server verifies it and returns our own session. */
    fun onGoogleToken(idToken: String) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, apiError = null, googleProblem = null) }
        viewModelScope.launch {
            when (val result = repo.googleLogin(idToken)) {
                is ApiResult.Success -> {
                    Log.i("AuthViewModel", "Signed in with Google")
                    _state.update { it.copy(loading = false) }
                }
                is ApiResult.Failure -> {
                    Log.w("AuthViewModel", "Google sign-in failed: ${result.error.kind}")
                    _state.update { it.copy(loading = false, apiError = result.error) }
                }
            }
        }
    }

    fun onGoogleProblem(problem: GoogleProblem) = _state.update { it.copy(googleProblem = problem, apiError = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return

        val emailError = Validators.email(s.email)
        val passwordError = if (s.registerMode) Validators.newPassword(s.password) else Validators.loginPassword(s.password)
        val nameError = if (s.registerMode) Validators.displayName(s.displayName) else null
        if (emailError != null || passwordError != null || nameError != null) {
            Log.d("AuthViewModel", "Validation failed: email=$emailError password=$passwordError name=$nameError")
            _state.update { it.copy(emailError = emailError, passwordError = passwordError, nameError = nameError) }
            return
        }

        _state.update { it.copy(loading = true, apiError = null) }
        viewModelScope.launch {
            val result = if (s.registerMode) repo.register(s.email, s.password, s.displayName) else repo.login(s.email, s.password)
            when (result) {
                is ApiResult.Success -> {
                    Log.i("AuthViewModel", if (s.registerMode) "Registered" else "Logged in")
                    _state.update { it.copy(loading = false, password = "") }
                }
                is ApiResult.Failure -> {
                    Log.w("AuthViewModel", "Auth failed: ${result.error.kind}")
                    _state.update { it.copy(loading = false, apiError = result.error) }
                }
            }
        }
    }
}
