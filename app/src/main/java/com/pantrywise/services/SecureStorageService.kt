package com.pantrywise.services

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage service using EncryptedSharedPreferences
 * for storing sensitive data like API keys
 */
@Singleton
class SecureStorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureStorageService"
        private const val PREFS_NAME = "pantrywise_secure_prefs"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences", e)
            // Fallback to regular SharedPreferences in case of error
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Save the OpenAI API key securely
     */
    fun saveApiKey(apiKey: String) {
        securePrefs.edit()
            .putString(KEY_OPENAI_API_KEY, apiKey)
            .apply()
        Log.d(TAG, "API key saved securely")
    }

    /**
     * Retrieve the OpenAI API key
     */
    fun getApiKey(): String? {
        return securePrefs.getString(KEY_OPENAI_API_KEY, null)
    }

    /**
     * Check if an API key is stored
     */
    fun hasApiKey(): Boolean {
        val key = getApiKey()
        return !key.isNullOrEmpty() && key.startsWith("sk-")
    }

    /**
     * Delete the stored API key
     */
    fun deleteApiKey() {
        securePrefs.edit()
            .remove(KEY_OPENAI_API_KEY)
            .apply()
        Log.d(TAG, "API key deleted")
    }
}
