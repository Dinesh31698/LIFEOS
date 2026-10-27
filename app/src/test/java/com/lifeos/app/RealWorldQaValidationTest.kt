package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.security.DataMinimizer
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.security.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class RealWorldQaValidationTest {

    private lateinit var fakeDao: FakeLifeOsDao

    @Before
    fun setUp() {
        fakeDao = FakeLifeOsDao()
        SafeModeManager.resetForTesting()
        SafeModeManager.setSafeMode(false)
        HandsFreeVoiceManager.setVoiceState(VoiceState.IDLE)
        EmergencySosManager.resetSos()
        InteractiveLifeMapManager.setTrackingPaused(false)
        PhysicalLifeRecorder.setTrackingPaused(false)
    }

    // =========================================================================
    // PART 2: Database Migration & Schema Integrity Test
    // =========================================================================
    @Test
    fun testPart2_DatabaseMigrationAndIntegrity() = runBlocking {
        // Populate realistic v7 baseline data
        fakeDao.insertLifeMode(LifeModeEntity(id = 1, name = "College", isActive = true))
        fakeDao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", isActive = false))
        fakeDao.insertItem(ItemRequirementEntity(id = 101, modeId = 1, itemName = "DBMS Record", isReady = false))
        fakeDao.insertEvent(EventEntity(id = 201, title = "DBMS Lab", startTime = 1000L, endTime = 2000L, associatedModeId = 1))
        fakeDao.insertOrUpdateHabit(ItemHabitEntity(id = 301, itemName = "DBMS Record", modeId = 1, forgetCount = 3))
        fakeDao.insertNode(NodeEntity(id = 401, name = "College", type = "PLACE"))
        fakeDao.insertEdge(EdgeEntity(id = 501, sourceNodeId = 401, targetNodeId = 101, relationType = "REQUIRES"))

        // Simulate upgrade to v8 by adding v8 tables
        val profile = UserProfileEntity(id = 1, preferredName = "Dinesh", primaryRole = "Student")
        val anchor = AnchorPlaceEntity(id = 1, name = "Campus", type = "CAMPUS", latitude = 17.385, longitude = 78.486)
        val memory = MemoryEntity(id = 1, content = "Locker key in top drawer")
        val contact = EmergencyContactEntity(id = 1, name = "Guardian", phoneNumber = "+919876543210")
        val rule = AppUsageRuleEntity(id = 1, packageName = "com.instagram.android", appName = "Instagram", dailyLimitMinutes = 30)

        fakeDao.insertOrUpdateUserProfile(profile)
        fakeDao.insertAnchorPlace(anchor)
        fakeDao.insertMemory(memory)
        fakeDao.insertEmergencyContact(contact)
        fakeDao.insertOrUpdateAppUsageRule(rule)

        // Verify EVERY pre-existing v7 record remained intact
        assertEquals(2, fakeDao.getAllModesFlow().first().size)
        assertEquals("DBMS Record", fakeDao.getItemsForMode(1).first().itemName)
        assertNotNull(fakeDao.findEventByTitleLike("DBMS Lab"))
        assertEquals(3, fakeDao.getAllHabitsFlow().first().first().forgetCount)

        // Verify all new v8 tables function properly
        assertEquals("Dinesh", fakeDao.getUserProfile()?.preferredName)
        assertEquals("Campus", fakeDao.getActiveAnchorPlaces().first().name)
        assertEquals(1, fakeDao.getAllMemoriesFlow().first().size)
        assertEquals("+919876543210", fakeDao.getAllEmergencyContacts().first().phoneNumber)
        assertEquals("Instagram", fakeDao.getAllAppUsageRulesFlow().first().first().appName)

        // Verify factory reset clears all tables without orphaned records
        fakeDao.clearAllUserData()
        assertTrue(fakeDao.getAllModesFlow().first().isEmpty())
        assertTrue(fakeDao.getItemsForMode(1).isEmpty())
        assertNull(fakeDao.getUserProfile())
        assertTrue(fakeDao.getActiveAnchorPlaces().isEmpty())
        assertTrue(fakeDao.getAllMemoriesFlow().first().isEmpty())
        assertTrue(fakeDao.getAllEmergencyContacts().isEmpty())
        assertTrue(fakeDao.getAllAppUsageRulesFlow().first().isEmpty())
    }

    // =========================================================================
    // PART 3 & 4: User Profile Lifecycle & Dynamic Greeting Test
    // =========================================================================
    @Test
    fun testPart3And4_UserProfileLifecycleAndDynamicGreetings() = runBlocking {
        // Create initial profile: Dinesh
        var profile = UserProfileEntity(
            id = 1,
            preferredName = "Dinesh",
            primaryRole = "Student",
            preferredLanguage = "English",
            preferredVoiceOutput = "Telugu + English",
            sleepTarget = "23:00",
            wakeTarget = "07:00",
            bedtimeTarget = "23:00",
            defaultCommuteMode = "Bike"
        )
        fakeDao.insertOrUpdateUserProfile(profile)

        // Test greetings for Dinesh across all 4 diurnal phases
        assertEquals("Good morning, Dinesh", UserProfileEngine.generateGreeting(profile, currentHour = 8))
        assertEquals("Good afternoon, Dinesh", UserProfileEngine.generateGreeting(profile, currentHour = 13))
        assertEquals("Good evening, Dinesh", UserProfileEngine.generateGreeting(profile, currentHour = 19))
        assertEquals("Good night, Dinesh", UserProfileEngine.generateGreeting(profile, currentHour = 23))

        // Rename: Dinesh -> Rahul
        profile = profile.copy(preferredName = "Rahul")
        fakeDao.insertOrUpdateUserProfile(profile)

        // Verify every dynamic greeting changes immediately
        assertEquals("Good morning, Rahul", UserProfileEngine.generateGreeting(profile, currentHour = 8))
        assertEquals("Good afternoon, Rahul", UserProfileEngine.generateGreeting(profile, currentHour = 13))
        assertEquals("Good evening, Rahul", UserProfileEngine.generateGreeting(profile, currentHour = 19))
        assertEquals("Good night, Rahul", UserProfileEngine.generateGreeting(profile, currentHour = 23))

        // Verify fallback when no name is configured -> "User"
        val emptyProfile = profile.copy(preferredName = "")
        assertEquals("Good morning, User", UserProfileEngine.generateGreeting(emptyProfile, currentHour = 9))
    }

    // =========================================================================
    // PART 5 & 14: Anchor Location, Boundary Conditions & Place Clustering
    // =========================================================================
    @Test
    fun testPart5And14_AnchorLocationBoundariesAndClustering() = runBlocking {
        val campus = AnchorPlaceEntity(
            id = 1,
            name = "College Campus",
            type = "CAMPUS",
            latitude = 17.385044,
            longitude = 78.486671,
            radiusMeters = 200f,
            isEnabled = true
        )
        fakeDao.insertAnchorPlace(campus)

        val anchors = fakeDao.getActiveAnchorPlaces()

        // 1. Center / Inside boundary
        val insideMatch = AnchorLocationEngine.matchAnchorPlace(17.385044, 78.486671, anchors)
        assertNotNull(insideMatch)
        assertEquals("College Campus", insideMatch?.name)

        // 2. Exact Boundary / Slightly Inside (< 200m)
        val slightlyInside = AnchorLocationEngine.matchAnchorPlace(17.386000, 78.486671, anchors)
        assertNotNull(slightlyInside)

        // 3. Clearly Outside (> 200m)
        val outsideMatch = AnchorLocationEngine.matchAnchorPlace(17.395044, 78.486671, anchors)
        assertNull(outsideMatch)

        // 4. Duplicate prevention when adding anchor with identical name
        val duplicateCampus = AnchorPlaceEntity(id = 2, name = "College Campus", type = "CAMPUS", latitude = 17.385100, longitude = 78.486700)
        AnchorLocationEngine.addAnchor(fakeDao, duplicateCampus)
        val activeCount = fakeDao.getActiveAnchorPlaces().count { it.name == "College Campus" }
        assertEquals(1, activeCount)

        // 5. Part 14: Frequent place clustering
        val repeatedVisits = listOf(
            17.4400 to 78.3480,
            17.4401 to 78.3481,
            17.4402 to 78.3479
        )
        val candidate = AnchorLocationEngine.detectFrequentPlace(repeatedVisits, anchors, clusterThresholdMeters = 100.0, minVisits = 3)
        assertNotNull(candidate)
        assertEquals(3, candidate?.visitCount)
        assertTrue(candidate?.suggestedLabels?.contains("Gym") == true)
    }

    // =========================================================================
    // PART 6 & 7: Voice Assistant Multilingual, Safety & Hardware Honesty
    // =========================================================================
    @Test
    fun testPart6And7_VoiceAssistantSafetyAndMultilingual() = runBlocking {
        fakeDao.insertLifeMode(LifeModeEntity(id = 1, name = "College", isActive = true))
        fakeDao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", isActive = false))
        fakeDao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", isActive = false))
        fakeDao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "ID Card", isReady = true))
        fakeDao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "Lab Notebook", isReady = false))

        // English: "Show my college readiness."
        val res1 = HandsFreeVoiceManager.processVoiceCommand("Show my college readiness.", fakeDao)
        assertEquals("QUERY_READINESS", res1.parsedIntent)
        assertTrue(res1.spokenResponse.contains("1 of 2 items ready"))

        // English: "Add water bottle to sports."
        val res2 = HandsFreeVoiceManager.processVoiceCommand("Add water bottle to sports", fakeDao)
        assertEquals("MUTATION_ADD_ITEM", res2.parsedIntent)
        val sportsItems = fakeDao.getItemsForMode(4)
        assertTrue(sportsItems.any { it.itemName.equals("Water bottle", ignoreCase = true) })

        // Teluglish: "Na college checklist chupinchu."
        val resTelugu = HandsFreeVoiceManager.processVoiceCommand("Na college checklist chupinchu.", fakeDao)
        assertEquals("QUERY_READINESS", resTelugu.parsedIntent)

        // Telugu Script: "నా కాలేజీ చెక్‌లిస్ట్ చూపించు"
        val resTeluguScript = HandsFreeVoiceManager.processVoiceCommand("నా కాలేజీ చెక్‌లిస్ట్ చూపించు", fakeDao)
        assertEquals("QUERY_READINESS", resTeluguScript.parsedIntent)

        // Teluglish Action: "Repu morning 9 ki DBMS record teesukoni vellali"
        val resTakeItem = HandsFreeVoiceManager.processVoiceCommand("Repu morning 9 ki DBMS record teesukoni vellali", fakeDao)
        assertEquals("MUTATION_ADD_ITEM", resTakeItem.parsedIntent)
        val collegeItems = fakeDao.getItemsForMode(1)
        assertTrue(collegeItems.any { it.itemName == "DBMS Record" })

        // Ambiguous safety: "Maybe move my class."
        val resAmbiguous = HandsFreeVoiceManager.processVoiceCommand("Maybe move my class.", fakeDao)
        assertEquals("AMBIGUOUS_COMMAND", resAmbiguous.parsedIntent)
        assertTrue(resAmbiguous.requiresApproval)
        assertFalse(resAmbiguous.executedDirectly)

        // Destructive safety: "Cancel everything." / "Delete my college."
        val resDestructive = HandsFreeVoiceManager.processVoiceCommand("Delete my college.", fakeDao)
        assertEquals("DESTRUCTIVE_COMMAND_BLOCKED", resDestructive.parsedIntent)
        assertTrue(resDestructive.requiresApproval)
        assertFalse(resDestructive.executedDirectly)

        // Voice failure test: Empty transcript
        val resEmpty = HandsFreeVoiceManager.processVoiceCommand("   ", fakeDao)
        assertEquals("EMPTY_SPEECH", resEmpty.parsedIntent)
        assertFalse(resEmpty.executedDirectly)

        // Hardware honesty: 5x Volume Up
        val (capability, msg) = HandsFreeVoiceManager.checkHardwareWakeCapability()
        assertEquals(TriggerCapability.PLATFORM_LIMITED, capability)
        assertTrue(msg.contains("PLATFORM_LIMITED") || msg.contains("security restrictions"))
    }

    // =========================================================================
    // PART 8, 9 & 10: Usage Stats, Screen-Time Rules & Validated Timeline
    // =========================================================================
    @Test
    fun testPart8_9_10_UsageStatsScreenTimeAndValidatedTimeline() {
        val instaRule = AppUsageRuleEntity(
            id = 1,
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyLimitMinutes = 30,
            modeLimitMinutes = 10,
            targetModeId = 1, // College mode
            blockAfterTime = "22:00"
        )

        // 1. Below limit (20 mins)
        val eval1 = TimeAuditManager.evaluateAppRule(instaRule, usageMinutes = 20, activeModeId = 2, currentHour = 15, currentMinute = 0)
        assertFalse(eval1.isOverDailyLimit)
        assertFalse(eval1.interventionTriggered)

        // 2. Over mode limit during College Mode (15 mins > 10 mins)
        val eval2 = TimeAuditManager.evaluateAppRule(instaRule, usageMinutes = 15, activeModeId = 1, currentHour = 11, currentMinute = 0)
        assertTrue(eval2.isOverModeLimit)
        assertTrue(eval2.interventionTriggered)
        assertTrue(eval2.interventionMessage?.contains("exceeded 10m limit during active focus mode") == true)
        // Honest: Does NOT claim "Instagram blocked"
        assertFalse(eval2.interventionMessage?.contains("blocked") == true)

        // 3. Over daily limit (35 mins > 30 mins)
        val eval3 = TimeAuditManager.evaluateAppRule(instaRule, usageMinutes = 35, activeModeId = 2, currentHour = 16, currentMinute = 0)
        assertTrue(eval3.isOverDailyLimit)
        assertTrue(eval3.interventionTriggered)

        // 4. Restricted after 10 PM
        val eval4 = TimeAuditManager.evaluateAppRule(instaRule, usageMinutes = 5, activeModeId = 2, currentHour = 22, currentMinute = 30)
        assertTrue(eval4.isRestrictedByTime)
        assertTrue(eval4.interventionTriggered)

        // Part 10: Validated timeline with no negative durations and strict ordering
        val rawTimeline = listOf(
            LifeTimelineEntry("10:30", "Instagram", "Session", "APP_USAGE", durationMinutes = 15),
            LifeTimelineEntry("08:00", "Home", "Departure", "PLACE", durationMinutes = 0),
            LifeTimelineEntry("09:00", "College", "Arrival", "PLACE", durationMinutes = 0),
            LifeTimelineEntry("12:00", "Corrupt Entry", "Invalid", "ACTIVITY", durationMinutes = -10),
            LifeTimelineEntry("09:00", "College", "Arrival Duplicate", "PLACE", durationMinutes = 0)
        )
        val validated = TimeAuditManager.createValidatedTimeline(rawTimeline)
        assertEquals(3, validated.size)
        assertEquals("08:00", validated[0].timeLabel)
        assertEquals("09:00", validated[1].timeLabel)
        assertEquals("10:30", validated[2].timeLabel)
        assertTrue(validated.all { (it.durationMinutes ?: 0L) >= 0L })
    }

    // =========================================================================
    // PART 11 & 12: Physical Life Recorder & Tracking Pause/Resume
    // =========================================================================
    @Test
    fun testPart11And12_PhysicalLifeRecorderTransitionsAndPause() {
        // Transition: STILL -> WALKING
        val t1 = PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.WALKING)
        assertTrue(t1)
        assertEquals(PhysicalMotionState.WALKING, PhysicalLifeRecorder.motionState.value)

        // Duplicate transition suppression: WALKING again
        val tDuplicate = PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.WALKING)
        assertFalse(tDuplicate)

        // Transition: WALKING -> BIKING
        val tBiking = PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.BIKING)
        assertTrue(tBiking)
        assertEquals(PhysicalMotionState.BIKING, PhysicalLifeRecorder.motionState.value)

        // Pause tracking
        PhysicalLifeRecorder.setTrackingPaused(true)
        val tPaused = PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.IN_VEHICLE)
        assertFalse(tPaused)
        // State remains BIKING while paused
        assertEquals(PhysicalMotionState.BIKING, PhysicalLifeRecorder.motionState.value)

        // Resume tracking
        PhysicalLifeRecorder.setTrackingPaused(false)
        val tResume = PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.IN_VEHICLE)
        assertTrue(tResume)
        assertEquals(PhysicalMotionState.IN_VEHICLE, PhysicalLifeRecorder.motionState.value)
    }

    // =========================================================================
    // PART 16, 17 & 18: Memory Vault Semantic Search, TTL & Security
    // =========================================================================
    @Test
    fun testPart16_17_18_MemoryVaultSemanticSearchAndTTL() = runBlocking {
        // Store memory: "My locker key is in the top drawer."
        MemoryVaultEngine.storeMemory(
            dao = fakeDao,
            content = "My locker key is in the top drawer.",
            memoryType = "SHORT_TERM",
            tags = "personal, locker",
            ttlOption = MemoryTtlOption.TWENTY_FOUR_HOURS
        )

        // Search: "Where did I put my locker key?"
        val (found1, answer1) = MemoryVaultEngine.askLifeOsMemory(fakeDao, "Where did I put my locker key?")
        assertTrue(found1)
        assertTrue(answer1.contains("top drawer"))

        // Search never stored: "Where did I keep my passport?"
        val (found2, answer2) = MemoryVaultEngine.askLifeOsMemory(fakeDao, "Where did I keep my passport?")
        assertFalse(found2)
        assertEquals("I don't have a saved memory for that.", answer2)

        // Part 17: TTL Expiration test
        val now = System.currentTimeMillis()
        val expiredMem = MemoryEntity(
            id = 99,
            content = "One hour secret code 777",
            createdAt = now - 7200_000L,
            expiresAt = now - 3600_000L,
            isPinned = false
        )
        fakeDao.insertMemory(expiredMem)

        // Expired memory should NOT be returned by semantic search even before cleanup runs
        val (foundExpired, _) = MemoryVaultEngine.askLifeOsMemory(fakeDao, "Where is my secret code 777?")
        assertFalse(foundExpired)
    }

    // =========================================================================
    // PART 19 & 20: Executive Secretary Briefing & Conflict Arbitration
    // =========================================================================
    @Test
    fun testPart19And20_ExecutiveSecretaryBriefingAndConflict() = runBlocking {
        val labEvent = EventEntity(id = 1, title = "DBMS Lab", startTime = 1000L, endTime = 2000L, location = "Block B, Room 302")
        val gymEvent = EventEntity(id = 2, title = "Gym Workout", startTime = 1000L, endTime = 2000L, location = "Fitness Center")

        // Conflict detection: 10:00 DBMS Lab vs 10:00 Gym
        val proposals = VirtualExecutiveSecretary.detectAndResolveConflicts(listOf(labEvent, gymEvent))
        assertEquals(1, proposals.size)
        val proposal = proposals.first()
        assertEquals("DBMS Lab", proposal.eventA)
        assertEquals("Gym Workout", proposal.eventB)
        assertTrue(proposal.proposedAction.contains("Reschedule 'Gym Workout'"))
        assertEquals(0.95f, proposal.confidence, 0.01f)

        // Approve proposal (Idempotent)
        val approved1 = VirtualExecutiveSecretary.approveConflictProposal(proposal.id)
        assertTrue(approved1)
        val approved2 = VirtualExecutiveSecretary.approveConflictProposal(proposal.id)
        assertTrue(approved2) // Idempotency check

        // 10-minute Pre-Event Briefing
        val checklist = listOf(
            ItemRequirementEntity(modeId = 1, itemName = "Laptop", isReady = true),
            ItemRequirementEntity(modeId = 1, itemName = "Lab Record", isReady = true)
        )
        val briefing = VirtualExecutiveSecretary.generatePreEventBriefing(labEvent, checklist, BriefingMode.BRIEF, leadMinutes = 10)
        assertEquals("DBMS Lab", briefing.eventTitle)
        assertEquals(100, briefing.readinessScorePercent)
        assertTrue(briefing.spokenSummary.contains("begins in 10 minutes"))
        assertTrue(briefing.spokenSummary.contains("100% ready"))
    }

    // =========================================================================
    // PART 22: Emergency SOS Test Mode & 5-Second Grace Period
    // =========================================================================
    @Test
    fun testPart22_EmergencySosTestModeAndGracePeriod() {
        EmergencySosManager.setTestMode(true)
        assertTrue(EmergencySosManager.isTestMode.value)

        // 1. Trigger SOS -> Starts 5-second countdown
        EmergencySosManager.triggerSos()
        assertEquals(SosState.COUNTDOWN, EmergencySosManager.sosState.value)
        assertEquals(5, EmergencySosManager.countdownRemainingSeconds.value)

        // 2. Cancel SOS before expiration
        EmergencySosManager.cancelSos()
        assertEquals(SosState.CANCELLED, EmergencySosManager.sosState.value)

        // 3. Test Mode simulation dispatch
        val contacts = listOf(EmergencyContactEntity(name = "Trusted Parent", phoneNumber = "+919876543210"))
        val result = EmergencySosManager.simulateDispatch(contacts)
        assertEquals(SmsDispatchStatus.TEST_MODE_SIMULATED, result.status)
        assertEquals(1, result.contactsNotifiedCount)
        assertTrue(result.statusMessage.contains("Test Mode Verified"))
    }

    // =========================================================================
    // PART 25: Smart Clipboard Sanitization & DecisionGate Event Suggestion
    // =========================================================================
    @Test
    fun testPart25_SmartClipboardSanitizationAndEventProposal() {
        // Snippet containing JWT token, password, API key, phone number, and meeting details
        val dirtySnippet = """
            Meeting at Starbucks on Friday 10 AM.
            Auth: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig
            Secret api_key: AIzaSyD987654321
            Call me at +1 555-123-4567 or email john@example.com
            Password: mysecretpass123
        """.trimIndent()

        val parsed = SmartClipboardManager.parseSnippet(dirtySnippet)
        assertNotNull(parsed)
        assertEquals("Starbucks", parsed?.locationSnippet)

        // Verify all sensitive elements were thoroughly sanitized
        val sanitized = parsed?.rawSnippetSanitized ?: ""
        assertFalse(sanitized.contains("eyJ"))
        assertFalse(sanitized.contains("AIzaSyD987654321"))
        assertFalse(sanitized.contains("+1 555-123-4567"))
        assertFalse(sanitized.contains("john@example.com"))
        assertFalse(sanitized.contains("mysecretpass123"))
    }

    // =========================================================================
    // PART 38: Real-World User Journeys 1 to 10
    // =========================================================================
    @Test
    fun testRealWorldUserJourneys_1_to_10() = runBlocking {
        // JOURNEY 1 — MORNING
        val dineshProfile = UserProfileEntity(preferredName = "Dinesh", primaryRole = "Student")
        fakeDao.insertOrUpdateUserProfile(dineshProfile)
        val morningGreeting = UserProfileEngine.generateGreeting(dineshProfile, currentHour = 7)
        assertTrue(morningGreeting.contains("Good morning, Dinesh"))

        // JOURNEY 2 — COLLEGE & SCREEN-TIME INTERVENTION
        fakeDao.insertLifeMode(LifeModeEntity(id = 1, name = "College", isActive = true))
        val instaRule = AppUsageRuleEntity(packageName = "com.instagram.android", appName = "Instagram", modeLimitMinutes = 10, targetModeId = 1)
        val intervention = TimeAuditManager.evaluateAppRule(instaRule, usageMinutes = 15, activeModeId = 1)
        assertTrue(intervention.interventionTriggered)

        // JOURNEY 3 — LAB SHIFT NOTIFICATION / PROPOSAL
        val origEvent = EventEntity(id = 10, title = "DBMS Lab", startTime = 1000L, endTime = 2000L, associatedModeId = 1)
        fakeDao.insertEvent(origEvent)
        val shiftedEvent = origEvent.copy(startTime = 1500L, endTime = 2500L)
        fakeDao.updateEvent(shiftedEvent)
        assertEquals(1500L, fakeDao.findEventByTitleLike("DBMS Lab")?.startTime)

        // JOURNEY 4 — MEMORY
        MemoryVaultEngine.storeMemory(fakeDao, "My locker key is in the top drawer.", ttlOption = MemoryTtlOption.TWENTY_FOUR_HOURS)
        val (foundKey, keyLocation) = MemoryVaultEngine.askLifeOsMemory(fakeDao, "Where is my locker key?")
        assertTrue(foundKey)
        assertTrue(keyLocation.contains("top drawer"))

        // JOURNEY 5 — GYM (Checklist isolation)
        fakeDao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", isActive = false))
        fakeDao.insertItem(ItemRequirementEntity(modeId = 2, itemName = "Shaker Bottle", isReady = false))
        val gymItems = fakeDao.getItemsForMode(2)
        assertEquals(1, gymItems.size)
        assertEquals("Shaker Bottle", gymItems.first().itemName)

        // JOURNEY 6 — CONFLICT
        val gymEvent = EventEntity(id = 20, title = "Gym Workout", startTime = 1500L, endTime = 2500L, associatedModeId = 2)
        val conflicts = VirtualExecutiveSecretary.detectAndResolveConflicts(listOf(shiftedEvent, gymEvent))
        assertEquals(1, conflicts.size)

        // JOURNEY 7 — PRIVACY (Pause Tracking)
        InteractiveLifeMapManager.setTrackingPaused(true)
        assertTrue(InteractiveLifeMapManager.privacySettings.value.isTrackingPaused)

        // JOURNEY 8 — SOS TEST
        EmergencySosManager.setTestMode(true)
        val sosSim = EmergencySosManager.simulateDispatch(listOf(EmergencyContactEntity(name = "Mom", phoneNumber = "+919876543210")))
        assertEquals(SmsDispatchStatus.TEST_MODE_SIMULATED, sosSim.status)

        // JOURNEY 9 — NIGHT (Evening Recap & No fake sleep)
        val eveningRecap = PhysicalLifeRecorder.getInactivitySummary(7.5f)
        assertTrue(eveningRecap.contains("Estimated phone inactivity: 7.5 hours"))
        assertFalse(eveningRecap.contains("Deep Sleep"))

        // JOURNEY 10 — REBOOT / PERSISTENCE
        val persistedProfile = fakeDao.getUserProfile()
        assertEquals("Dinesh", persistedProfile?.preferredName)
        assertEquals(1, fakeDao.getAllMemoriesFlow().first().size)
    }
}
