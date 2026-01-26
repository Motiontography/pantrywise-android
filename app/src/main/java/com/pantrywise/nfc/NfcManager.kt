package com.pantrywise.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an NFC scan operation
 */
data class NfcScanResult(
    val productId: String?,
    val barcode: String?,
    val rawPayload: String,
    val tagType: NfcTagType
)

enum class NfcTagType {
    PANTRY_WISE,
    URL,
    TEXT,
    UNKNOWN
}

/**
 * NFC operation errors
 */
sealed class NfcError(val message: String) {
    object NotSupported : NfcError("NFC is not supported on this device")
    object NotEnabled : NfcError("NFC is disabled. Please enable it in settings.")
    object TagNotWritable : NfcError("This NFC tag is not writable or is locked")
    object InsufficientCapacity : NfcError("This NFC tag does not have enough capacity")
    object EmptyTag : NfcError("This NFC tag is empty")
    class ReadFailed(reason: String) : NfcError("Failed to read NFC tag: $reason")
    class WriteFailed(reason: String) : NfcError("Failed to write to NFC tag: $reason")
    object InvalidPayload : NfcError("Invalid NFC tag data format")
}

/**
 * NFC operation result
 */
sealed class NfcResult<out T> {
    data class Success<T>(val data: T) : NfcResult<T>()
    data class Error(val error: NfcError) : NfcResult<Nothing>()
}

/**
 * Manager class for NFC reading and writing operations
 */
@Singleton
class NfcManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NfcManager"
        const val PANTRY_WISE_SCHEME = "pantrywise://"
        const val MIME_TYPE = "application/vnd.mypantrybuddy.item"
    }

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isWriting = MutableStateFlow(false)
    val isWriting: StateFlow<Boolean> = _isWriting.asStateFlow()

    private val _lastScanResult = MutableStateFlow<NfcScanResult?>(null)
    val lastScanResult: StateFlow<NfcScanResult?> = _lastScanResult.asStateFlow()

    private val _lastError = MutableStateFlow<NfcError?>(null)
    val lastError: StateFlow<NfcError?> = _lastError.asStateFlow()

    // Pending write data
    private var pendingWriteProductId: String? = null
    private var pendingWriteProductName: String? = null
    private var pendingWriteBarcode: String? = null

    /**
     * Check if NFC is available on this device
     */
    val isNfcAvailable: Boolean
        get() = nfcAdapter != null

    /**
     * Check if NFC is enabled
     */
    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    /**
     * Enable foreground dispatch for NFC (call in Activity.onResume)
     */
    fun enableForegroundDispatch(activity: Activity) {
        if (!isNfcAvailable) return

        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Set up intent filters for NFC
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try {
                    addDataType(MIME_TYPE)
                } catch (e: IntentFilter.MalformedMimeTypeException) {
                    Log.e(TAG, "Malformed MIME type", e)
                }
            },
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                addDataScheme("http")
                addDataScheme("https")
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )

        val techLists = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )

        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    /**
     * Disable foreground dispatch for NFC (call in Activity.onPause)
     */
    fun disableForegroundDispatch(activity: Activity) {
        if (!isNfcAvailable) return
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Start scanning mode
     */
    fun startScanning() {
        _isScanning.value = true
        _isWriting.value = false
        _lastError.value = null
        pendingWriteProductId = null
    }

    /**
     * Stop scanning mode
     */
    fun stopScanning() {
        _isScanning.value = false
    }

    /**
     * Start write mode with product data
     */
    fun startWriteMode(productId: String, productName: String?, barcode: String?) {
        _isWriting.value = true
        _isScanning.value = false
        _lastError.value = null
        pendingWriteProductId = productId
        pendingWriteProductName = productName
        pendingWriteBarcode = barcode
    }

    /**
     * Stop write mode
     */
    fun stopWriteMode() {
        _isWriting.value = false
        pendingWriteProductId = null
        pendingWriteProductName = null
        pendingWriteBarcode = null
    }

    /**
     * Handle an NFC intent
     */
    suspend fun handleNfcIntent(intent: Intent): NfcResult<NfcScanResult> = withContext(Dispatchers.IO) {
        if (!isNfcAvailable) {
            return@withContext NfcResult.Error(NfcError.NotSupported)
        }

        if (!isNfcEnabled) {
            return@withContext NfcResult.Error(NfcError.NotEnabled)
        }

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            ?: return@withContext NfcResult.Error(NfcError.ReadFailed("No tag found"))

        // If we're in write mode, write to the tag
        if (_isWriting.value && pendingWriteProductId != null) {
            return@withContext writeToTag(tag)
        }

        // Otherwise, read from the tag
        return@withContext readFromTag(intent, tag)
    }

    /**
     * Read data from an NFC tag
     */
    private fun readFromTag(intent: Intent, tag: Tag): NfcResult<NfcScanResult> {
        return try {
            // Try to read NDEF messages from intent
            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)

            if (rawMessages != null && rawMessages.isNotEmpty()) {
                val message = rawMessages[0] as NdefMessage
                val result = parseNdefMessage(message)
                _lastScanResult.value = result
                NfcResult.Success(result)
            } else {
                // Try to read directly from tag
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    val message = ndef.cachedNdefMessage
                    ndef.close()

                    if (message != null) {
                        val result = parseNdefMessage(message)
                        _lastScanResult.value = result
                        NfcResult.Success(result)
                    } else {
                        _lastError.value = NfcError.EmptyTag
                        NfcResult.Error(NfcError.EmptyTag)
                    }
                } else {
                    _lastError.value = NfcError.EmptyTag
                    NfcResult.Error(NfcError.EmptyTag)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading NFC tag", e)
            val error = NfcError.ReadFailed(e.message ?: "Unknown error")
            _lastError.value = error
            NfcResult.Error(error)
        }
    }

    /**
     * Write product data to an NFC tag
     */
    private fun writeToTag(tag: Tag): NfcResult<NfcScanResult> {
        val productId = pendingWriteProductId ?: return NfcResult.Error(NfcError.InvalidPayload)

        return try {
            val message = createNdefMessage(productId, pendingWriteProductName, pendingWriteBarcode)
                ?: return NfcResult.Error(NfcError.InvalidPayload)

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()

                if (!ndef.isWritable) {
                    ndef.close()
                    _lastError.value = NfcError.TagNotWritable
                    return NfcResult.Error(NfcError.TagNotWritable)
                }

                if (message.toByteArray().size > ndef.maxSize) {
                    ndef.close()
                    _lastError.value = NfcError.InsufficientCapacity
                    return NfcResult.Error(NfcError.InsufficientCapacity)
                }

                ndef.writeNdefMessage(message)
                ndef.close()

                // Create result
                val result = NfcScanResult(
                    productId = productId,
                    barcode = pendingWriteBarcode,
                    rawPayload = "$PANTRY_WISE_SCHEME/product/$productId",
                    tagType = NfcTagType.PANTRY_WISE
                )

                _isWriting.value = false
                pendingWriteProductId = null
                pendingWriteProductName = null
                pendingWriteBarcode = null

                NfcResult.Success(result)
            } else {
                // Try to format the tag
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()

                    val result = NfcScanResult(
                        productId = productId,
                        barcode = pendingWriteBarcode,
                        rawPayload = "$PANTRY_WISE_SCHEME/product/$productId",
                        tagType = NfcTagType.PANTRY_WISE
                    )

                    _isWriting.value = false
                    pendingWriteProductId = null

                    NfcResult.Success(result)
                } else {
                    _lastError.value = NfcError.TagNotWritable
                    NfcResult.Error(NfcError.TagNotWritable)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to NFC tag", e)
            val error = NfcError.WriteFailed(e.message ?: "IO Error")
            _lastError.value = error
            NfcResult.Error(error)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to NFC tag", e)
            val error = NfcError.WriteFailed(e.message ?: "Unknown error")
            _lastError.value = error
            NfcResult.Error(error)
        }
    }

    /**
     * Create an NDEF message for a product
     */
    private fun createNdefMessage(productId: String, productName: String?, barcode: String?): NdefMessage? {
        val records = mutableListOf<NdefRecord>()

        // Create PantryWise URL record
        var urlString = "${PANTRY_WISE_SCHEME}product/$productId"
        if (!barcode.isNullOrEmpty()) {
            urlString += "?barcode=$barcode"
        }

        // URI record
        val uriRecord = NdefRecord.createUri(urlString)
        records.add(uriRecord)

        // Add MIME type record for app association
        val mimeRecord = NdefRecord.createMime(
            MIME_TYPE,
            productId.toByteArray(Charset.forName("UTF-8"))
        )
        records.add(mimeRecord)

        // Add text record with product name if available
        if (!productName.isNullOrEmpty()) {
            val textRecord = createTextRecord(productName)
            records.add(textRecord)
        }

        return if (records.isNotEmpty()) {
            NdefMessage(records.toTypedArray())
        } else {
            null
        }
    }

    /**
     * Create a text NDEF record
     */
    private fun createTextRecord(text: String): NdefRecord {
        val language = "en"
        val languageBytes = language.toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))

        val payload = ByteArray(1 + languageBytes.size + textBytes.size)
        payload[0] = languageBytes.size.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    /**
     * Parse an NDEF message and extract PantryWise data
     */
    private fun parseNdefMessage(message: NdefMessage): NfcScanResult {
        var productId: String? = null
        var barcode: String? = null
        var rawPayload = ""
        var tagType = NfcTagType.UNKNOWN

        for (record in message.records) {
            when (record.tnf) {
                NdefRecord.TNF_WELL_KNOWN -> {
                    if (record.type.contentEquals(NdefRecord.RTD_URI)) {
                        // URI record
                        val uri = parseUriRecord(record)
                        rawPayload = uri

                        if (uri.startsWith(PANTRY_WISE_SCHEME)) {
                            tagType = NfcTagType.PANTRY_WISE

                            // Parse product ID from path
                            val pathStart = uri.indexOf("/product/")
                            if (pathStart >= 0) {
                                val idStart = pathStart + "/product/".length
                                val idEnd = uri.indexOf("?", idStart).takeIf { it >= 0 } ?: uri.length
                                productId = uri.substring(idStart, idEnd)
                            }

                            // Parse barcode from query
                            val barcodeParam = "?barcode="
                            val barcodeStart = uri.indexOf(barcodeParam)
                            if (barcodeStart >= 0) {
                                barcode = uri.substring(barcodeStart + barcodeParam.length)
                            }
                        } else {
                            tagType = NfcTagType.URL
                            // Try to extract barcode from URL
                            val barcodeRegex = "\\d{8,14}".toRegex()
                            barcode = barcodeRegex.find(uri)?.value
                        }
                    } else if (record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        // Text record
                        val text = parseTextRecord(record)
                        if (tagType == NfcTagType.UNKNOWN) {
                            tagType = NfcTagType.TEXT
                            rawPayload = text
                        }

                        // Check if text is a UUID
                        if (productId == null && text.matches("[0-9a-fA-F-]{36}".toRegex())) {
                            productId = text
                        }

                        // Check if text is a barcode
                        if (barcode == null && text.matches("\\d{8,14}".toRegex())) {
                            barcode = text
                        }
                    }
                }

                NdefRecord.TNF_MIME_MEDIA -> {
                    val mimeType = String(record.type, Charset.forName("UTF-8"))
                    if (mimeType == MIME_TYPE) {
                        productId = String(record.payload, Charset.forName("UTF-8"))
                        tagType = NfcTagType.PANTRY_WISE
                    }
                }
            }
        }

        return NfcScanResult(
            productId = productId,
            barcode = barcode,
            rawPayload = rawPayload,
            tagType = tagType
        )
    }

    /**
     * Parse a URI NDEF record
     */
    private fun parseUriRecord(record: NdefRecord): String {
        val payload = record.payload
        if (payload.isEmpty()) return ""

        val prefixByte = payload[0].toInt() and 0xFF
        val prefixes = arrayOf(
            "", "http://www.", "https://www.", "http://", "https://",
            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.",
            "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://",
            "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:",
            "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
            "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
            "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
        )

        val prefix = if (prefixByte < prefixes.size) prefixes[prefixByte] else ""
        return prefix + String(payload, 1, payload.size - 1, Charset.forName("UTF-8"))
    }

    /**
     * Parse a text NDEF record
     */
    private fun parseTextRecord(record: NdefRecord): String {
        val payload = record.payload
        if (payload.isEmpty()) return ""

        val status = payload[0].toInt() and 0xFF
        val languageCodeLength = status and 0x3F
        return String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, Charset.forName("UTF-8"))
    }

    /**
     * Clear the last error
     */
    fun clearError() {
        _lastError.value = null
    }

    /**
     * Clear the last scan result
     */
    fun clearLastScanResult() {
        _lastScanResult.value = null
    }
}
