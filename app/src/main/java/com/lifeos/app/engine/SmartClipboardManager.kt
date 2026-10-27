package com.lifeos.app.engine

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.lifeos.app.security.DataMinimizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClipboardEventProposal(
    val title: String,
    val timeSnippet: String?,
    val locationSnippet: String?,
    val meetingLink: String?,
    val rawSnippetSanitized: String,
    val detectedLocation: String? = locationSnippet
)

object SmartClipboardManager {

    private val TIME_REGEX = Regex("(?i)\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)|(?:at\\s+)?\\d{1,2}\\s*(?:am|pm))\\b")
    private val DAY_REGEX = Regex("(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|today|tomorrow)\\b")
    private val URL_REGEX = Regex("(?i)\\bhttps?://\\S+\\b")
    private val LOCATION_KEYWORD_REGEX = Regex("(?i)\\b(?:at|in|room|block|hall|campus|lab)\\s+([A-Za-z0-9]+(?:\\s+[A-Za-z0-9]+){0,3}?)(?=\\s+(?:on|tomorrow|today|at|from|by|with)|[.,;\\n]|$)")

    private val _lastParsedResult = MutableStateFlow<ClipboardEventProposal?>(null)
    val lastParsedResult = _lastParsedResult.asStateFlow()

    fun clearParsedResult() {
        _lastParsedResult.value = null
    }

    fun parseSnippet(rawText: String): ClipboardEventProposal? {
        val sanitized = DataMinimizer.sanitizeClipboardSnippet(rawText)
        if (sanitized.isBlank()) return null

        val timeMatch = TIME_REGEX.find(sanitized)?.value
        val dayMatch = DAY_REGEX.find(sanitized)?.value
        val urlMatch = URL_REGEX.find(sanitized)?.value
        val locMatch = LOCATION_KEYWORD_REGEX.find(sanitized)?.groupValues?.getOrNull(1)?.trim()

        val timeSummary = listOfNotNull(dayMatch, timeMatch).joinToString(" ").ifEmpty { null }

        val titleCandidate = sanitized
            .replace(URL_REGEX, "")
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?.take(40)
            ?.trim() ?: "Scheduled Commitment"

        val proposal = ClipboardEventProposal(
            title = titleCandidate,
            timeSnippet = timeSummary,
            locationSnippet = locMatch,
            meetingLink = urlMatch,
            rawSnippetSanitized = sanitized.take(100),
            detectedLocation = locMatch
        )
        _lastParsedResult.value = proposal
        return proposal
    }

    /**
     * Inspects the device clipboard ONLY when explicitly invoked by user in the foreground.
     * Absolutely zero background polling or Accessibility scraping.
     * Discards the raw content immediately after parsing.
     */
    fun inspectClipboardOnDemand(context: Context): ClipboardEventProposal? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription
        if (description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true &&
            description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) != true
        ) return null

        val item = clipboard.primaryClip?.getItemAt(0) ?: return null
        val rawText = item.text?.toString() ?: return null

        return parseSnippet(rawText)
    }
}
