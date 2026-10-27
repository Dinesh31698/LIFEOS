package com.lifeos.app.security

import android.util.Log
import com.lifeos.app.data.LifeOsDao

object DataMinimizer {

    private const val TAG = "LIFEOS_MINIMIZER"

    // Regex patterns to detect and strip PII from payload text
    private val PHONE_REGEX = Regex("\\+\\d{10,13}\\b|(?:\\+?\\d{1,3}[- ]?)?(?:\\(\\d{3}\\)[- ]?|\\b\\d{3}[- ]?)\\d{3}[- ]?\\d{4}\\b|\\b\\d{10}\\b|\\b\\d{5}[- ]\\d{5}\\b")
    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PERSONAL_GREETINGS = Regex("(?i)\\b(dear|hi|hello|hey|mr\\.?|mrs\\.?|ms\\.?|prof\\.?|dr\\.?|bhai|sir)\\b(?:\\s+(?:dr\\.?|prof\\.?|mr\\.?|mrs\\.?|ms\\.)?\\s*[a-zA-Z]+)?")
    private val URL_REGEX = Regex("(?i)\\bhttps?://\\S+\\b")
    private val OTP_TOKEN_REGEX = Regex("(?i)\\b(?:otp|code|pin|password|passcode|token)\\s*(?:is|:)?\\s*[a-zA-Z0-9_-]{4,20}\\b")
    private val JWT_TOKEN_REGEX = Regex("(?i)\\b(?:eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9._-]+)?|bearer\\s+[a-zA-Z0-9._-]+)\\b")
    private val SENSITIVE_ID_REGEX = Regex("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b|\\b\\d{3}-\\d{2}-\\d{4}\\b")

    private val API_KEY_REGEX = Regex("(?i)\\b(?:api[_-]?key|secret|client[_-]?secret|access[_-]?token)\\s*[:=]\\s*[a-zA-Z0-9_\\-\\.~]{8,}\\b")
    private val CONVERSATIONAL_NOISE = Regex("(?i)\\b(array|arey|yaar|subah|shaam|dost|macha|bro|dude)\\b[.,!\\s]*")

    /**
     * Strips conversational filler and noise words from text.
     */
    fun stripConversationalNoise(text: String): String {
        return text.replace(CONVERSATIONAL_NOISE, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Strips PII (phone numbers, emails, personal salutations, URLs, OTPs, and sensitive IDs)
     * and conversational noise from the given text.
     * Guaranteed: NEVER logs sanitized user text/memories to Logcat.
     */
    fun stripPii(text: String): String {
        val sanitized = text
            .replace(PHONE_REGEX, "[PHONE REDACTED]")
            .replace(EMAIL_REGEX, "[EMAIL REDACTED]")
            .replace(PERSONAL_GREETINGS, "[GREETING REDACTED]")
            .replace(URL_REGEX, "[URL REDACTED]")
            .replace(OTP_TOKEN_REGEX, "[TOKEN REDACTED]")
            .replace(JWT_TOKEN_REGEX, "[TOKEN REDACTED]")
            .replace(API_KEY_REGEX, "[TOKEN REDACTED]")
            .replace(SENSITIVE_ID_REGEX, "[ID REDACTED]")
            .replace(CONVERSATIONAL_NOISE, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        Log.d("LIFEOS_MODULE7", "LIFEOS_MODULE7: PII Sanitized successfully (length: ${sanitized.length})")
        Log.i("LIFEOS_MODULE6", "LIFEOS_MODULE6: PII Redacted successfully")
        return sanitized
    }

    /**
     * Sanitizes incoming raw text to enforce the Data Minimization standard:
     * - Strips telephone numbers, emails, and personal greetings/names.
     * - Truncates excessively long message bodies.
     * - Formats a concise, privacy-safe summary for persistent Room DB storage.
     */
    fun sanitizeRawPayload(
        rawText: String,
        extractedItem: String? = null,
        extractedAction: String? = null
    ): String {
        var sanitized = stripPii(rawText)

        if (sanitized.length > 80) {
            sanitized = sanitized.take(77) + "..."
        }

        val actionTag = extractedAction ?: "Event"
        val itemTag = extractedItem ?: "None"
        val minimizedText = "[Sanitized: Action=$actionTag, Item=$itemTag] - $sanitized"
        Log.d(TAG, "Data Minimizer produced sanitized payload (length: ${minimizedText.length})")
        return minimizedText
    }

    /**
     * Replaces raw text of all historical evidence logs with privacy safe placeholders.
     */
    suspend fun pruneEvidenceLogs(dao: LifeOsDao) {
        dao.sanitizeAllEvidence()
        Log.i(TAG, "All historical evidence records sanitized under Data Minimization standard.")
    }

    /**
     * Purges all evidence records from the Room database.
     */
    suspend fun clearEvidenceLogs(dao: LifeOsDao) {
        dao.clearAllEvidence()
        Log.i(TAG, "All evidence logs permanently purged from Room DB.")
    }

    /**
     * Purges all temporary items from the Room database.
     */
    suspend fun clearTemporaryItems(dao: LifeOsDao): Int {
        val count = dao.clearTemporaryItems()
        Log.i(TAG, "Cleared $count temporary items under Data Minimization standard.")
        return count
    }

    /**
     * Sanitizes clipboard content to remove OTPs, sensitive numbers, passwords, emails, phone numbers, and API keys.
     */
    fun sanitizeClipboardSnippet(text: String): String {
        return text
            .replace(JWT_TOKEN_REGEX, "[TOKEN REDACTED]")
            .replace(API_KEY_REGEX, "[TOKEN REDACTED]")
            .replace(OTP_TOKEN_REGEX, "[TOKEN REDACTED]")
            .replace(SENSITIVE_ID_REGEX, "[ID REDACTED]")
            .replace(EMAIL_REGEX, "[EMAIL REDACTED]")
            .replace(PHONE_REGEX, "[PHONE REDACTED]")
            .trim()
    }

    /**
     * Masks emergency phone number for privacy display (e.g. +91 *****1234).
     */
    fun maskPhoneNumber(phone: String): String {
        val clean = phone.trim()
        return if (clean.length > 4) {
            val prefix = if (clean.startsWith("+")) clean.take(3) else ""
            val suffix = clean.takeLast(4)
            val stars = "*".repeat(maxOf(3, clean.length - prefix.length - 4))
            "$prefix$stars$suffix"
        } else {
            "****"
        }
    }

    /**
     * Minimizes memory note text before storage.
     */
    fun minimizeMemoryNote(text: String): String {
        return stripPii(text)
    }
}

