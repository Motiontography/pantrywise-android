package com.pantrywise.services

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class FirebaseAuthService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val auth: FirebaseAuth = Firebase.auth
    private var googleSignInClient: GoogleSignInClient? = null

    // Current user as Flow
    val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Current user (synchronous)
    val currentUserSync: AuthUser?
        get() = auth.currentUser?.toAuthUser()

    // Check if user is signed in
    val isSignedIn: Boolean
        get() = auth.currentUser != null

    // Initialize Google Sign-In
    fun initGoogleSignIn(webClientId: String) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    // Get Google Sign-In intent
    fun getGoogleSignInIntent(): Intent? {
        return googleSignInClient?.signInIntent
    }

    // Handle Google Sign-In result
    suspend fun handleGoogleSignInResult(idToken: String): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user.toAuthUser())
            } else {
                AuthResult.Error("Sign-in failed: No user returned")
            }
        } catch (e: Exception) {
            AuthResult.Error("Sign-in failed: ${e.localizedMessage}")
        }
    }

    // Sign in with email and password
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user.toAuthUser())
            } else {
                AuthResult.Error("Sign-in failed: No user returned")
            }
        } catch (e: Exception) {
            AuthResult.Error("Sign-in failed: ${e.localizedMessage}")
        }
    }

    // Create account with email and password
    suspend fun createAccountWithEmail(email: String, password: String, displayName: String?): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                // Update display name if provided
                displayName?.let { name ->
                    val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                        this.displayName = name
                    }
                    user.updateProfile(profileUpdates).await()
                }
                AuthResult.Success(user.toAuthUser())
            } else {
                AuthResult.Error("Account creation failed: No user returned")
            }
        } catch (e: Exception) {
            AuthResult.Error("Account creation failed: ${e.localizedMessage}")
        }
    }

    // Sign in anonymously
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user.toAuthUser())
            } else {
                AuthResult.Error("Anonymous sign-in failed")
            }
        } catch (e: Exception) {
            AuthResult.Error("Anonymous sign-in failed: ${e.localizedMessage}")
        }
    }

    // Send password reset email
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success(AuthUser("", email, null, null, false))
        } catch (e: Exception) {
            AuthResult.Error("Failed to send reset email: ${e.localizedMessage}")
        }
    }

    // Sign out
    fun signOut() {
        auth.signOut()
        googleSignInClient?.signOut()
    }

    // Delete account
    suspend fun deleteAccount(): AuthResult {
        return try {
            auth.currentUser?.delete()?.await()
            AuthResult.Success(AuthUser("", null, null, null, false))
        } catch (e: Exception) {
            AuthResult.Error("Failed to delete account: ${e.localizedMessage}")
        }
    }

    // Convert FirebaseUser to AuthUser
    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            isAnonymous = isAnonymous
        )
    }
}
