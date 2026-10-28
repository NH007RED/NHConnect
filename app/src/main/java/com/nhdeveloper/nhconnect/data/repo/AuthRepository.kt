package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import com.nhdeveloper.nhconnect.data.local.AppDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class NeedsVerification(val email: String) : AuthState
    data class Ready(val uid: String) : AuthState
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
    private val room: AppDatabase
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    val currentUid: String? get() = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { refreshState() }
    }

    private fun refreshState() {
        val u = auth.currentUser
        _state.value = when {
            u == null -> AuthState.SignedOut
            !u.isEmailVerified -> AuthState.NeedsVerification(u.email ?: "")
            else -> AuthState.Ready(u.uid)
        }
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /**
     * Creates the auth account, then claims the username and writes the public profile in ONE atomic update.
     * If the username is taken, rules reject the whole update and the new account is removed again.
     */
    suspend fun signUp(
        email: String, password: String, displayName: String,
        username: String, country: String, language: String
    ) {
        // The auth listener moves the UI to the "verify email" screen as soon as the account exists, which leaves the
        // sign-up screen and would cancel this coroutine halfway (profile never written, account deleted by the
        // cleanup below). NonCancellable lets the whole sequence finish regardless of navigation.
        withContext(NonCancellable) { signUpInternal(email, password, displayName, username, country, language) }
    }

    private suspend fun signUpInternal(
        email: String, password: String, displayName: String,
        username: String, country: String, language: String
    ) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Account was not created")
        val lower = username.lowercase()
        val profile = mapOf(
            "displayName" to displayName.trim(),
            "username" to username,
            "usernameLower" to lower,
            "country" to country,
            "language" to language,
            "createdAt" to ServerValue.TIMESTAMP
        )
        // Two ordered steps: claim the username first (rules reject it if taken), then write the profile,
        // whose rules check that the claim belongs to this uid.
        try {
            db.getReference("usernames/$lower").setValue(user.uid).await()
            db.getReference("users/${user.uid}").setValue(profile).await()
        } catch (t: Throwable) {
            runCatching { db.getReference("usernames/$lower").removeValue().await() }
            runCatching { user.delete().await() }
            throw AppException("That username is not available. Please choose another one.")
        }
        runCatching { user.sendEmailVerification().await() }
    }

    suspend fun resendVerification() { auth.currentUser?.sendEmailVerification()?.await() }

    /** Reloads the account and refreshes the ID token so the `email_verified` claim used by the rules is current. */
    suspend fun checkVerified(): Boolean {
        val u = auth.currentUser ?: return false
        u.reload().await()
        if (u.isEmailVerified) {
            u.getIdToken(true).await()
            refreshState()
            return true
        }
        return false
    }

    suspend fun sendPasswordReset(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }

    suspend fun saveToken(uid: String, token: String) {
        db.getReference("deviceTokens/$uid/$token").setValue(true).await()
    }

    suspend fun registerPushToken() {
        val uid = currentUid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        saveToken(uid, token)
    }

    suspend fun logout() {
        val uid = currentUid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                db.getReference("deviceTokens/$uid/$token").removeValue()
            }
            db.getReference("presence/$uid").setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        }
        room.messages().clearAll(); room.messages().clearMedia(); room.messages().clearShares()
        auth.signOut()
    }

    /**
     * Re-authenticates, removes this device's token, then deletes the Auth user.
     * The `onUserDeleted` Cloud Function removes profile, username, chats, receipts and tokens (see functions/index.js).
     */
    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: error("No email on account")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            db.getReference("deviceTokens/${user.uid}/$token").removeValue().await()
            FirebaseMessaging.getInstance().deleteToken().await()
        }
        room.messages().clearAll(); room.messages().clearMedia(); room.messages().clearShares()
        user.delete().await()
    }
}

class AppException(message: String) : Exception(message)

object ErrorText {
    fun friendly(t: Throwable): String = when (t) {
        is AppException -> t.message ?: "Something went wrong"
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 8 characters."
        is FirebaseAuthUserCollisionException -> "An account with this email already exists."
        is FirebaseAuthInvalidUserException -> "No account found for this email."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        is FirebaseAuthRecentLoginRequiredException -> "Please sign in again and retry."
        is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }
}
