package com.pantrywise.services

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of parsing an expiration date
 */
data class ParsedExpirationDate(
    val date: Date,
    val originalText: String,
    val confidence: Float,
    val formatUsed: String
)

/**
 * Service for parsing expiration dates from various formats found on product packaging
 */
@Singleton
class ExpirationDateParser @Inject constructor() {
    companion object {
        private const val TAG = "ExpirationDateParser"

        // Common expiration date prefixes to look for
        private val DATE_PREFIXES = listOf(
            "EXP", "EXPIRES", "EXPIRY", "EXP DATE", "EXPIRATION",
            "BEST BY", "BEST BEFORE", "BB", "BEST IF USED BY",
            "USE BY", "USE BEFORE", "SELL BY",
            "BEST WHEN USED BY", "FRESHEST BY",
            "PACKED ON", "PACK DATE", "MFG", "MFD",
            "PRODUCTION", "PROD"
        )

        // Regex patterns for extracting dates
        private val DATE_PATTERNS = listOf(
            // US formats (MM/DD/YYYY, MM-DD-YYYY)
            DatePattern("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})", "MM/dd/yyyy", 0.9f),
            DatePattern("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2})", "MM/dd/yy", 0.85f),

            // European formats (DD/MM/YYYY, DD-MM-YYYY)
            DatePattern("(\\d{2})[/\\-](\\d{2})[/\\-](\\d{4})", "dd/MM/yyyy", 0.9f),
            DatePattern("(\\d{2})[/\\-](\\d{2})[/\\-](\\d{2})", "dd/MM/yy", 0.8f),

            // ISO format (YYYY-MM-DD)
            DatePattern("(\\d{4})[/\\-](\\d{2})[/\\-](\\d{2})", "yyyy-MM-dd", 0.95f),

            // Month name formats
            DatePattern("(\\d{1,2})\\s+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\s+(\\d{4})", "dd MMM yyyy", 0.95f, true),
            DatePattern("(\\d{1,2})\\s+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\s+(\\d{2})", "dd MMM yy", 0.9f, true),
            DatePattern("(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\s+(\\d{1,2}),?\\s+(\\d{4})", "MMM dd, yyyy", 0.95f, true),
            DatePattern("(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\s+(\\d{1,2}),?\\s+(\\d{2})", "MMM dd, yy", 0.9f, true),
            DatePattern("(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\s+(\\d{4})", "MMM yyyy", 0.85f, true),

            // Full month names
            DatePattern("(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+(\\d{1,2}),?\\s+(\\d{4})", "MMMM dd, yyyy", 0.95f, true),
            DatePattern("(\\d{1,2})\\s+(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+(\\d{4})", "dd MMMM yyyy", 0.95f, true),

            // Compact formats (MMDDYYYY, YYYYMMDD, DDMMYY)
            DatePattern("(\\d{8})", "yyyyMMdd", 0.7f),
            DatePattern("(\\d{6})", "MMddyy", 0.65f),
            DatePattern("(\\d{6})", "yyMMdd", 0.6f),

            // Julian date (YDDD where Y is last digit of year, DDD is day of year)
            DatePattern("([0-9])([0-3][0-9][0-9])", "julian", 0.6f),

            // Month/Year only formats
            DatePattern("(\\d{1,2})[/\\-](\\d{4})", "MM/yyyy", 0.8f),
            DatePattern("(\\d{1,2})[/\\-](\\d{2})", "MM/yy", 0.7f),

            // Day.Month.Year (European dot separator)
            DatePattern("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})", "dd.MM.yyyy", 0.9f),
            DatePattern("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2})", "dd.MM.yy", 0.85f),

            // With time
            DatePattern("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})\\s+\\d{1,2}:\\d{2}", "MM/dd/yyyy HH:mm", 0.95f),
        )
    }

    private data class DatePattern(
        val regex: String,
        val dateFormat: String,
        val baseConfidence: Float,
        val caseInsensitive: Boolean = false
    )

    /**
     * Parse expiration date from raw text
     * Returns the most likely date with confidence score
     */
    fun parseExpirationDate(text: String): ParsedExpirationDate? {
        val normalizedText = normalizeText(text)
        val candidates = mutableListOf<ParsedExpirationDate>()

        // First, look for dates with prefixes (higher confidence)
        for (prefix in DATE_PREFIXES) {
            val prefixIndex = normalizedText.indexOf(prefix, ignoreCase = true)
            if (prefixIndex != -1) {
                // Extract text after prefix
                val afterPrefix = normalizedText.substring(prefixIndex + prefix.length).take(30).trim()
                val dateWithPrefix = tryParseDateFromPatterns(afterPrefix, confidenceBoost = 0.1f)
                dateWithPrefix?.let { candidates.add(it) }
            }
        }

        // Also look for standalone dates
        val standaloneDate = tryParseDateFromPatterns(normalizedText)
        standaloneDate?.let { candidates.add(it) }

        // Return the highest confidence result
        return candidates
            .filter { isReasonableExpirationDate(it.date) }
            .maxByOrNull { it.confidence }
    }

    /**
     * Find all potential expiration dates in text
     */
    fun findAllPotentialDates(text: String): List<ParsedExpirationDate> {
        val normalizedText = normalizeText(text)
        val results = mutableListOf<ParsedExpirationDate>()

        for (pattern in DATE_PATTERNS) {
            val options = if (pattern.caseInsensitive) {
                setOf(RegexOption.IGNORE_CASE)
            } else {
                emptySet()
            }

            val regex = Regex(pattern.regex, options)
            val matches = regex.findAll(normalizedText)

            for (match in matches) {
                val parsed = parseMatchToDate(match.value, pattern)
                if (parsed != null && isReasonableExpirationDate(parsed.date)) {
                    results.add(parsed)
                }
            }
        }

        return results.distinctBy { it.date.time }
    }

    /**
     * Try to parse a date from the given text using known patterns
     */
    private fun tryParseDateFromPatterns(text: String, confidenceBoost: Float = 0f): ParsedExpirationDate? {
        for (pattern in DATE_PATTERNS) {
            val options = if (pattern.caseInsensitive) {
                setOf(RegexOption.IGNORE_CASE)
            } else {
                emptySet()
            }

            val regex = Regex(pattern.regex, options)
            val match = regex.find(text)

            if (match != null) {
                val parsed = parseMatchToDate(match.value, pattern)
                if (parsed != null) {
                    return parsed.copy(
                        confidence = (parsed.confidence + confidenceBoost).coerceAtMost(1f)
                    )
                }
            }
        }
        return null
    }

    /**
     * Parse matched text into a date using the specified format
     */
    private fun parseMatchToDate(text: String, pattern: DatePattern): ParsedExpirationDate? {
        return try {
            if (pattern.dateFormat == "julian") {
                parseJulianDate(text)
            } else {
                val sdf = SimpleDateFormat(pattern.dateFormat, Locale.US)
                sdf.isLenient = false

                // Clean up the text for parsing
                val cleanText = text
                    .replace("-", "/")
                    .replace(".", "/")
                    .trim()

                val date = sdf.parse(cleanText)
                if (date != null) {
                    // Adjust for 2-digit years
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    val year = calendar.get(Calendar.YEAR)

                    // If year is before 1970, assume it's 2000s
                    if (year < 1970) {
                        calendar.set(Calendar.YEAR, year + 100)
                    }
                    // If year is > 100 years from now, it's probably wrong
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    if (year > currentYear + 50) {
                        calendar.set(Calendar.YEAR, year - 100)
                    }

                    ParsedExpirationDate(
                        date = calendar.time,
                        originalText = text,
                        confidence = pattern.baseConfidence,
                        formatUsed = pattern.dateFormat
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse '$text' with format ${pattern.dateFormat}: ${e.message}")
            null
        }
    }

    /**
     * Parse Julian date format (YDDD)
     */
    private fun parseJulianDate(text: String): ParsedExpirationDate? {
        if (text.length != 4) return null

        return try {
            val yearDigit = text[0].toString().toInt()
            val dayOfYear = text.substring(1).toInt()

            if (dayOfYear < 1 || dayOfYear > 366) return null

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val currentDecade = (currentYear / 10) * 10
            val year = currentDecade + yearDigit

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear)

            ParsedExpirationDate(
                date = calendar.time,
                originalText = text,
                confidence = 0.6f,
                formatUsed = "julian"
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalize text for parsing
     */
    private fun normalizeText(text: String): String {
        return text
            .uppercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[Oo]", "0")  // Common OCR mistake
            .replace("[Ll]", "1")  // Common OCR mistake
            .replace("I", "1")     // Common OCR mistake
            .replace("S", "5")     // Common OCR mistake (sometimes)
            .trim()
    }

    /**
     * Check if date is reasonable for an expiration date
     */
    private fun isReasonableExpirationDate(date: Date): Boolean {
        val calendar = Calendar.getInstance()
        val now = calendar.time

        // Date shouldn't be more than 6 months in the past
        calendar.add(Calendar.MONTH, -6)
        val minDate = calendar.time

        // Date shouldn't be more than 10 years in the future
        calendar.time = now
        calendar.add(Calendar.YEAR, 10)
        val maxDate = calendar.time

        return date.after(minDate) && date.before(maxDate)
    }

    /**
     * Format a date for display
     */
    fun formatDate(date: Date, includeYear: Boolean = true): String {
        val pattern = if (includeYear) "MMM d, yyyy" else "MMM d"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    /**
     * Get days until expiration (negative if already expired)
     */
    fun getDaysUntilExpiration(date: Date): Int {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)

        val expiration = Calendar.getInstance()
        expiration.time = date
        expiration.set(Calendar.HOUR_OF_DAY, 0)
        expiration.set(Calendar.MINUTE, 0)
        expiration.set(Calendar.SECOND, 0)
        expiration.set(Calendar.MILLISECOND, 0)

        val diffMillis = expiration.timeInMillis - now.timeInMillis
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * Get expiration status text
     */
    fun getExpirationStatusText(date: Date): String {
        val daysUntil = getDaysUntilExpiration(date)
        return when {
            daysUntil < 0 -> "Expired ${-daysUntil} day${if (daysUntil != -1) "s" else ""} ago"
            daysUntil == 0 -> "Expires today"
            daysUntil == 1 -> "Expires tomorrow"
            daysUntil <= 7 -> "Expires in $daysUntil days"
            daysUntil <= 30 -> "Expires in ${daysUntil / 7} week${if (daysUntil >= 14) "s" else ""}"
            daysUntil <= 365 -> "Expires in ${daysUntil / 30} month${if (daysUntil >= 60) "s" else ""}"
            else -> "Expires in ${daysUntil / 365} year${if (daysUntil >= 730) "s" else ""}"
        }
    }
}
