package com.routinepulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.GoogleSignInHelper
import com.routinepulse.app.ui.GoogleSignInResult
import com.routinepulse.app.ui.viewmodel.AuthViewModel
import com.routinepulse.app.ui.viewmodel.GoogleProblem
import kotlinx.coroutines.launch

/** Mockup 1: login + registration in one screen (REQ-01). Google single sign-on (REQ-02) is the second button. */
@Composable
fun AuthScreen(vm: AuthViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(if (s.registerMode) R.string.auth_title_register else R.string.auth_title_login))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LogoBadge()
            Spacer(Modifier.size(8.dp))

            if (s.registerMode) {
                RpTextField(
                    value = s.displayName,
                    onValueChange = vm::onNameChange,
                    label = stringResource(R.string.field_display_name),
                    error = fieldErrorText(s.nameError)
                )
            }
            RpTextField(
                value = s.email,
                onValueChange = vm::onEmailChange,
                label = stringResource(R.string.field_email),
                keyboardType = KeyboardType.Email,
                error = fieldErrorText(s.emailError)
            )
            RpTextField(
                value = s.password,
                onValueChange = vm::onPasswordChange,
                label = stringResource(R.string.field_password),
                isPassword = true,
                error = fieldErrorText(s.passwordError)
            )
            if (s.registerMode) {
                Text(
                    stringResource(R.string.auth_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            s.apiError?.let { ErrorBanner(errorMessage(it)) }

            PrimaryButton(
                text = stringResource(if (s.registerMode) R.string.auth_button_register else R.string.auth_button_login),
                onClick = vm::submit,
                loading = s.loading
            )

            Text(
                stringResource(R.string.auth_or_continue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Single sign-on (REQ-02): Google shows its account picker, we send the ID token to the API to verify.
            SecondaryButton(
                text = stringResource(R.string.auth_google),
                onClick = {
                    scope.launch {
                        when (val result = GoogleSignInHelper.getIdToken(context)) {
                            is GoogleSignInResult.Token -> vm.onGoogleToken(result.idToken)
                            GoogleSignInResult.Cancelled -> Unit // the user closed the picker - nothing to report
                            GoogleSignInResult.NotConfigured -> vm.onGoogleProblem(GoogleProblem.NOT_CONFIGURED)
                            GoogleSignInResult.Failed -> vm.onGoogleProblem(GoogleProblem.FAILED)
                        }
                    }
                },
                enabled = !s.loading
            )
            s.googleProblem?.let {
                ErrorBanner(
                    stringResource(
                        if (it == GoogleProblem.NOT_CONFIGURED) R.string.auth_google_not_configured else R.string.auth_google_failed
                    )
                )
            }

            TextButton(onClick = vm::toggleMode) {
                Text(stringResource(if (s.registerMode) R.string.auth_switch_to_login else R.string.auth_switch_to_register))
            }
        }
    }
}

@Composable
private fun LogoBadge() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("RP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
}
