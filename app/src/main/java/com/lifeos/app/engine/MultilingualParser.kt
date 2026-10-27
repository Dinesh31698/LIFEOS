package com.lifeos.app.engine

import java.util.regex.Pattern

enum class CanonicalAction {
    REQUIRED_ITEM,
    EVENT_CANCELLED,
    EVENT_RESCHEDULED,
    EVENT_VENUE_CHANGED,
    INFO_NOTICE;

    companion object {
        val ACTION_REQUIREMENT = REQUIRED_ITEM
    }
}

data class ParsedIntentResult(
    val canonicalAction: CanonicalAction,
    val detectedLanguage: String,
    val rawText: String,
    val subjectActivity: String?,
    val relatedItem: String?,
    val timeOrDateInfo: String?,
    val confidence: Float,
    val targetModeId: Int,
    val targetEpochMillis: Long? = null,
    val oldTime: String? = null,
    val newTime: String? = null,
    val location: String? = null,
    val isAdversarialAttempt: Boolean = false
)

object MultilingualParser {

    // Language identification scripts
    private val TELUGU_SCRIPT = Pattern.compile("[\\u0C00-\\u0C7F]")
    private val HINDI_DEVANAGARI = Pattern.compile("[\\u0900-\\u097F]")

    // Teluglish markers
    private val TELUGLISH_MARKERS = listOf(
        "repu", "undi", "ledu", "undadu", "marchipoku", "teesuko", "teesukoni", "ravali", "thee", "theeyi",
        "theeyandi", "theeko", "theukondi", "thesukondi", "pettuko", "aindi", "ayindi", "aypoyindi", "marindi", "marcharu", "ekkada",
        "vachara", "kadha", "ki", "lo", "nundi", "gurinchi", "ippudu", "chesuko", "pattukoni", "pattukonira",
        "eroju", "nedu", "shift ayyindi", "shift aindi", "shift aypoyindi"
    )

    // Hinglish markers
    private val HINGLISH_MARKERS = listOf(
        "kal", "hai", "nahi", "mat", "bhoolna", "lao", "lana", "laana", "leke", "rakhna",
        "hoga", "hogi", "gaya", "gayi", "chutti", "badal", "karo", "karna", "bhai", "aana", "leke aana",
        "aaj", "shift ho gaya", "shift ho gayi", "band hai", "bandh hai", "bandh"
    )

    // Cancellation phrase patterns
    private val CANCEL_KEYWORDS = listOf(
        "cancel", "cancelled", "canceled", "cancellation", "closed", "college is closed",
        // Teluglish
        "cancel aindi", "cancel ayindi", "cancel aypoyindi", "lab ledu", "class ledu", "practice ledu", "undadu", "jaragadhu", "college ledu",
        // Telugu Script
        "రద్దు", "లేదు", "రద్దయింది", "రద్దైంది", "జరగదు",
        // Hinglish
        "cancel ho gayi", "cancel ho gaya", "cancel hai", "nahi hogi", "nahi hoga", "chutti hai", "band hai", "bandh hai", "bandh",
        // Hindi Devanagari Script
        "रद्द", "नहीं होगी", "नहीं होगा", "छुट्टी", "बंद है"
    )

    // Rescheduling phrase patterns
    private val RESCHEDULE_KEYWORDS = listOf(
        "reschedule", "rescheduled", "postpone", "postponed", "prepone", "preponed", "delayed", "shifted", "shift", "moved to", "moved from", "shift to", "instead of",
        // Teluglish
        "postpone aindi", "marindi", "marcharu", "repatiki marindi", "shift ayyindi", "shift aindi", "shift aypoyindi",
        // Telugu Script
        "మార్చారు", "వాయిదా", "పోస్ట్ పోన్",
        // Hinglish
        "postpone ho gaya", "postpone ho gayi", "reschedule ho gaya", "time badal", "shift ho gaya", "shift ho gayi",
        // Hindi Devanagari Script
        "बदल", "स्थगित", "बदला गया"
    )

    // Required Item action phrases
    private val REQUIRED_ITEM_KEYWORDS = listOf(
        "bring", "carry", "take", "pack", "submit", "remember", "need", "don't forget", "dont forget", "must have", "get", "keep", "compulsory", "mandatory", "must bring", "definitely",
        // Teluglish
        "marchipoku", "teesuko", "teesukoni", "ravali", "thee", "theeyi", "theeyandi", "theeko", "theukondi", "thesukondi", "pettuko", "teesukoni ravali", "pattukoni ra", "pattukoni ravali",
        // Telugu Script
        "తేవాలి", "తీసుకురావాలి", "మర్చిపోకు", "తీసుకో", "పెట్టుకో", "తప్పనిసరి",
        // Hinglish
        "mat bhoolna", "bhoolna mat", "laana", "lana", "leke", "rakhna", "leke aana", "lao", "laana hai", "lana hai", "zaroori hai",
        // Hindi Devanagari Script
        "लाना", "लेकर आना", "भूलना मत", "मत भूलना", "ज़रूरी"
    )

    // Ambiguity / Uncertainty markers (trigger Ghost Mode 0.50 - 0.84)
    val UNCERTAINTY_KEYWORDS = listOf(
        "maybe", "might", "may be", "perhaps", "possibly", "probably", "not sure", "tentative",
        "check if", "doubt", "if possible", "kinda", "sort of", "could be", "unconfirmed",
        "think", "i think", "rumor", "rumour", "not feeling well", "heard that",
        // Teluglish uncertainty
        "emo", "anukunta", "undemo", "chudu", "telidu", "doubt undi",
        // Hinglish uncertainty
        "shayad", "lagta hai", "dekh lena", "ho sake toh", "pata nahi", "pakka nahi"
    )

    // Adversarial / Malicious prompt injection patterns (Untrusted data defense)
    private val ADVERSARIAL_PATTERNS = listOf(
        Regex("(?i)\\b(?:ignore|bypass|override)\\s+(?:all\\s+)?(?:rules|instructions|prompts|lifeos|security)\\b"),
        Regex("(?i)\\b(?:delete|drop|wipe|remove|clear)\\s+(?:all\\s+)?(?:reminders|data|database|tables|modes|records)\\b"),
        Regex("(?i)\\b(?:system|administrator|admin|root)\\s*(?::|instructions?|command|override|privileges)\\b"),
        Regex("(?i)\\b(?:administrator|admin):?\\s*(?:disable|turn\\s*off|bypass)\\s*safe\\s*mode\\b"),
        Regex("(?i)\\b(?:grant|enable|escalate)\\s+(?:yourself|permission|access)\\b"),
        Regex("(?i)\\bmark\\s+(?:everything|all)\\s+ready\\b"),
        Regex("(?i)\\b(?:system:\\s*change\\s+every\\s+mode)\\b"),
        Regex("(?i)\\b(?:drop\\s+table|truncate\\s+table|delete\\s+from)\\b")
    )

    // Activity keywords (sorted by length descending so specific multi-word expressions match first)
    private val ACTIVITIES = listOf(
        "dbms lab evaluation" to "DBMS Lab",
        "football practice" to "Football Practice",
        "sports practice" to "Football Practice",
        "lab evaluation" to "DBMS Lab",
        "डीबीएमएस लैब" to "DBMS Lab",
        "ఫుట్‌బాల్" to "Football Practice",
        "ఫుట్ బాల్" to "Football Practice",
        "फुटबॉल" to "Football Practice",
        "gym session" to "Workout",
        "dbms lab" to "DBMS Lab",
        "evaluation" to "DBMS Lab",
        "తరగతి" to "College Lecture",
        "క్లాస్" to "College Lecture",
        "क्लास" to "College Lecture",
        "lecture" to "College Lecture",
        "meeting" to "Meeting",
        "పరీక్ష" to "Semester Exam",
        "परीक्षा" to "Semester Exam",
        "workout" to "Workout",
        "cricket" to "Sports Practice",
        "football" to "Football Practice",
        "ల్యాబ్" to "DBMS Lab",
        "లాబ్" to "DBMS Lab",
        "लैब" to "DBMS Lab",
        "class" to "College Lecture",
        "exam" to "Semester Exam",
        "dbms" to "DBMS Lab",
        "gym" to "Workout",
        "జిమ్" to "Workout",
        "జిమ్ సెషన్" to "Workout",
        "వ్యాయామం" to "Workout",
        "जिम" to "Workout",
        "lab" to "DBMS Lab"
    ).sortedByDescending { it.first.length }

    // Item keywords (sorted by length descending so specific items like 'hall ticket' match before 'ticket')
    private val ITEMS = listOf(
        "football boots" to "Football Boots",
        "boarding pass" to "Boarding Pass / Ticket",
        "workout clothes" to "Gym Clothes & Shoes",
        "gym clothes" to "Gym Clothes & Shoes",
        "water bottle" to "Water Bottle",
        "గుర్తింపు కార్డు" to "ID Card",
        "laptop & charger" to "Laptop & Charger",
        "assignment file" to "Assignment File",
        "textbook & notes" to "Textbook & Notes",
        "dbms record" to "DBMS Record",
        "hall ticket" to "Hall Ticket",
        "హాల్ టికెట్" to "Hall Ticket",
        "lab record" to "DBMS Record",
        "shin guards" to "Shin Guards",
        "पहचान पत्र" to "ID Card",
        "నీళ్ల బాటిల్" to "Water Bottle",
        "assignment" to "Assignment File",
        "అసైన్ మెంట్" to "Assignment File",
        "shaker bottle" to "Shaker Bottle",
        "id card" to "ID Card",
        "ఐడీ కార్డు" to "ID Card",
        "आईडी कार्ड" to "ID Card",
        "passport" to "Passport / ID",
        "పాస్ పోర్ట్" to "Passport / ID",
        "notebook" to "Textbook & Notes",
        "రికార్డు" to "DBMS Record",
        "రికార్డ్" to "DBMS Record",
        "रिकॉर्ड" to "DBMS Record",
        "पुस्तకం" to "Textbook & Notes",
        "charger" to "Laptop & Charger",
        "shaker" to "Shaker Bottle",
        "protein" to "Shaker Bottle",
        "ticket" to "Boarding Pass / Ticket",
        "టికెట్" to "Boarding Pass / Ticket",
        "jersey" to "Jersey & Shorts",
        "జర్సీ" to "Jersey & Shorts",
        "laptop" to "Laptop & Charger",
        "record" to "DBMS Record",
        "किताब" to "Textbook & Notes",
        "boots" to "Football Boots",
        "బూట్లు" to "Football Boots",
        "shoes" to "Gym Clothes & Shoes",
        "towel" to "Gym Towel",
        "టవల్" to "Gym Towel",
        "notes" to "Textbook & Notes",
        "book" to "Textbook & Notes"
    ).sortedByDescending { it.first.length }

    // Venue change phrase patterns
    private val VENUE_KEYWORDS = listOf(
        "venue changed", "venue moved", "venue shifted", "venue is", "room changed",
        "shifted to block", "moved to block", "shifted to room", "moved to room", "shifted to hall", "moved to hall",
        "shifted from block", "moved from block", "relocated to", "transferred to",
        // Teluglish
        "venue marindi", "venue marcharu", "block ki shift", "block lo undi", "block ki marcharu",
        // Telugu
        "వేదిక మారింది", "బ్లాక్ సి", "బ్లాక్ బి",
        // Hinglish / Hindi
        "venue badal", "venue badal gaya", "block me shift", "kamra badal gaya"
    )

    private val TIME_REGEX = Pattern.compile("(?i)\\b(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)|\\d{1,2}:\\d{2})\\b")
    private val DATE_REGEX = Pattern.compile("(?i)(?:\\b(tomorrow|today|tonight|repu|repatiki|kal|monday|tuesday|wednesday|thursday|friday|saturday|sunday|somavaram|mangalvaram|budhavaram|guruvaram|shukravaram|shanivaram|adivaram)\\b|(రేపు|ఈరోజు|నేడు|కల్|బుధవారం|బుధవారానికి|సోమవారం|మంగళవారం|గురువారం|శుక్రవారం|శనివారం|ఆదివారం|कल|आज|बुधवार|सोमवार|मंगलवार|गुरुवार|शुक्रवार|शनिवार|रविवार))")

    /**
     * Resolves natural language date and time markers into an absolute system epoch timestamp (in milliseconds).
     */
    fun parseDateTimeToEpoch(
        dateStr: String?,
        timeStr: String?,
        baseTimeMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (dateStr == null && timeStr == null) return null
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = baseTimeMillis
        }

        // Relative Date Computation
        if (dateStr != null) {
            val lowerDate = dateStr.lowercase()
            val targetDayOfWeek = when (lowerDate) {
                "sunday", "ఆదివారం", "adivaram", "रविवार" -> java.util.Calendar.SUNDAY
                "monday", "సోమవారం", "somavaram", "सोमवार" -> java.util.Calendar.MONDAY
                "tuesday", "మంగళవారం", "mangalvaram", "मंगलवार" -> java.util.Calendar.TUESDAY
                "wednesday", "బుధవారం", "budhavaram", "బుధవారానికి", "बुधवार" -> java.util.Calendar.WEDNESDAY
                "thursday", "గురువారం", "guruvaram", "गुरुवार" -> java.util.Calendar.THURSDAY
                "friday", "శుక్రవారం", "shukravaram", "शुक्रवार" -> java.util.Calendar.FRIDAY
                "saturday", "శనివారం", "shanivaram", "शनिवार" -> java.util.Calendar.SATURDAY
                else -> null
            }

            if (targetDayOfWeek != null) {
                val currentDay = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                var daysUntil = (targetDayOfWeek - currentDay + 7) % 7
                if (daysUntil == 0) {
                    daysUntil = 7
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, daysUntil)
            } else when {
                lowerDate in listOf("repu", "repatiki", "tomorrow", "kal", "రేపు", "कल") -> {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                lowerDate in listOf("today", "tonight", "aaj", "eroju", "ఈరోజు", "నేడు", "आज") -> {
                    // Stays on current calendar day
                }
            }
        }

        // Time Computation
        if (timeStr != null) {
            val matcher = Pattern.compile("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(timeStr.trim())
            if (matcher.find()) {
                var hour = matcher.group(1)?.toIntOrNull() ?: 9
                val minute = matcher.group(2)?.toIntOrNull() ?: 0
                val ampm = matcher.group(3)?.lowercase()
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
            }
        } else {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
        }

        return calendar.timeInMillis
    }

    fun parse(rawText: String, sourcePackage: String = "unknown"): ParsedIntentResult {
        val lower = rawText.lowercase().trim()

        // 1. Detect Adversarial Prompt Injection / Malicious Text
        val isAdversarial = ADVERSARIAL_PATTERNS.any { it.containsMatchIn(rawText) }
        if (isAdversarial) {
            return ParsedIntentResult(
                canonicalAction = CanonicalAction.INFO_NOTICE,
                detectedLanguage = "English",
                rawText = rawText,
                subjectActivity = null,
                relatedItem = null,
                timeOrDateInfo = null,
                confidence = 0.10f,
                targetModeId = 1,
                targetEpochMillis = null,
                isAdversarialAttempt = true
            )
        }

        // 2. Detect Language
        val language = detectLanguage(rawText, lower)

        // 3. Extract Time Variations (including moved from/to, instead of, vernacular markers)
        val (extractedTime, oldTime, newTime) = extractTimeDetails(rawText, lower)
        val extractedDate = extractRegex(rawText, DATE_REGEX)
        val dateTime = listOfNotNull(extractedDate, extractedTime).joinToString(" ").ifBlank { null }
        val targetEpochMillis = parseDateTimeToEpoch(extractedDate, extractedTime)
        val extractedLocation = extractLocation(rawText, lower)

        // 4. Classify Canonical Action
        val isCancelled = CANCEL_KEYWORDS.any { lower.contains(it) }
        val isVenueChanged = !isCancelled && !lower.contains("ticket") && (
            VENUE_KEYWORDS.any { lower.contains(it) } ||
            (extractedLocation != null && (lower.contains("block") || lower.contains("room") || lower.contains("hall") || lower.contains("venue") || lower.contains("stadium")) && extractedTime == null && !REQUIRED_ITEM_KEYWORDS.any { lower.contains(it) })
        )
        val isRescheduled = !isCancelled && !isVenueChanged && RESCHEDULE_KEYWORDS.any { lower.contains(it) }
        val isRequired = !isCancelled && !isVenueChanged && !isRescheduled && REQUIRED_ITEM_KEYWORDS.any { lower.contains(it) }

        val canonicalAction = when {
            isCancelled -> CanonicalAction.EVENT_CANCELLED
            isVenueChanged -> CanonicalAction.EVENT_VENUE_CHANGED
            isRescheduled -> CanonicalAction.EVENT_RESCHEDULED
            isRequired -> CanonicalAction.REQUIRED_ITEM
            else -> CanonicalAction.INFO_NOTICE
        }

        // 5. Identify Subject Activity
        val subjectActivity = ACTIVITIES.firstOrNull { lower.contains(it.first) }?.second

        // 6. Identify Related Item
        var relatedItem = ITEMS.firstOrNull { lower.contains(it.first) }?.second
        if (relatedItem == null && (subjectActivity == "DBMS Lab" || lower.contains("lab") || lower.contains("ల్యాబ్") || lower.contains("लैब"))) {
            // Contextual correlation: Lab relates to DBMS Record
            relatedItem = "DBMS Record"
        }

        // 7. Context Linking & Target Mode (1 = College, 2 = Gym, 3 = Travel, 4 = Sports)
        val targetModeId = determineTargetMode(lower, subjectActivity, relatedItem)

        // 8. Compute Confidence
        val hasUncertainty = UNCERTAINTY_KEYWORDS.any { lower.contains(it) }

        // Rule for isolated ambiguous items with zero context (e.g. "Don't forget the record" with no lab/college/exam):
        val hasWeakIsolatedContext = (subjectActivity == null && relatedItem != null &&
                !lower.contains("lab") && !lower.contains("dbms") && !lower.contains("college") &&
                !lower.contains("exam") && !lower.contains("hall ticket") && !lower.contains("gym") &&
                !lower.contains("football"))

        var confidence = if (canonicalAction == CanonicalAction.INFO_NOTICE && subjectActivity == null && relatedItem == null) {
            0.20f
        } else if (hasWeakIsolatedContext) {
            0.45f // Low/medium confidence: requires user confirmation / Ghost Mode
        } else {
            0.60f
        }

        if (canonicalAction != CanonicalAction.INFO_NOTICE && !hasWeakIsolatedContext) confidence += 0.20f
        if (subjectActivity != null) confidence += 0.10f
        if (relatedItem != null && !hasWeakIsolatedContext) confidence += 0.10f
        if (dateTime != null && !hasWeakIsolatedContext) confidence += 0.05f

        val finalConfidence = if (hasUncertainty) {
            // Ambiguous/informal expressions penalized into Ghost Mode territory [0.50 - 0.84]
            (confidence - 0.25f).coerceIn(0.55f, 0.78f)
        } else if (lower == "tomorrow changed" || lower == "changed") {
            // Unclear what changed -> insufficient information
            0.35f
        } else {
            confidence.coerceIn(0.20f, 0.98f)
        }

        return ParsedIntentResult(
            canonicalAction = canonicalAction,
            detectedLanguage = language,
            rawText = rawText,
            subjectActivity = subjectActivity,
            relatedItem = relatedItem,
            timeOrDateInfo = dateTime,
            confidence = finalConfidence,
            targetModeId = targetModeId,
            targetEpochMillis = targetEpochMillis,
            oldTime = oldTime,
            newTime = newTime,
            location = extractedLocation,
            isAdversarialAttempt = false
        )
    }

    fun extractLocation(rawText: String, lower: String): String? {
        if (lower.contains("ticket")) return null

        // Pattern 1: "from X to Y" (e.g., "from Block A to Block C")
        val fromToPattern = Pattern.compile("(?i)\\bfrom\\s+(Block\\s+[A-Za-z0-9]+|Hall\\s+[0-9]+[A-Za-z]?|Room\\s+[0-9]+[A-Za-z]?)\\s+to\\s+(Block\\s+[A-Za-z0-9]+|Hall\\s+[0-9]+[A-Za-z]?|Room\\s+[0-9]+[A-Za-z]?)\\b")
        val fromToMatcher = fromToPattern.matcher(rawText)
        if (fromToMatcher.find()) {
            return fromToMatcher.group(2)?.trim()
        }

        // Pattern 2: "X instead of Y" (e.g., "Block C instead of Block A")
        val insteadPattern = Pattern.compile("(?i)\\b(Block\\s+[A-Za-z0-9]+|Hall\\s+[0-9]+[A-Za-z]?|Room\\s+[0-9]+[A-Za-z]?)\\s+instead\\s+of\\s+(Block\\s+[A-Za-z0-9]+|Hall\\s+[0-9]+[A-Za-z]?|Room\\s+[0-9]+[A-Za-z]?)\\b")
        val insteadMatcher = insteadPattern.matcher(rawText)
        if (insteadMatcher.find()) {
            return insteadMatcher.group(1)?.trim()
        }

        // Pattern 3: Standard venues: Block A/B/C, Hall 3, Room 204, Main Indoor Stadium, Seminar Hall, Auditorium, etc.
        val venuePattern = Pattern.compile("(?i)\\b(?:venue:?\\s*|shifted\\s+to\\s+|moved\\s+to\\s+|conducted\\s+(?:in|at)\\s+|in\\s+|at\\s+|to\\s+)?(Block\\s+[A-Za-z0-9]+|Hall\\s+[0-9]+[A-Za-z]?|Hall\\s+[A-D]\\b|Room\\s+[0-9]+[A-Za-z]?|Main\\s+Indoor\\s+Stadium|Indoor\\s+Stadium|Outdoor\\s+Ground|Seminar\\s+Hall(?:\\s+[0-9]+[A-Za-z]?)?|Auditorium)\\b")
        val venueMatcher = venuePattern.matcher(rawText)
        if (venueMatcher.find()) {
            val candidate = venueMatcher.group(1)?.trim()
            if (candidate != null && !candidate.equals("at", ignoreCase = true) && !candidate.equals("in", ignoreCase = true) && !candidate.contains("ticket", ignoreCase = true)) {
                return candidate
            }
        }

        // Vernacular script checks
        return when {
            lower.contains("బ్లాక్ సి") -> "Block C"
            lower.contains("బ్లాక్ బి") -> "Block B"
            lower.contains("బ్లాక్ ఎ") || lower.contains("బ్లాక్ a") -> "Block A"
            else -> null
        }
    }

    private fun determineTargetMode(lower: String, subjectActivity: String?, relatedItem: String?): Int {
        return when {
            // Sports (Mode 4)
            lower.contains("football") || lower.contains("cricket") || lower.contains("practice") ||
            lower.contains("match") || lower.contains("sports") || lower.contains("shin guards") ||
            lower.contains("jersey") || lower.contains("boots") || lower.contains("ఫుట్‌బాల్") ||
            lower.contains("फुटबॉल") || subjectActivity == "Football Practice" -> 4

            // Gym (Mode 2)
            lower.contains("gym") || lower.contains("workout") || lower.contains("shaker") ||
            lower.contains("protein") || lower.contains("towel") || lower.contains("creatine") ||
            lower.contains("జిమ్") || lower.contains("వ్యాయామం") || lower.contains("जिम") ||
            subjectActivity == "Workout" -> 2

            // Travel (Mode 3)
            lower.contains("flight") || lower.contains("train") || lower.contains("passport") ||
            lower.contains("travel") || lower.contains("trip") || lower.contains("airport") -> 3

            // College (Mode 1)
            lower.contains("lab") || lower.contains("dbms") || lower.contains("record") ||
            lower.contains("exam") || lower.contains("college") || lower.contains("lecture") ||
            lower.contains("assignment") || lower.contains("class") || lower.contains("book") ||
            lower.contains("notes") || lower.contains("hall ticket") || lower.contains("ల్యాబ్") ||
            lower.contains("పరీక్ష") || lower.contains("తరగతి") || lower.contains("కాలేజ్") ||
            lower.contains("కాలేజి") || lower.contains("పుస్తకం") || lower.contains("लैब") ||
            lower.contains("क्लास") || lower.contains("परीक्षा") || lower.contains("कॉलेज") ||
            subjectActivity == "DBMS Lab" || subjectActivity == "College Lecture" -> 1

            else -> 1 // Default fallback
        }
    }

    private data class TimeExtraction(val primaryTime: String?, val oldTime: String?, val newTime: String?)

    private fun extractTimeDetails(rawText: String, lower: String): TimeExtraction {
        // Pattern 1: "from X to Y" or "X to Y" (e.g. "from 6 PM to 7 PM", "from 10 AM to 2 PM", "10 AM to 2 PM")
        val fromToPattern = Pattern.compile("(?i)\\b(?:from\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\s+to\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b")
        val fromToMatcher = fromToPattern.matcher(rawText)
        if (fromToMatcher.find()) {
            val tOld = fromToMatcher.group(1)?.trim()
            val tNew = fromToMatcher.group(2)?.trim()
            return TimeExtraction(primaryTime = tNew, oldTime = tOld, newTime = tNew)
        }

        // Pattern 2: "at X instead of Y" (e.g. "at 11 AM instead of 10")
        val insteadPattern = Pattern.compile("(?i)\\b(?:at\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\s+instead\\s+of\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b")
        val insteadMatcher = insteadPattern.matcher(rawText)
        if (insteadMatcher.find()) {
            val tNew = insteadMatcher.group(1)?.trim()
            val tOld = insteadMatcher.group(2)?.trim()
            return TimeExtraction(primaryTime = tNew, oldTime = tOld, newTime = tNew)
        }

        // Pattern 3: Vernacular "11 గంటలకు" (Telugu) or "11 बजे" (Hindi) or "11 ki" (Teluglish)
        val vernacularTimePattern = Pattern.compile("(?i)\\b(\\d{1,2})\\s*(?:గంటలకు|గంటలు|బజే|बजे|ki|baje)")
        val vernMatcher = vernacularTimePattern.matcher(rawText)
        if (vernMatcher.find()) {
            val hour = vernMatcher.group(1)
            val formatted = "$hour AM"
            return TimeExtraction(primaryTime = formatted, oldTime = null, newTime = formatted)
        }

        // Pattern 4: "moved to 11" or "shift to 11" or "postponed to 2 PM"
        val movedToBare = Pattern.compile("(?i)\\b(?:moved\\s+to|shift\\s+to|shifted\\s+to|postpone\\s+to|postponed\\s+to|preponed\\s+to|at)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b")
        val movedMatcher = movedToBare.matcher(rawText)
        if (movedMatcher.find()) {
            val time = movedMatcher.group(1)?.trim() ?: ""
            val formatted = if (!time.lowercase().contains("am") && !time.lowercase().contains("pm")) {
                val hr = time.toIntOrNull() ?: 9
                if (hr in 1..7) "$hr PM" else "$hr AM"
            } else {
                time
            }
            return TimeExtraction(primaryTime = formatted, oldTime = null, newTime = formatted)
        }

        // Pattern 5: Standard TIME_REGEX
        val standardTime = extractRegex(rawText, TIME_REGEX)
        return TimeExtraction(primaryTime = standardTime, oldTime = null, newTime = standardTime)
    }

    private fun detectLanguage(rawText: String, lower: String): String {
        return when {
            TELUGU_SCRIPT.matcher(rawText).find() -> "Telugu"
            HINDI_DEVANAGARI.matcher(rawText).find() -> "Hindi"
            TELUGLISH_MARKERS.any { lower.contains(it) } -> "Teluglish"
            HINGLISH_MARKERS.any { lower.contains(it) } -> "Hinglish"
            else -> "English"
        }
    }

    private fun extractRegex(text: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }
}
