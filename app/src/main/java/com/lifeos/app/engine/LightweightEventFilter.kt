package com.lifeos.app.engine

import java.util.regex.Pattern

data class FilteredPayload(
    val rawText: String,
    val sourcePackage: String,
    val detectedLanguage: String,
    val extractedAction: String?,
    val extractedItemName: String?,
    val extractedDateTime: String?,
    val targetModeId: Int,
    val relevanceScore: Float,
    val canonicalAction: CanonicalAction = CanonicalAction.INFO_NOTICE,
    val targetEpochMillis: Long? = null,
    val oldTime: String? = null,
    val newTime: String? = null,
    val location: String? = null,
    val isRelevant: Boolean = true,
    val isAdversarial: Boolean = false
)

object LightweightEventFilter {

    private val ACTION_KEYWORDS = listOf(
        "bring", "carry", "take", "submit", "get", "pack", "remember", "need", "keep", "don't forget", "dont forget", "compulsory", "mandatory",
        "teesuko", "teesukoni", "theukondi", "thesukondi", "pettuko", "thee", "theeyi", "theeyandi", "theeko", "ravali", "marchipoku", "pattukoni ra", // Telugu / Teluglish
        "lao", "lana", "laana", "laana hai", "rakhna", "leke", "bhoolna mat", "mat bhoolna" // Hindi / Hinglish
    )

    private val COLLEGE_KEYWORDS = listOf(
        "lab", "record", "dbms", "exam", "assignment", "college", "id card", "viva",
        "evaluation", "hall ticket", "notes", "notebook", "book", "project", "faculty", "hod", "attendance", "lecture",
        "ల్యాబ్", "పరీక్ష", "తరగతి", "కాలేజ్", "పుస్తకం", "लैब", "क्लास", "परीक्षा", "कॉलेज"
    )

    private val GYM_KEYWORDS = listOf(
        "gym", "workout", "protein", "shaker", "creatine", "dumbbells", "cardio",
        "towel", "shoes", "bench", "fitness", "training", "legs", "chest",
        "జిమ్", "వ్యాయామం", "जिम", "कसरत"
    )

    private val TRAVEL_KEYWORDS = listOf(
        "flight", "ticket", "airport", "passport", "train", "boarding", "station",
        "hotel", "visa", "pack bag", "travel", "trip", "luggage", "cab"
    )

    private val SPORTS_KEYWORDS = listOf(
        "football", "practice", "cricket", "match", "sports", "boots", "jersey", "shin guards", "ground",
        "ఫుట్‌బాల్", "ఫుట్ బాల్", "ప్రాక్టీస్", "फुटबॉल", "मैच"
    )

    // Irrelevant chatter / promotional / system status signals to discard immediately without LLM processing
    private val IRRELEVANT_PATTERNS = listOf(
        Regex("(?i)\\b(?:liked your (?:message|photo|post|story)|new (?:instagram|twitter|tiktok) follower)\\b"),
        Regex("(?i)\\b(?:battery fully charged|battery low|connected to wifi|download complete)\\b"),
        Regex("(?i)\\b(?:sale ends (?:today|now)|flat \\d+% off|cashback|discount code|free delivery|promo code)\\b"),
        Regex("(?i)\\b(?:hey,?\\s*are you coming\\??|are you coming\\??|how was lunch|catch up later|good morning|good night|happy birthday)\\b")
    )

    fun filter(rawText: String, sourcePackage: String = "unknown"): FilteredPayload {
        val lower = rawText.lowercase().trim()

        // 1. Cheap First-Stage Rejection: Discard social likes, battery full, promo sales, non-actionable chatter
        if (isIrrelevantSocialOrSystemChatter(lower)) {
            return FilteredPayload(
                rawText = rawText,
                sourcePackage = sourcePackage,
                detectedLanguage = "English",
                extractedAction = null,
                extractedItemName = null,
                extractedDateTime = null,
                targetModeId = 1,
                relevanceScore = 0.15f,
                canonicalAction = CanonicalAction.INFO_NOTICE,
                targetEpochMillis = null,
                isRelevant = false,
                isAdversarial = false
            )
        }

        // 2. Run through Multilingual Parser
        val parsedIntent = MultilingualParser.parse(rawText, sourcePackage)

        // If prompt injection / adversarial attack was detected
        if (parsedIntent.isAdversarialAttempt) {
            return FilteredPayload(
                rawText = rawText,
                sourcePackage = sourcePackage,
                detectedLanguage = parsedIntent.detectedLanguage,
                extractedAction = "Untrusted Input",
                extractedItemName = null,
                extractedDateTime = null,
                targetModeId = 1,
                relevanceScore = 0.10f,
                canonicalAction = CanonicalAction.INFO_NOTICE,
                targetEpochMillis = null,
                isRelevant = false,
                isAdversarial = true
            )
        }

        // 3. Keyword matches for modes
        val collegeMatches = countMatches(lower, COLLEGE_KEYWORDS)
        val gymMatches = countMatches(lower, GYM_KEYWORDS)
        val travelMatches = countMatches(lower, TRAVEL_KEYWORDS)
        val sportsMatches = countMatches(lower, SPORTS_KEYWORDS)
        val actionMatches = countMatches(lower, ACTION_KEYWORDS)

        val targetModeId = when {
            sportsMatches > 0 && sportsMatches >= collegeMatches && sportsMatches >= gymMatches -> 4
            gymMatches > collegeMatches && gymMatches >= travelMatches -> 2
            travelMatches > collegeMatches && travelMatches > gymMatches -> 3
            parsedIntent.targetModeId != 1 -> parsedIntent.targetModeId
            else -> 1
        }

        // 4. Extracted Item Name
        val itemName = parsedIntent.relatedItem ?: extractItemName(lower)

        // 5. Compute Relevance Score
        val hasUncertainty = MultilingualParser.UNCERTAINTY_KEYWORDS.any { lower.contains(it) }

        var score = parsedIntent.confidence
        val finalScore = if (!hasUncertainty) {
            if (actionMatches > 0) score += 0.10f
            if (collegeMatches > 0 || gymMatches > 0 || travelMatches > 0 || sportsMatches > 0) score += 0.10f
            score.coerceIn(0.0f, 1.0f)
        } else {
            // Keep ambiguous/informal expressions strictly inside Ghost Mode bracket [0.50 - 0.84]
            score.coerceIn(0.55f, 0.78f)
        }

        return FilteredPayload(
            rawText = rawText,
            sourcePackage = sourcePackage,
            detectedLanguage = parsedIntent.detectedLanguage,
            extractedAction = when (parsedIntent.canonicalAction) {
                CanonicalAction.EVENT_CANCELLED -> "Event Cancelled"
                CanonicalAction.EVENT_RESCHEDULED -> "Event Rescheduled"
                CanonicalAction.EVENT_VENUE_CHANGED -> "Venue Changed"
                CanonicalAction.REQUIRED_ITEM -> "Action Required"
                CanonicalAction.INFO_NOTICE -> if (actionMatches > 0) "Action Required" else "Notice"
            },
            extractedItemName = itemName,
            extractedDateTime = parsedIntent.timeOrDateInfo,
            targetModeId = targetModeId,
            relevanceScore = finalScore,
            canonicalAction = parsedIntent.canonicalAction,
            targetEpochMillis = parsedIntent.targetEpochMillis,
            oldTime = parsedIntent.oldTime,
            newTime = parsedIntent.newTime,
            location = parsedIntent.location,
            isRelevant = finalScore >= 0.50f,
            isAdversarial = false
        )
    }

    private fun isIrrelevantSocialOrSystemChatter(lower: String): Boolean {
        return IRRELEVANT_PATTERNS.any { it.containsMatchIn(lower) }
    }

    private fun countMatches(text: String, keywords: List<String>): Int {
        return keywords.count { text.contains(it) }
    }

    private fun extractItemName(lower: String): String? {
        return when {
            lower.contains("dbms record") || lower.contains("lab record") || lower.contains("record") ||
            lower.contains("రికార్డు") || lower.contains("రికార్డ్") || lower.contains("रिकॉर्ड") -> "DBMS Record"
            lower.contains("hall ticket") -> "Hall Ticket"
            lower.contains("id card") || lower.contains("గుర్తింపు కార్డు") || lower.contains("आईडी कार्ड") -> "ID Card"
            lower.contains("assignment") -> "Assignment File"
            lower.contains("book") || lower.contains("notes") || lower.contains("notebook") || lower.contains("పుస్తకం") || lower.contains("किताब") -> "Textbook & Notes"
            lower.contains("shaker") -> "Shaker Bottle"
            lower.contains("gym clothes") || lower.contains("workout clothes") -> "Gym Clothes & Shoes"
            lower.contains("football boots") || lower.contains("boots") -> "Football Boots"
            lower.contains("shin guards") -> "Shin Guards"
            lower.contains("jersey") -> "Jersey & Shorts"
            lower.contains("shoes") -> "Gym Clothes & Shoes"
            lower.contains("passport") -> "Passport / ID"
            lower.contains("charger") || lower.contains("laptop") -> "Laptop & Charger"
            lower.contains("water bottle") -> "Water Bottle"
            lower.contains("towel") -> "Gym Towel"
            lower.contains("flight ticket") || lower.contains("boarding pass") -> "Boarding Pass / Ticket"
            lower.contains("bring") || lower.contains("lao") || lower.contains("teesuko") || lower.contains("theeyi") -> "Required Item"
            else -> null
        }
    }
}
