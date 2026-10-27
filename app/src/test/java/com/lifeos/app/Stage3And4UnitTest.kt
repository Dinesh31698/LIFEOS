package com.lifeos.app

import com.lifeos.app.data.ItemRequirementEntity
import com.lifeos.app.data.LifeModeEntity
import com.lifeos.app.engine.CanonicalAction
import com.lifeos.app.engine.MultilingualParser
import com.lifeos.app.engine.ReadinessEngine
import com.lifeos.app.service.AntiSpamNotificationEngine
import org.junit.Assert.*
import org.junit.Test

class Stage3And4UnitTest {

    @Test
    fun testTeluglishRequiredItemParsing() {
        val input = "Repu lab undi record marchipoku"
        val result = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.REQUIRED_ITEM, result.canonicalAction)
        assertEquals("Teluglish", result.detectedLanguage)
        assertEquals("DBMS Record", result.relatedItem)
        assertTrue(result.confidence >= 0.80f)
    }

    @Test
    fun testTeluglishCancellationParsing() {
        val input = "DBMS lab cancel aindi"
        val result = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.EVENT_CANCELLED, result.canonicalAction)
        assertEquals("Teluglish", result.detectedLanguage)
        assertEquals("DBMS Lab", result.subjectActivity)
        assertEquals("DBMS Record", result.relatedItem)
    }

    @Test
    fun testHinglishRequiredItemParsing() {
        val input = "Kal lab hai record mat bhoolna"
        val result = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.REQUIRED_ITEM, result.canonicalAction)
        assertEquals("Hinglish", result.detectedLanguage)
        assertEquals("DBMS Record", result.relatedItem)
        assertTrue(result.confidence >= 0.80f)
    }

    @Test
    fun testHinglishCancellationParsing() {
        val input = "DBMS lab cancel ho gayi"
        val result = MultilingualParser.parse(input)

        assertEquals(CanonicalAction.EVENT_CANCELLED, result.canonicalAction)
        assertEquals("Hinglish", result.detectedLanguage)
        assertEquals("DBMS Lab", result.subjectActivity)
        assertEquals("DBMS Record", result.relatedItem)
    }

    @Test
    fun testReschedulingParsing() {
        val inputHinglish = "DBMS lab reschedule ho gaya"
        val resultHinglish = MultilingualParser.parse(inputHinglish)
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, resultHinglish.canonicalAction)

        val inputTeluglish = "DBMS lab repatiki marindi"
        val resultTeluglish = MultilingualParser.parse(inputTeluglish)
        assertEquals(CanonicalAction.EVENT_RESCHEDULED, resultTeluglish.canonicalAction)
    }

    @Test
    fun testReadinessWithWaivedItem() {
        val mode = LifeModeEntity(id = 1, name = "College")
        val items = listOf(
            ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true),
            ItemRequirementEntity(id = 2, modeId = 1, itemName = "DBMS Record", isReady = false, isWaived = false),
            ItemRequirementEntity(id = 3, modeId = 1, itemName = "Laptop", isReady = false),
            ItemRequirementEntity(id = 4, modeId = 1, itemName = "Water Bottle", isReady = false)
        )

        // 1 ready out of 4 active = 25%
        val reportBefore = ReadinessEngine.calculateReadiness(mode, items)
        assertEquals(25, reportBefore.percentage)
        assertEquals(4, reportBefore.totalCount)
        assertEquals(1, reportBefore.readyCount)
        assertEquals(0, reportBefore.waivedCount)

        // Waive DBMS Record due to Lab Cancellation
        val itemsAfterCancellation = items.map {
            if (it.id == 2) it.copy(isWaived = true) else it
        }

        // 1 ready out of 3 active = 33%
        val reportAfter = ReadinessEngine.calculateReadiness(mode, itemsAfterCancellation)
        assertEquals(33, reportAfter.percentage)
        assertEquals(3, reportAfter.totalCount)
        assertEquals(1, reportAfter.readyCount)
        assertEquals(1, reportAfter.waivedCount)
        assertFalse(reportAfter.pendingItems.contains("DBMS Record"))

        // Pack remaining 2 items
        val allPacked = itemsAfterCancellation.map {
            if (!it.isWaived) it.copy(isReady = true) else it
        }
        val reportFinal = ReadinessEngine.calculateReadiness(mode, allPacked)
        assertEquals(100, reportFinal.percentage)
        assertTrue(reportFinal.pendingItems.isEmpty())
    }

    @Test
    fun testAntiSpamAlertSuppression() {
        // At 100% readiness, alerts must be suppressed
        val (suppressed100, reason100) = AntiSpamNotificationEngine.shouldSuppressAlert(
            modeName = "College",
            readinessPercentage = 100,
            alertKey = "college_reminder"
        )
        assertTrue(suppressed100)
        assertTrue(reason100.contains("100% readiness"))

        // Below 100%, alerts should be allowed initially
        AntiSpamNotificationEngine.resetCooldowns()
        val (suppressed50, _) = AntiSpamNotificationEngine.shouldSuppressAlert(
            modeName = "College",
            readinessPercentage = 50,
            alertKey = "college_reminder"
        )
        assertFalse(suppressed50)
    }

    @Test
    fun testAntiSpamDepartureConsolidation() {
        val singleItem = AntiSpamNotificationEngine.consolidateDepartureAlert("College", listOf("ID Card"))
        assertTrue(singleItem.contains("Don't forget your ID Card"))

        val twoItems = AntiSpamNotificationEngine.consolidateDepartureAlert("College", listOf("Laptop", "Water Bottle"))
        assertTrue(twoItems.contains("Don't forget: Laptop and Water Bottle"))

        val multiItems = AntiSpamNotificationEngine.consolidateDepartureAlert("College", listOf("Laptop", "Water Bottle", "ID Card", "Charger"))
        assertTrue(multiItems.contains("+ 2 more items"))
    }
}
