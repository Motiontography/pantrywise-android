package com.pantrywise

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pantrywise.nfc.NfcManager
import com.pantrywise.ui.navigation.PantryNavHost
import com.pantrywise.ui.theme.PantryWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var nfcManager: NfcManager

    // Callback for NFC intent handling
    private var nfcIntentCallback: ((Intent) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle NFC intent that launched the activity
        if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            // Will be handled after nfcManager is initialized via Hilt
            intent?.let { handleNfcIntent(it) }
        }

        setContent {
            PantryWiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantryNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC foreground dispatch
        nfcManager.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        // Disable NFC foreground dispatch
        nfcManager.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Handle NFC intent
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            handleNfcIntent(intent)
        }
    }

    private fun handleNfcIntent(intent: Intent) {
        // Pass the intent to any registered callback (e.g., from NfcViewModel)
        nfcIntentCallback?.invoke(intent)
    }

    /**
     * Register a callback for NFC intents
     */
    fun setNfcIntentCallback(callback: ((Intent) -> Unit)?) {
        nfcIntentCallback = callback
    }
}
