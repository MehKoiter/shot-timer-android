package com.shottimer.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.shottimer.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Sign-in state exposed to the rest of the app. [SignedIn] carries only the small bits of
 * profile info the UI needs to render (e.g. "Signed in as ...") - callers that need the full
 * `FirebaseUser` (for ID tokens, etc.) should go through [FirebaseAuth.getInstance] directly.
 */
sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String?, val displayName: String?) : AuthState
}

/**
 * Wraps Credential Manager's Google sign-in flow and Firebase Auth. Must be a singleton (via
 * [getInstance], matching [com.shottimer.app.settings.SettingsRepository]'s pattern rather than
 * the stateless-fresh-instance pattern used by `RunRepository`): sign-in state is shared mutable
 * state that every observer (Settings, a future sync layer, etc.) needs to see consistently, so
 * two independently-constructed instances holding their own stale copies would be a bug.
 *
 * [authState] is seeded from whatever `FirebaseAuth` already has cached on disk (so app restarts
 * don't force a re-login) and kept current via an [FirebaseAuth.AuthStateListener], which fires
 * for sign-in, sign-out, and token refresh alike.
 */
class AuthRepository private constructor(context: Context) {
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(firebaseAuth.currentUser.toAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _authState.value = auth.currentUser.toAuthState()
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    /**
     * Launches the Credential Manager "Sign in with Google" flow and, on success, exchanges the
     * returned Google ID token for a signed-in Firebase user. [activityContext] must be an
     * Activity context (not an Application context) - Credential Manager needs it to host the
     * account-picker UI.
     *
     * Never throws: Credential Manager surfaces failure as typed [GetCredentialException]
     * subclasses for perfectly ordinary situations (no Google account on the device, user
     * cancels the picker, Play Services missing/outdated on a de-Googled or unusual device) and
     * this app must stay usable fully offline/signed-out in all of those cases rather than crash.
     * Any exception - not just [GetCredentialException] - is caught and reported as
     * [Result.failure] so a caller (a future ViewModel) can show a message and let the user carry
     * on signed-out.
     */
    suspend fun signIn(activityContext: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val webClientId = activityContext.getString(R.string.default_web_client_id)

            // Try the returning-user path first (only accounts already authorized for this
            // app's OAuth client, no picker shown if there's exactly one). Google's own
            // Credential Manager guidance is to fall back to an unfiltered request - which
            // shows a full account picker - when that finds nothing, since "no account has
            // ever signed into this app before" is the ordinary case for a first sign-in, not
            // an error.
            val response = try {
                credentialManager.getCredential(
                    activityContext,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setServerClientId(webClientId)
                                .setFilterByAuthorizedAccounts(true)
                                .build()
                        )
                        .build()
                )
            } catch (e: NoCredentialException) {
                credentialManager.getCredential(
                    activityContext,
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetGoogleIdOption.Builder()
                                .setServerClientId(webClientId)
                                .setFilterByAuthorizedAccounts(false)
                                .build()
                        )
                        .build()
                )
            }

            val idToken = response.credential.toGoogleIdToken()
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(firebaseCredential).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            // Never swallow coroutine cancellation - rethrow so structured concurrency still
            // works (e.g. the calling ViewModel scope being cleared mid-sign-in).
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out of Firebase and clears Credential Manager's own remembered-credential state.
     * Clearing the latter matters: without it, a subsequent [signIn] call can silently
     * re-authenticate with the same account (no picker shown) rather than genuinely prompting,
     * which is surprising after an explicit "Sign out".
     */
    suspend fun signOut(context: Context) {
        firebaseAuth.signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
    }
}

private fun FirebaseUser?.toAuthState(): AuthState =
    if (this == null) {
        AuthState.SignedOut
    } else {
        AuthState.SignedIn(uid = uid, email = email, displayName = displayName)
    }

/**
 * Extracts the Google ID token from a Credential Manager [Credential]. The credential comes back
 * as a [CustomCredential] whose type tag identifies it as a Google ID token credential;
 * [GoogleIdTokenCredential.createFrom] parses the underlying data bundle into a typed object.
 */
private fun Credential.toGoogleIdToken(): String {
    require(this is CustomCredential && type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type: $type"
    }
    return GoogleIdTokenCredential.createFrom(data).idToken
}
