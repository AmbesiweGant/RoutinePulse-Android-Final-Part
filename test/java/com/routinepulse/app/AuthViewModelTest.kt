package com.routinepulse.app

import com.routinepulse.app.data.remote.ApiError
import com.routinepulse.app.data.remote.ApiResult
import com.routinepulse.app.data.remote.ErrorKind
import com.routinepulse.app.data.repository.AuthRepository
import com.routinepulse.app.domain.FieldError
import com.routinepulse.app.ui.viewmodel.AuthViewModel
import com.routinepulse.app.ui.viewmodel.GoogleProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FakeAuthRepository(var result: ApiResult<Unit> = ApiResult.Success(Unit)) : AuthRepository {
    var loginCalls = 0
    var registerCalls = 0
    var googleCalls = 0
    var lastGoogleToken: String? = null
    override suspend fun login(email: String, password: String): ApiResult<Unit> { loginCalls++; return result }
    override suspend fun register(email: String, password: String, displayName: String): ApiResult<Unit> {
        registerCalls++
        return result
    }
    override suspend fun googleLogin(idToken: String): ApiResult<Unit> {
        googleCalls++
        lastGoogleToken = idToken
        return result
    }
    override suspend fun logout() {}
    override suspend fun deleteAccount(): ApiResult<Unit> = result
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun login_with_empty_fields_shows_errors_and_never_calls_the_api() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.submit()
        assertEquals(FieldError.EMPTY, vm.state.value.emailError)
        assertEquals(FieldError.EMPTY, vm.state.value.passwordError)
        assertEquals(0, repo.loginCalls)
    }

    @Test fun login_with_malformed_email_is_rejected_locally() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onEmailChange("bad-email")
        vm.onPasswordChange("whatever1")
        vm.submit()
        assertEquals(FieldError.INVALID_EMAIL, vm.state.value.emailError)
        assertEquals(0, repo.loginCalls)
    }

    @Test fun registration_rejects_a_weak_password_without_calling_the_api() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.toggleMode()
        vm.onNameChange("Lindiwe")
        vm.onEmailChange("lindiwe@example.com")
        vm.onPasswordChange("onlyletters")
        vm.submit()
        assertEquals(FieldError.PASSWORD_WEAK, vm.state.value.passwordError)
        assertEquals(0, repo.registerCalls)
    }

    @Test fun successful_login_calls_the_repository_and_clears_the_password() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onEmailChange("lindiwe@example.com")
        vm.onPasswordChange("Study2026")
        vm.submit()
        assertEquals(1, repo.loginCalls)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.apiError)
        assertEquals("", vm.state.value.password)
    }

    @Test fun successful_registration_calls_register_not_login() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.toggleMode()
        vm.onNameChange("Lindiwe")
        vm.onEmailChange("lindiwe@example.com")
        vm.onPasswordChange("Study2026")
        vm.submit()
        assertEquals(1, repo.registerCalls)
        assertEquals(0, repo.loginCalls)
    }

    @Test fun failed_login_surfaces_the_server_message() {
        val repo = FakeAuthRepository(
            ApiResult.Failure(ApiError(ErrorKind.UNAUTHORIZED, "Invalid email or password"))
        )
        val vm = AuthViewModel(repo)
        vm.onEmailChange("lindiwe@example.com")
        vm.onPasswordChange("WrongPass1")
        vm.submit()
        assertFalse(vm.state.value.loading)
        assertEquals(ErrorKind.UNAUTHORIZED, vm.state.value.apiError?.kind)
        assertEquals("Invalid email or password", vm.state.value.apiError?.message)
    }

    @Test fun network_failure_is_reported_as_a_network_error() {
        val repo = FakeAuthRepository(ApiResult.Failure(ApiError(ErrorKind.NETWORK)))
        val vm = AuthViewModel(repo)
        vm.onEmailChange("lindiwe@example.com")
        vm.onPasswordChange("Study2026")
        vm.submit()
        assertEquals(ErrorKind.NETWORK, vm.state.value.apiError?.kind)
    }

    @Test fun switching_mode_clears_previous_errors() {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.submit() // produces validation errors
        vm.toggleMode()
        assertNull(vm.state.value.emailError)
        assertNull(vm.state.value.passwordError)
        assertEquals(true, vm.state.value.registerMode)
    }

    // ---------------- Single sign-on (Google) ----------------

    @Test fun google_token_is_sent_to_the_repository_and_signs_in() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onGoogleToken("token-123")
        assertEquals(1, repo.googleCalls)
        assertEquals("token-123", repo.lastGoogleToken)
        assertEquals(0, repo.loginCalls) // no password involved
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.apiError)
    }

    @Test fun a_rejected_google_token_shows_the_servers_message() {
        val repo = FakeAuthRepository(ApiResult.Failure(ApiError(ErrorKind.UNAUTHORIZED, "Google sign-in failed. Please try again.")))
        val vm = AuthViewModel(repo)
        vm.onGoogleToken("bad-token")
        assertEquals(ErrorKind.UNAUTHORIZED, vm.state.value.apiError?.kind)
        assertFalse(vm.state.value.loading)
    }

    @Test fun google_problems_are_reported_without_calling_the_api() {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onGoogleProblem(GoogleProblem.NOT_CONFIGURED)
        assertEquals(GoogleProblem.NOT_CONFIGURED, vm.state.value.googleProblem)
        assertEquals(0, repo.googleCalls)
        vm.toggleMode() // switching screens clears the message
        assertNull(vm.state.value.googleProblem)
    }
}
