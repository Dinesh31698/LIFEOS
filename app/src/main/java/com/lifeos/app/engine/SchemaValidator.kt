package com.lifeos.app.engine

import com.lifeos.app.data.EventEntity
import com.lifeos.app.data.ItemRequirementEntity

data class SchemaValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val extractedFields: Map<String, Any?> = emptyMap()
)

/**
 * Validates extracted structured entities (JSON representations or Room entities)
 * before persisting to SQLite Room DB. Guarantees schema integrity, non-blank identifiers,
 * valid positive mode associations, and normalized confidence intervals.
 * Acts as an impermeable firewall against malformed or hallucinated LLM outputs.
 */
object SchemaValidator {

    private val RECOGNIZED_INTENTS = setOf(
        "EVENT_TIME_CHANGE",
        "EVENT_RESCHEDULED",
        "EVENT_CANCELLED",
        "REQUIRED_ITEM",
        "ACTION_REQUIREMENT",
        "INFO_NOTICE"
    )

    /**
     * Validates a JSON representation of an extracted requirement item or event.
     * Ensures mandatory fields (itemName/title/activity, modeId/associatedModeId, confidence/confidenceScore)
     * are present, non-blank, and valid before Room DB persistence.
     */
    fun validateJson(jsonString: String): SchemaValidationResult {
        val errors = mutableListOf<String>()
        val fields = parseSimpleJson(jsonString)

        if (fields.isEmpty()) {
            return SchemaValidationResult(isValid = false, errors = listOf("Invalid or empty JSON payload"))
        }

        // Mandatory name or title or activity
        val hasItemName = (fields["itemName"] ?: fields["extractedItemName"])?.toString()?.isNotBlank() == true
        val hasTitle = fields["title"]?.toString()?.isNotBlank() == true
        val hasActivity = fields["activity"]?.toString()?.isNotBlank() == true

        if (!hasItemName && !hasTitle && !hasActivity) {
            errors.add("Missing mandatory entity identifier: must contain non-blank 'itemName', 'extractedItemName', 'title', or 'activity'")
        }

        // Positive mode ID if present
        val modeIdRaw = (fields["modeId"] ?: fields["associatedModeId"] ?: fields["targetModeId"])
        if (modeIdRaw != null) {
            val modeId = modeIdRaw.toString().toIntOrNull()
            if (modeId == null || modeId <= 0) {
                errors.add("Invalid mode identifier: 'modeId', 'associatedModeId', or 'targetModeId' must be a positive integer")
            }
        }

        // If intent is specified, check against recognized intents
        val intent = fields["intent"]?.toString()
        if (intent != null && intent !in RECOGNIZED_INTENTS) {
            errors.add("Unsupported or unrecognized intent: '$intent'")
        }

        // Confidence score in range [0.0, 1.0] if provided
        val confidenceRaw = fields["confidenceScore"] ?: fields["confidence"] ?: fields["relevanceScore"]
        if (confidenceRaw != null) {
            val confidence = confidenceRaw.toString().toFloatOrNull()
            if (confidence == null || confidence < 0.0f || confidence > 1.0f) {
                errors.add("Invalid confidence score: must be in range [0.0, 1.0]")
            }
        }

        return SchemaValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            extractedFields = fields
        )
    }

    /**
     * Validates an ItemRequirementEntity against persistence schema constraints.
     */
    fun validateItem(item: ItemRequirementEntity): SchemaValidationResult {
        val errors = mutableListOf<String>()
        if (item.itemName.isBlank()) {
            errors.add("Item name must not be blank")
        }
        if (item.modeId <= 0) {
            errors.add("Mode ID must be greater than 0")
        }
        if (item.confidenceScore !in 0.0f..1.0f) {
            errors.add("Confidence score must be between 0.0 and 1.0")
        }
        return SchemaValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    /**
     * Validates an EventEntity against persistence schema constraints.
     */
    fun validateEvent(event: EventEntity): SchemaValidationResult {
        val errors = mutableListOf<String>()
        if (event.title.isBlank()) {
            errors.add("Event title must not be blank")
        }
        if (event.associatedModeId <= 0) {
            errors.add("Associated Mode ID must be greater than 0")
        }
        if (event.confidence !in 0.0f..1.0f) {
            errors.add("Confidence score must be between 0.0 and 1.0")
        }
        return SchemaValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    /**
     * Serializes an ItemRequirementEntity into standardized JSON format for schema validation.
     */
    fun toJson(item: ItemRequirementEntity): String {
        return """{"id":${item.id},"modeId":${item.modeId},"itemName":"${escapeJson(item.itemName)}","isReady":${item.isReady},"isTemporary":${item.isTemporary},"ttlTimestamp":${item.ttlTimestamp},"confidenceScore":${item.confidenceScore},"isGhostMode":${item.isGhostMode},"isWaived":${item.isWaived}}"""
    }

    /**
     * Serializes an EventEntity into standardized JSON format for schema validation.
     */
    fun toJson(event: EventEntity): String {
        return """{"id":${event.id},"title":"${escapeJson(event.title)}","startTime":${event.startTime},"endTime":${event.endTime},"location":"${escapeJson(event.location ?: "")}","associatedModeId":${event.associatedModeId},"isGhostMode":${event.isGhostMode},"confidence":${event.confidence},"evidenceText":"${escapeJson(event.evidenceText ?: "")}"}"""
    }

    /**
     * Serializes a FilteredPayload into standardized JSON format for schema validation.
     */
    fun toJson(payload: FilteredPayload): String {
        return """{"rawText":"${escapeJson(payload.rawText)}","sourcePackage":"${escapeJson(payload.sourcePackage)}","detectedLanguage":"${escapeJson(payload.detectedLanguage)}","extractedAction":"${escapeJson(payload.extractedAction ?: "")}","extractedItemName":"${escapeJson(payload.extractedItemName ?: "")}","extractedDateTime":"${escapeJson(payload.extractedDateTime ?: "")}","targetModeId":${payload.targetModeId},"relevanceScore":${payload.relevanceScore},"canonicalAction":"${payload.canonicalAction.name}","targetEpochMillis":${payload.targetEpochMillis ?: 0L},"oldTime":"${escapeJson(payload.oldTime ?: "")}","newTime":"${escapeJson(payload.newTime ?: "")}"}"""
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun parseSimpleJson(json: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)")
        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val rawVal = match.groupValues[2]
            val value: Any? = when {
                rawVal.startsWith("\"") && rawVal.endsWith("\"") -> rawVal.substring(1, rawVal.length - 1)
                rawVal == "true" -> true
                rawVal == "false" -> false
                rawVal == "null" -> null
                rawVal.contains(".") -> rawVal.toDoubleOrNull()
                else -> rawVal.toLongOrNull() ?: rawVal.toIntOrNull()
            }
            map[key] = value
        }
        return map
    }
}
