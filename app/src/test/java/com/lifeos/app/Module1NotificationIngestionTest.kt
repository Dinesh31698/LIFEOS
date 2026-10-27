package com.lifeos.app

import com.lifeos.app.engine.*
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * LIFEOS Module 1: Notification Ingestion & Multilingual NLP Engine Test Suite.
 * Validates:
 *  1. Vernacular corpus extraction across English, Teluglish, and Hinglish (>= 0.90 confidence).
 *  2. Context and mode assignment (College Mode modeId = 1).
 *  3. Ambiguity / uncertainty detection and Ghost Mode thresholding [0.50 - 0.84].
 *  4. Permission Firewall gating for Notification Ingestion.
 *  5. Autonomous action suppression under Safe Mode.
 */
class Module1NotificationIngestionTest {

    @Before
    fun setUp() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.LOCATION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        PermissionFirewall.setChannelAuthorized(SourceType.BLE_SCANNING, true)
        PermissionFirewall.setChannelAuthorized(SourceType.CALENDAR_SYNC, false)
    }

    @After
    fun tearDown() {
        SafeModeManager.setSafeMode(false)
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
    }

    // =========================================================================
    // 1. High-Confidence Vernacular Parsing (>= 0.90) & Mode Assignment
    // =========================================================================

    @Test
    fun testTeluglishHighConfidenceExpressions() {
        // Expression 1: "Repu lab undi record marchipoku"
        val expr1 = "Repu lab undi record marchipoku"
        val parsed1 = MultilingualParser.parse(expr1)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed1.canonicalAction)
        assertEquals("Teluglish", parsed1.detectedLanguage)
        assertEquals("DBMS Record", parsed1.relatedItem)
        assertEquals(1, parsed1.targetModeId) // College Mode
        assertTrue("Teluglish Expr 1 confidence must be >= 0.90, was ${parsed1.confidence}", parsed1.confidence >= 0.90f)

        val filtered1 = LightweightEventFilter.filter(expr1, "com.whatsapp")
        assertTrue("Filter relevance must be >= 0.90", filtered1.relevanceScore >= 0.90f)
        assertFalse("High confidence should auto-commit when Safe Mode is OFF", DecisionGate.isGhostRoutingRequired(filtered1.relevanceScore))

        // Expression 2: "DBMS record teesuko"
        val expr2 = "DBMS record teesuko"
        val parsed2 = MultilingualParser.parse(expr2)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed2.canonicalAction)
        assertEquals("Teluglish", parsed2.detectedLanguage)
        assertEquals("DBMS Record", parsed2.relatedItem)
        assertEquals(1, parsed2.targetModeId)
        assertTrue("Teluglish Expr 2 confidence must be >= 0.90, was ${parsed2.confidence}", parsed2.confidence >= 0.90f)

        // Expression 3: "Lab ki book theeyi"
        val expr3 = "Lab ki book theeyi"
        val parsed3 = MultilingualParser.parse(expr3)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed3.canonicalAction)
        assertEquals("Teluglish", parsed3.detectedLanguage)
        assertNotNull("Related item should be extracted", parsed3.relatedItem)
        assertEquals(1, parsed3.targetModeId)
        assertTrue("Teluglish Expr 3 confidence must be >= 0.90, was ${parsed3.confidence}", parsed3.confidence >= 0.90f)
    }

    @Test
    fun testHinglishHighConfidenceExpressions() {
        // Expression 1: "Kal lab hai record mat bhoolna"
        val expr1 = "Kal lab hai record mat bhoolna"
        val parsed1 = MultilingualParser.parse(expr1)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed1.canonicalAction)
        assertEquals("Hinglish", parsed1.detectedLanguage)
        assertEquals("DBMS Record", parsed1.relatedItem)
        assertEquals(1, parsed1.targetModeId)
        assertTrue("Hinglish Expr 1 confidence must be >= 0.90, was ${parsed1.confidence}", parsed1.confidence >= 0.90f)

        val filtered1 = LightweightEventFilter.filter(expr1, "com.whatsapp")
        assertTrue("Hinglish Expr 1 filter relevance must be >= 0.90", filtered1.relevanceScore >= 0.90f)
        assertFalse("High confidence Hinglish alert must auto-commit", DecisionGate.isGhostRoutingRequired(filtered1.relevanceScore))

        // Expression 2: "DBMS record laana hai"
        val expr2 = "DBMS record laana hai"
        val parsed2 = MultilingualParser.parse(expr2)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed2.canonicalAction)
        assertEquals("Hinglish", parsed2.detectedLanguage)
        assertEquals("DBMS Record", parsed2.relatedItem)
        assertEquals(1, parsed2.targetModeId)
        assertTrue("Hinglish Expr 2 confidence must be >= 0.90, was ${parsed2.confidence}", parsed2.confidence >= 0.90f)
    }

    @Test
    fun testEnglishHighConfidenceExpression() {
        val expr = "Please remember to bring your DBMS Record for lab tomorrow"
        val parsed = MultilingualParser.parse(expr)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed.canonicalAction)
        assertEquals("English", parsed.detectedLanguage)
        assertEquals("DBMS Record", parsed.relatedItem)
        assertEquals(1, parsed.targetModeId)
        assertTrue("English alert confidence must be >= 0.90, was ${parsed.confidence}", parsed.confidence >= 0.90f)

        val filtered = LightweightEventFilter.filter(expr, "com.google.android.gm")
        assertFalse("High confidence alert must auto-commit", DecisionGate.isGhostRoutingRequired(filtered.relevanceScore))
    }

    // =========================================================================
    // 2. Ambiguity / Uncertainty & Ghost Mode Routing (0.50 - 0.84)
    // =========================================================================

    @Test
    fun testAmbiguousExpressionRoutesToGhostMode() {
        // Expression: "Maybe remember record notes"
        val expr = "Maybe remember record notes"
        val parsed = MultilingualParser.parse(expr)
        assertEquals(CanonicalAction.REQUIRED_ITEM, parsed.canonicalAction)
        assertTrue(
            "Ambiguous expression confidence must be in Ghost Mode range [0.50, 0.84], but was ${parsed.confidence}",
            parsed.confidence in 0.50f..0.84f
        )

        val filtered = LightweightEventFilter.filter(expr, "com.telegram.messenger")
        assertTrue(
            "Filtered relevance must stay in Ghost Mode range [0.50, 0.84], but was ${filtered.relevanceScore}",
            filtered.relevanceScore in 0.50f..0.84f
        )

        // Decision Gate check: Must require Ghost Mode routing
        val requiresGhost = DecisionGate.isGhostRoutingRequired(filtered.relevanceScore)
        assertTrue("Ambiguous expression must route to Ghost Mode (requiresGhost=true)", requiresGhost)
    }

    @Test
    fun testVernacularUncertaintyExpressionsRouteToGhostMode() {
        // Teluglish uncertainty with "anukunta" or "doubt"
        val teluglishUncertain = "Repu lab record teesukovalemo anukunta"
        val parsedTel = MultilingualParser.parse(teluglishUncertain)
        assertTrue(
            "Teluglish uncertainty must fall in Ghost Mode range [0.50, 0.84], was ${parsedTel.confidence}",
            parsedTel.confidence in 0.50f..0.84f
        )

        // Hinglish uncertainty with "shayad"
        val hinglishUncertain = "Shayad DBMS record laana hai"
        val parsedHin = MultilingualParser.parse(hinglishUncertain)
        assertTrue(
            "Hinglish uncertainty must fall in Ghost Mode range [0.50, 0.84], was ${parsedHin.confidence}",
            parsedHin.confidence in 0.50f..0.84f
        )
    }

    // =========================================================================
    // 3. Permission Firewall Ingestion Channel Gating
    // =========================================================================

    @Test
    fun testPermissionFirewallBlocksNotificationIngestionWhenDisabled() {
        // Channel starts authorized
        assertTrue(
            "Notification Ingestion must be authorized by default",
            PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION)
        )

        // User disables Notification Ingestion via Privacy Dashboard / Settings
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, false)
        assertFalse(
            "Disabled Notification Ingestion must be blocked by Permission Firewall",
            PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION)
        )

        // Re-enabling restores authorization
        PermissionFirewall.setChannelAuthorized(SourceType.NOTIFICATION_INGESTION, true)
        assertTrue(
            "Re-enabled Notification Ingestion must be authorized",
            PermissionFirewall.isSourceAuthorized(SourceType.NOTIFICATION_INGESTION)
        )
    }

    // =========================================================================
    // 4. Safe Mode Autonomous Action Suppression
    // =========================================================================

    @Test
    fun testSafeModeForcesGhostRoutingEvenForHighConfidence() {
        val highConfidence = 0.98f

        // When Safe Mode is OFF, high confidence auto-commits
        assertFalse(
            "High confidence must auto-commit when Safe Mode is OFF",
            DecisionGate.isGhostRoutingRequired(highConfidence)
        )

        // Engage Safe Mode
        SafeModeManager.setSafeMode(true)
        assertTrue(
            "High confidence must be forced to Ghost Mode when Safe Mode is ON",
            DecisionGate.isGhostRoutingRequired(highConfidence)
        )

        // Disengage Safe Mode
        SafeModeManager.setSafeMode(false)
        assertFalse(
            "Disengaging Safe Mode restores normal auto-commit threshold",
            DecisionGate.isGhostRoutingRequired(highConfidence)
        )
    }

    // =========================================================================
    // 5. Irrelevant Chatter & Low-Confidence Drop (< 0.50)
    // =========================================================================

    @Test
    fun testIrrelevantChatterDropped() {
        val chatter = "Hey bro let's catch up tonight for dinner"
        val parsed = MultilingualParser.parse(chatter)
        assertEquals(CanonicalAction.INFO_NOTICE, parsed.canonicalAction)
        assertNull("No item should be extracted from casual chatter", parsed.relatedItem)

        val filtered = LightweightEventFilter.filter(chatter, "com.whatsapp")
        assertEquals(CanonicalAction.INFO_NOTICE, filtered.canonicalAction)
        assertNull("No item should be extracted by event filter", filtered.extractedItemName)
    }
}
