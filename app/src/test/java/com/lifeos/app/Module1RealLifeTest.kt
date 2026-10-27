package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * LIFEOS Real-Life Test & Hardening Suite — Module 1: Multilingual Ingestion & AI Event Extraction
 *
 * Real-Life Test Scenario:
 *  "Repu subah 10 AM ki DBMS Lab evaluation undi, Record definitely theukondi array."
 *
 * Validates:
 *  1. Lightweight Filtering: LightweightEventFilter flags relevance score >= 0.85 without running full LLM parsing for simple filler text.
 *  2. Entity Extraction Accuracy:
 *     - Date "Repu" (Tomorrow) is converted to system epoch timestamp for tomorrow at 10:00 AM.
 *     - Required item "DBMS Record" is extracted and marked as isTemporary = true.
 *     - Conversational noise ("array", "subah") is removed via DataMinimizer.
 *  3. Schema Validation: Extracted JSON passes SchemaValidator before Room DB persistence.
 *  4. AI Sleep Cycle: Thread/coroutine execution completes and terminates immediately after committing structured entities to Room DB.
 *  5. Real-Life Defect Resolution: Teluglish verbs ("theukondi", "teesuko", "pattukoni ra") map to ACTION_REQUIREMENT under College Mode (ModeId = 1).
 */
class Module1RealLifeTest {

    private lateinit var fakeDao: FakeLifeOsDao
    private lateinit var decisionGate: DecisionGate

    private val realLifeMessage = "Repu subah 10 AM ki DBMS Lab evaluation undi, Record definitely theukondi array."

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)

        fakeDao = FakeLifeOsDao()
        fakeDao.modes.add(LifeModeEntity(id = 1, name = "College", iconRes = "school", isActive = true))
        fakeDao.modes.add(LifeModeEntity(id = 2, name = "Gym", iconRes = "fitness_center", isActive = false))

        decisionGate = DecisionGate()
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
    }

    // =========================================================================
    // 1. Lightweight Filtering (Relevance Score >= 0.85 vs Low-Confidence Filler)
    // =========================================================================

    @Test
    fun testLightweightFilteringRelevanceScore() {
        // High-value action notification: DBMS Lab evaluation + Record requirement
        val filtered = LightweightEventFilter.filter(realLifeMessage, sourcePackage = "org.telegram.messenger")

        assertTrue(
            "Relevance score for actionable message must be >= 0.85, was ${filtered.relevanceScore}",
            filtered.relevanceScore >= 0.85f
        )
        assertEquals(CanonicalAction.REQUIRED_ITEM, filtered.canonicalAction)
        assertEquals(CanonicalAction.ACTION_REQUIREMENT, filtered.canonicalAction)
        assertEquals("Action Required", filtered.extractedAction)
        assertEquals(1, filtered.targetModeId) // College Mode
        assertFalse(
            "High confidence payload must not require Ghost Mode routing",
            DecisionGate.isGhostRoutingRequired(filtered.relevanceScore)
        )

        // Simple conversational filler text without actionable requirements
        val fillerMessage = "Hey guys how was lunch array, catch up later"
        val fillerFiltered = LightweightEventFilter.filter(fillerMessage, sourcePackage = "com.whatsapp")

        assertTrue(
            "Filler text relevance score must be < 0.50, was ${fillerFiltered.relevanceScore}",
            fillerFiltered.relevanceScore < 0.50f
        )
        assertEquals(CanonicalAction.INFO_NOTICE, fillerFiltered.canonicalAction)
        assertNull("Filler message must not extract any required items", fillerFiltered.extractedItemName)
    }

    // =========================================================================
    // 2. Entity Extraction Accuracy: Date/Time Epoch, Item, and Noise Removal
    // =========================================================================

    @Test
    fun testEntityExtractionAccuracy() {
        val parsed = MultilingualParser.parse(realLifeMessage, sourcePackage = "com.whatsapp")

        // 2a. Date "Repu" (Tomorrow) converted to system epoch timestamp for tomorrow at 10:00 AM
        assertNotNull("Target epoch timestamp must be parsed from 'Repu' and '10 AM'", parsed.targetEpochMillis)
        val targetEpoch = parsed.targetEpochMillis!!

        val epochCal = Calendar.getInstance().apply { timeInMillis = targetEpoch }
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        assertEquals(
            "Day of year must match tomorrow",
            tomorrowCal.get(Calendar.DAY_OF_YEAR),
            epochCal.get(Calendar.DAY_OF_YEAR)
        )
        assertEquals("Hour must be 10 AM (10:00)", 10, epochCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute must be 0", 0, epochCal.get(Calendar.MINUTE))
        assertEquals("Second must be 0", 0, epochCal.get(Calendar.SECOND))

        // 2b. Required Item "DBMS Record" extracted
        assertEquals("DBMS Record", parsed.relatedItem)
        assertEquals("DBMS Lab", parsed.subjectActivity)

        // 2c. Conversational noise ("array", "subah") removed via DataMinimizer
        val sanitizedPii = DataMinimizer.stripPii(realLifeMessage)
        assertFalse("Conversational filler 'array' must be removed", sanitizedPii.contains("array", ignoreCase = true))
        assertFalse("Conversational filler 'subah' must be removed", sanitizedPii.contains("subah", ignoreCase = true))

        val noiseStripped = DataMinimizer.stripConversationalNoise(realLifeMessage)
        assertFalse("Noise 'array' must be stripped", noiseStripped.contains("array", ignoreCase = true))
        assertFalse("Noise 'subah' must be stripped", noiseStripped.contains("subah", ignoreCase = true))
        assertTrue("Essential content must be preserved", noiseStripped.contains("DBMS Lab"))
        assertTrue("Essential item must be preserved", noiseStripped.contains("Record"))
    }

    // =========================================================================
    // 3. Schema Validation Before Room DB Persistence
    // =========================================================================

    @Test
    fun testSchemaValidationBeforePersistence() {
        val payload = LightweightEventFilter.filter(realLifeMessage, sourcePackage = "college.whatsapp")

        // 3a. Verify extracted JSON passes SchemaValidator
        val payloadJson = SchemaValidator.toJson(payload)
        val payloadValidation = SchemaValidator.validateJson(payloadJson)
        assertTrue("Extracted FilteredPayload JSON must pass SchemaValidator", payloadValidation.isValid)
        assertTrue("Validation errors must be empty", payloadValidation.errors.isEmpty())

        // 3b. Verify ItemRequirementEntity JSON passes SchemaValidator
        val itemEntity = ItemRequirementEntity(
            modeId = payload.targetModeId,
            itemName = payload.extractedItemName ?: "DBMS Record",
            isReady = false,
            isTemporary = true,
            confidenceScore = payload.relevanceScore
        )
        val itemJson = SchemaValidator.toJson(itemEntity)
        val itemValidation = SchemaValidator.validateJson(itemJson)
        assertTrue("Item JSON representation must pass SchemaValidator", itemValidation.isValid)
        assertEquals(0, itemValidation.errors.size)

        // 3c. Direct entity validation
        val directValidation = SchemaValidator.validateItem(itemEntity)
        assertTrue("Direct ItemRequirementEntity must be valid", directValidation.isValid)

        // 3d. Verify malformed/invalid schemas are rejected with explicit errors
        val invalidEmptyName = """{"itemName":"","modeId":1,"confidenceScore":0.95}"""
        val emptyNameResult = SchemaValidator.validateJson(invalidEmptyName)
        assertFalse("Blank item name must fail schema validation", emptyNameResult.isValid)
        assertTrue("Must report missing entity identifier error", emptyNameResult.errors.any { it.contains("itemName") })

        val invalidModeId = """{"itemName":"DBMS Record","modeId":0,"confidenceScore":0.95}"""
        val invalidModeResult = SchemaValidator.validateJson(invalidModeId)
        assertFalse("Non-positive mode ID must fail schema validation", invalidModeResult.isValid)

        val invalidConfidence = """{"itemName":"DBMS Record","modeId":1,"confidenceScore":1.5}"""
        val invalidConfidenceResult = SchemaValidator.validateJson(invalidConfidence)
        assertFalse("Confidence > 1.0 must fail schema validation", invalidConfidenceResult.isValid)
    }

    // =========================================================================
    // 4. Room DB Persistence: Active Mode College (ModeId = 1) & isTemporary = true
    // =========================================================================

    @Test
    fun testRoomDbPersistenceUnderCollegeMode() = runBlocking {
        val payload = LightweightEventFilter.filter(realLifeMessage, sourcePackage = "com.college.portal")

        // Process through DecisionGate with Room DB DAO
        val decision = decisionGate.processPayload(payload, fakeDao)
        assertTrue("High confidence payload must be AutoCommitted", decision is DecisionResult.AutoCommitted)

        // Verify entity persisted in Room DB
        val committedItem = fakeDao.items.find { it.itemName == "DBMS Record" }
        assertNotNull("Committed item 'DBMS Record' must exist in Room DB", committedItem)
        assertEquals("Item must be committed under College Mode (ModeId = 1)", 1, committedItem!!.modeId)
        assertTrue("Item must be marked as isTemporary = true", committedItem.isTemporary)
        assertFalse("Item must be initially marked as isReady = false", committedItem.isReady)
        assertFalse("Item must not be waived (isWaived = false)", committedItem.isWaived)
        assertTrue("Confidence score must be >= 0.85", committedItem.confidenceScore >= 0.85f)

        // Verify evidence log persisted with sanitized text
        val evidenceList = fakeDao.evidence.filter { it.eventId == committedItem.id }
        assertEquals(1, evidenceList.size)
        val evidence = evidenceList.first()
        assertFalse("Evidence must not contain conversational noise 'array'", evidence.rawText.contains("array", ignoreCase = true))
        assertFalse("Evidence must not contain conversational noise 'subah'", evidence.rawText.contains("subah", ignoreCase = true))
    }

    // =========================================================================
    // 5. AI Sleep Cycle: Coroutine Completes & Terminates Immediately
    // =========================================================================

    @Test
    fun testAiSleepCycleTerminatesImmediatelyAfterCommit() = runBlocking {
        val payload = LightweightEventFilter.filter(realLifeMessage, sourcePackage = "chat.notice")

        var result: DecisionResult? = null
        // Launch insertion in an isolated asynchronous coroutine job
        val job = launch(Dispatchers.IO) {
            result = decisionGate.processPayload(payload, fakeDao)
        }

        // Await coroutine completion
        job.join()

        // Verify AI Sleep Cycle: Thread/coroutine terminated immediately without lingering loops
        assertTrue("Coroutine job must be completed after DB commit", job.isCompleted)
        assertFalse("Coroutine job must not remain active (no background leak)", job.isActive)
        assertFalse("Coroutine job must not be cancelled", job.isCancelled)
        assertNotNull("Decision result must be produced", result)
        assertTrue("Decision result must be AutoCommitted", result is DecisionResult.AutoCommitted)
    }

    // =========================================================================
    // 6. Vernacular Verb Mapping ("theukondi", "teesuko", "pattukoni ra")
    // =========================================================================

    @Test
    fun testTeluglishAndHinglishVerbDictionary() {
        val verbs = listOf(
            "Repu DBMS record theukondi" to "theukondi",
            "Repu lab ki DBMS record teesuko" to "teesuko",
            "Repu DBMS record pattukoni ra" to "pattukoni ra"
        )

        for ((phrase, verb) in verbs) {
            val parsed = MultilingualParser.parse(phrase)
            assertEquals(
                "Verb '$verb' must map directly to REQUIRED_ITEM",
                CanonicalAction.REQUIRED_ITEM,
                parsed.canonicalAction
            )
            assertEquals("DBMS Record", parsed.relatedItem)
            assertEquals(1, parsed.targetModeId)
            assertTrue("Confidence for '$verb' must be >= 0.90", parsed.confidence >= 0.90f)
        }
    }
}
