package com.routinepulse.app.ui

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.routinepulse.app.BuildConfig

sealed class GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult()
    data object Cancelled : GoogleSignInResult()
    data object NotConfigured : GoogleSignInResult()
    data object Failed : GoogleSignInResult()
}

/** Shows Google's account picker (Credential Manager) and returns the ID token the server will verify. */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    suspend fun getIdToken(activityContext: Context): GoogleSignInResult {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) return GoogleSignInResult.NotConfigured
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleSignInResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                GoogleSignInResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager error: ${e.type} ${e.message}")
            GoogleSignInResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Could not parse the Google ID token", e)
            GoogleSignInResult.Failed
        }
    }
}
