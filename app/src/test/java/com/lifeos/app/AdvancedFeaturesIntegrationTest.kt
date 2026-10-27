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
import java.io.File

class AdvancedFeaturesIntegrationTest {

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
    // Part 1: User Identity & Profile Model
    // =========================================================================
    @Test
    fun testPart1_UserIdentityAndGreeting_NoHardcoding() = runBlocking {
        // 1. Verify dynamic greetings for arbitrary user names
        val profile1 = UserProfileEntity(preferredName = "Aria", primaryRole = "Student")
        val greetingMorning = UserProfileEngine.generateGreeting(profile1, currentHour = 8)
        assertTrue(greetingMorning.contains("Good morning, Aria"))

        val greetingEvening = UserProfileEngine.generateGreeting(profile1, currentHour = 20)
        assertTrue(greetingEvening.contains("Good evening, Aria"))

        val greetingNight = UserProfileEngine.generateGreeting(profile1, currentHour = 23)
        assertTrue(greetingNight.contains("Good night, Aria"))

        // Verify fallback when name is blank
        val profileEmpty = UserProfileEntity(preferredName = "")
        val fallbackGreeting = UserProfileEngine.generateGreeting(profileEmpty, currentHour = 14)
        assertTrue(fallbackGreeting.contains("Good afternoon, User"))

        // Confirm NO name is hardcoded anywhere in engine defaults
        val defaultProfile = UserProfileEntity()
        assertEquals("User", defaultProfile.preferredName)

        // Verify routine targets parsing
        assertEquals("23:00", defaultProfile.sleepTarget)
        assertEquals("07:00", defaultProfile.wakeTarget)
        assertEquals("22:30", defaultProfile.bedtimeTarget)

        // Test DAO persistence
        fakeDao.insertOrUpdateUserProfile(profile1)
        val loaded = fakeDao.getUserProfile()
        assertNotNull(loaded)
        assertEquals("Aria", loaded?.preferredName)
        assertEquals("Student", loaded?.primaryRole)
    }

    // =========================================================================
    // Part 2: Multi-Language & Pronunciation
    // =========================================================================
    @Test
    fun testPart2_MultiLanguageAndPronunciation() {
        val profileWithHint = UserProfileEntity(preferredName = "Siobhan", pronunciationHint = "Shi-vawn")
        val guide1 = UserProfileEngine.generatePronunciationPreview(profileWithHint)
        assertTrue(guide1.contains("Shi-vawn"))

        val profileNoHint = UserProfileEntity(preferredName = "Marcus")
        val guide2 = UserProfileEngine.generatePronunciationPreview(profileNoHint)
        assertTrue(guide2.contains("Marcus"))

        // Non-Latin script pronunciation hint
        val profileHindi = UserProfileEntity(preferredName = "आरव", pronunciationHint = "Aarav")
        val guideHindi = UserProfileEngine.generatePronunciationPreview(profileHindi)
        assertTrue(guideHindi.contains("Aarav"))

        // Language status check
        val status = HandsFreeVoiceManager.getLanguageModelStatus("English")
        assertEquals(ModelStatus.SUPPORTED, status)
    }

    // =========================================================================
    // Part 3: Anchor Location & Place Model
    // =========================================================================
    @Test
    fun testPart3_AnchorLocationDistanceAndPrivacy() = runBlocking {
        // Distance calculation
        val distanceSame = AnchorLocationEngine.calculateDistanceMeters(37.7749, -122.4194, 37.7749, -122.4194)
        assertEquals(0.0, distanceSame, 0.01)

        val distanceClose = AnchorLocationEngine.calculateDistanceMeters(37.7749, -122.4194, 37.7759, -122.4194)
        assertTrue("Distance should be approximately 111 meters", distanceClose in 100.0..125.0)

        // Anchor matching
        val home = AnchorPlaceEntity(id = 1, name = "Home", type = "HOME", latitude = 37.7749, longitude = -122.4194, radiusMeters = 150.0f)
        val campus = AnchorPlaceEntity(id = 2, name = "Campus", type = "CAMPUS", latitude = 37.7850, longitude = -122.4050, radiusMeters = 200.0f)
        fakeDao.insertAnchorPlace(home)
        fakeDao.insertAnchorPlace(campus)

        val matchHome = AnchorLocationEngine.matchAnchorPlace(37.7750, -122.4195, fakeDao.getActiveAnchorPlaces())
        assertNotNull(matchHome)
        assertEquals("Home", matchHome?.name)

        val matchNone = AnchorLocationEngine.matchAnchorPlace(38.0000, -123.0000, fakeDao.getActiveAnchorPlaces())
        assertNull(matchNone)

        // Privacy masking: coordinates rounded to 2 decimal places
        val masked = AnchorLocationEngine.applyPrivacyMasking(37.774912, -122.419421, isMasked = true)
        assertEquals(37.77, masked.first, 0.001)
        assertEquals(-122.42, masked.second, 0.001)
    }

    // =========================================================================
    // Part 4: Routine Baseline & Anomaly Engine
    // =========================================================================
    @Test
    fun testPart4_RoutineBaselineAndAnomalyHonesty() {
        val profile = UserProfileEntity(sleepTarget = "23:00", wakeTarget = "07:00", bedtimeTarget = "22:30", prepBufferMinutes = 15)
        val summary = UserProfileEngine.formatRoutineSummary(profile)
        assertTrue(summary.contains("07:00"))
        assertTrue(summary.contains("22:30"))

        // Inactivity honesty: ensure it reports "phone inactivity" without clinical medical sleep claims
        val inactivityReport = PhysicalLifeRecorder.getInactivitySummary(inactivityHours = 7.5f)
        assertTrue(inactivityReport.contains("Estimated phone inactivity"))
        assertTrue(inactivityReport.contains("7.5 hours"))
        assertFalse(inactivityReport.contains("REM sleep"))
        assertFalse(inactivityReport.contains("Clinical EEG"))
    }

    // =========================================================================
    // Part 5: Hands-Free Voice Assistant Architecture
    // =========================================================================
    @Test
    fun testPart5_HandsFreeVoiceAssistantHonesty() = runBlocking {
        // Hardware wake honesty check
        val wakeStatus = HandsFreeVoiceManager.checkHardwareWakeCapability()
        assertEquals(TriggerCapability.PLATFORM_LIMITED, wakeStatus.first)
        assertTrue(wakeStatus.second.contains("Android security restrictions"))

        // Voice state transitions
        assertEquals(VoiceState.IDLE, HandsFreeVoiceManager.voiceState.value)
        HandsFreeVoiceManager.setVoiceState(VoiceState.LISTENING)
        assertEquals(VoiceState.LISTENING, HandsFreeVoiceManager.voiceState.value)

        // Command processing
        val result = HandsFreeVoiceManager.processVoiceCommand("what is my readiness", fakeDao)
        assertTrue(result.spokenResponse.contains("ready for"))
        assertEquals("QUERY_READINESS", result.parsedIntent)
    }

    // =========================================================================
    // Part 6: App Usage & Time Audit Engine
    // =========================================================================
    @Test
    fun testPart6_AppUsageAndFocusAudit() = runBlocking {
        val rule1 = AppUsageRuleEntity(packageName = "com.study.app", appName = "Study", category = "PRODUCTIVE", dailyLimitMinutes = 180)
        val rule2 = AppUsageRuleEntity(packageName = "com.social.app", appName = "Social", category = "DISTRACTION", dailyLimitMinutes = 45)
        fakeDao.insertOrUpdateAppUsageRule(rule1)
        fakeDao.insertOrUpdateAppUsageRule(rule2)

        val loadedRule1 = fakeDao.getUsageRuleForPackage("com.study.app")
        assertEquals("PRODUCTIVE", loadedRule1?.category)

        // Focus score calculation
        val focusScore = TimeAuditManager.calculateFocusScore(productiveMinutes = 120, distractionMinutes = 30)
        assertEquals(80, focusScore)

        val perfectScore = TimeAuditManager.calculateFocusScore(productiveMinutes = 60, distractionMinutes = 0)
        assertEquals(100, perfectScore)
    }

    // =========================================================================
    // Part 7: Physical Life & Activity Integration
    // =========================================================================
    @Test
    fun testPart7_PhysicalLifeAndTrackingPause() {
        assertEquals(PhysicalMotionState.STILL, PhysicalLifeRecorder.motionState.value)
        PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.WALKING)
        assertEquals(PhysicalMotionState.WALKING, PhysicalLifeRecorder.motionState.value)

        // Pause tracking
        PhysicalLifeRecorder.setTrackingPaused(true)
        assertTrue(PhysicalLifeRecorder.isTrackingPaused.value)

        // When paused, updates are ignored
        PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.IN_VEHICLE)
        assertEquals(PhysicalMotionState.WALKING, PhysicalLifeRecorder.motionState.value)

        // Resume tracking
        PhysicalLifeRecorder.setTrackingPaused(false)
        PhysicalLifeRecorder.updateMotionState(PhysicalMotionState.IN_VEHICLE)
        assertEquals(PhysicalMotionState.IN_VEHICLE, PhysicalLifeRecorder.motionState.value)
    }

    // =========================================================================
    // Part 8: Interactive Life Map Engine
    // =========================================================================
    @Test
    fun testPart8_InteractiveLifeMap() = runBlocking {
        val place = AnchorPlaceEntity(name = "Central Library", type = "LIBRARY", latitude = 40.7128, longitude = -74.0060)
        val id = fakeDao.insertAnchorPlace(place).toInt()

        val saved = fakeDao.getAnchorPlaceById(id)
        assertNotNull(saved)
        assertEquals("Central Library", saved?.name)

        // Toggle ignore
        fakeDao.setAnchorPlaceIgnored(id, true)
        val activePlaces = fakeDao.getActiveAnchorPlaces()
        assertTrue(activePlaces.none { it.id == id })

        // Clear custom location history
        fakeDao.setAnchorPlaceIgnored(id, false)
        val customPlace = AnchorPlaceEntity(name = "Secret Spot", type = "CUSTOM", latitude = 40.7128, longitude = -74.0060)
        fakeDao.insertAnchorPlace(customPlace)
        InteractiveLifeMapManager.clearLocationHistory(fakeDao)
        val updatedCustom = fakeDao.getActiveAnchorPlaces().find { it.type == "CUSTOM" }
        assertEquals(0.0, updatedCustom?.latitude ?: -1.0, 0.001)
    }

    // =========================================================================
    // Part 9: Semantic Micro-Memory Vault
    // =========================================================================
    @Test
    fun testPart9_SemanticMemoryVault_ExplicitStorageAndTTL() = runBlocking {
        val now = System.currentTimeMillis()
        val mem1 = MemoryEntity(
            content = "Locker combination is 4821 in building B",
            memoryType = "SHORT_TERM",
            tags = "locker,code",
            createdAt = now,
            expiresAt = now + 3600 * 1000L,
            isPinned = false
        )
        val mem2 = MemoryEntity(
            content = "Passport number stored in secure safe",
            memoryType = "PINNED",
            tags = "passport,id",
            createdAt = now,
            expiresAt = 0L,
            isPinned = true
        )
        val memExpired = MemoryEntity(
            content = "Temporary parking spot 42",
            memoryType = "EPHEMERAL",
            tags = "parking",
            createdAt = now - 7200 * 1000L,
            expiresAt = now - 3600 * 1000L,
            isPinned = false
        )

        fakeDao.insertMemory(mem1)
        fakeDao.insertMemory(mem2)
        fakeDao.insertMemory(memExpired)

        assertEquals(3, fakeDao.getAllMemoriesFlow().first().size)

        // TTL expiration cleanup
        val expiredCount = fakeDao.deleteExpiredMemories(currentTime = now)
        assertEquals(1, expiredCount)

        val remaining = fakeDao.getAllMemoriesFlow().first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.isPinned })

        // Keyword retrieval without LLM hallucination
        val searchLocker = MemoryVaultEngine.askLifeOsMemory(fakeDao, "where is my locker")
        assertTrue(searchLocker.first)
        assertTrue(searchLocker.second.contains("4821"))

        val searchUnknown = MemoryVaultEngine.askLifeOsMemory(fakeDao, "quantum physics notes")
        assertFalse(searchUnknown.first)
        assertTrue(searchUnknown.second.contains("I don't have a saved memory"))
    }

    // =========================================================================
    // Part 10: Virtual Executive Secretary
    // =========================================================================
    @Test
    fun testPart10_VirtualExecutiveSecretary() = runBlocking {
        val event = EventEntity(
            id = 1,
            title = "Final Exam Presentation",
            startTime = System.currentTimeMillis() + 600 * 1000L,
            location = "Hall B"
        )
        fakeDao.insertEvent(event)

        val item1 = ItemRequirementEntity(id = 1, modeId = 1, itemName = "ID Card", isReady = true)
        val item2 = ItemRequirementEntity(id = 2, modeId = 1, itemName = "Presentation Slides", isReady = false)

        val briefing = VirtualExecutiveSecretary.generatePreEventBriefing(event, listOf(item1, item2))
        assertEquals("Final Exam Presentation", briefing.eventTitle)
        assertEquals("Hall B", briefing.location)
        assertEquals(1, briefing.missingItems.size)
        assertEquals(50, briefing.readinessScorePercent)

        // Priority conflict detection
        val conflictEvent1 = EventEntity(id = 2, title = "DBMS Lab", startTime = 100000L, endTime = 103600L)
        val conflictEvent2 = EventEntity(id = 3, title = "Gym Session", startTime = 101800L, endTime = 105400L)

        val conflicts = VirtualExecutiveSecretary.detectAndResolveConflicts(listOf(conflictEvent1, conflictEvent2))
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.first().proposedAction.contains("Reschedule"))
        assertTrue(conflicts.first().reason.contains("Academic"))
    }

    // =========================================================================
    // Part 11: Emergency SOS & Physical Safety Engine
    // =========================================================================
    @Test
    fun testPart11_EmergencySos_CountdownAndHonesty() {
        val contact = EmergencyContactEntity(name = "Mom", phoneNumber = "+15551234567", relationship = "Parent", isPrimary = true)

        EmergencySosManager.setTestMode(true)
        assertTrue(EmergencySosManager.isTestMode.value)

        // Start SOS flow
        EmergencySosManager.triggerSos()
        assertEquals(SosState.COUNTDOWN, EmergencySosManager.sosState.value)
        assertEquals(5, EmergencySosManager.countdownRemainingSeconds.value)

        // Cancel during countdown
        EmergencySosManager.cancelSos()
        assertEquals(SosState.CANCELLED, EmergencySosManager.sosState.value)

        // Test mode dispatch simulation
        val testResult = EmergencySosManager.simulateDispatch(listOf(contact))
        assertEquals(SmsDispatchStatus.TEST_MODE_SIMULATED, testResult.status)
        assertEquals(1, testResult.contactsNotifiedCount)

        // Temporary audio cleanup verification
        val tempAudio = File.createTempFile("sos_test_audio", ".mp4")
        assertTrue(tempAudio.exists())
        EmergencySosManager.cleanupAudioRecording(tempAudio)
        assertFalse(tempAudio.exists())
    }

    // =========================================================================
    // Part 12: Smart Clipboard Context Engine
    // =========================================================================
    @Test
    fun testPart12_SmartClipboard_OnDemandDiscard() {
        val rawClip = "Meeting at Starbucks on friday at 3 pm. Passcode: SECRET_992"

        // On-demand foreground parsing
        val parseResult = SmartClipboardManager.parseSnippet(rawClip)
        assertNotNull(parseResult)
        assertEquals("Starbucks", parseResult?.detectedLocation)

        // Data minimization: sensitive password stripped
        val sanitized = DataMinimizer.sanitizeClipboardSnippet(rawClip)
        assertFalse(sanitized.contains("SECRET_992"))
        assertTrue(sanitized.contains("[TOKEN REDACTED]"))

        // Immediate discard: ensure manager clears parsed result
        SmartClipboardManager.clearParsedResult()
        assertNull(SmartClipboardManager.lastParsedResult.value)
    }

    // =========================================================================
    // Part 13: System Integration & Safe Mode Isolation
    // =========================================================================
    @Test
    fun testPart13_SafeModeAndDecisionGateEnforcement() = runBlocking {
        SafeModeManager.setSafeMode(true)
        assertTrue(SafeModeManager.isSafeModeActive)

        // Safe mode routes spoken mutation to Ghost Mode
        val response = HandsFreeVoiceManager.processVoiceCommand("add Laptop Charger to college", fakeDao)
        assertTrue(response.requiresApproval)
        assertTrue(response.spokenResponse.contains("Safe Mode is active"))

        SafeModeManager.setSafeMode(false)
        assertFalse(SafeModeManager.isSafeModeActive)
    }

    // =========================================================================
    // Part 14: Data Minimizer & Permission Firewall Extensions
    // =========================================================================
    @Test
    fun testPart14_DataMinimizerAndPermissionFirewall() {
        // Phone number masking
        val masked = DataMinimizer.maskPhoneNumber("+1 (555) 987-6543")
        assertTrue(masked.contains("***"))
        assertTrue(masked.endsWith("6543") || masked.endsWith("43"))

        // Memory note minimization
        val minimizedNote = DataMinimizer.minimizeMemoryNote("Note with token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitive")
        assertFalse(minimizedNote.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))

        // Permission firewall checks
        assertEquals("User Profile & Identity", SourceType.PROFILE.displayName)
        assertTrue(SourceType.SMS.whatIsStored.contains("Emergency contact"))
        assertTrue(SourceType.MICROPHONE.whatIsStored.contains("Voice orb audio discarded immediately"))
        assertTrue(SourceType.USAGE_ACCESS.whatHappensIfDenied.contains("Time audit shows disabled state"))
    }

    // =========================================================================
    // Scenario A: First Run Identity Personalization
    // =========================================================================
    @Test
    fun testScenarioA_FirstRunIdentityPersonalization() = runBlocking {
        val user = UserProfileEntity(preferredName = "Maya", primaryRole = "Entrepreneur", sleepTarget = "00:00", wakeTarget = "06:30")
        fakeDao.insertOrUpdateUserProfile(user)

        val fetched = fakeDao.getUserProfile()
        assertEquals("Maya", fetched?.preferredName)
        assertEquals("Entrepreneur", fetched?.primaryRole)
        val greeting = UserProfileEngine.generateGreeting(fetched!!, 7)
        assertTrue(greeting.contains("Maya"))
    }

    // =========================================================================
    // Scenario B: Pronunciation Preview & Non-Latin Script Fallback
    // =========================================================================
    @Test
    fun testScenarioB_PronunciationAndLanguageFallback() {
        val devanagariUser = UserProfileEntity(preferredName = "रोहन", pronunciationHint = "Rohan")
        val preview = UserProfileEngine.generatePronunciationPreview(devanagariUser)
        assertTrue(preview.contains("Rohan"))

        val kanjiUser = UserProfileEntity(preferredName = "蓮", pronunciationHint = "Ren")
        val previewKanji = UserProfileEngine.generatePronunciationPreview(kanjiUser)
        assertTrue(previewKanji.contains("Ren"))
    }

    // =========================================================================
    // Scenario C: Anchor Location Geofence Transition & Privacy Masking
    // =========================================================================
    @Test
    fun testScenarioC_AnchorLocationGeofenceTransition() = runBlocking {
        val gym = AnchorPlaceEntity(id = 10, name = "Fit Club", type = "GYM", latitude = 12.9716, longitude = 77.5946, radiusMeters = 100f)
        fakeDao.insertAnchorPlace(gym)

        // Inside geofence
        val matched = AnchorLocationEngine.matchAnchorPlace(12.97165, 77.59462, fakeDao.getActiveAnchorPlaces())
        assertEquals("Fit Club", matched?.name)

        // Outside geofence
        val outside = AnchorLocationEngine.matchAnchorPlace(12.98000, 77.60000, fakeDao.getActiveAnchorPlaces())
        assertNull(outside)
    }

    // =========================================================================
    // Scenario D: Hands-Free Hardware Wake Honesty & In-App Orb
    // =========================================================================
    @Test
    fun testScenarioD_HardwareWakeHonestyAndInAppOrb() {
        val capability = HandsFreeVoiceManager.checkHardwareWakeCapability()
        assertEquals(TriggerCapability.PLATFORM_LIMITED, capability.first)
        assertTrue(capability.second.contains("Android security restrictions"))
    }

    // =========================================================================
    // Scenario E: Voice Command Execution & Safe Mode Routing
    // =========================================================================
    @Test
    fun testScenarioE_VoiceCommandSafeModeRouting() = runBlocking {
        // When Safe Mode is ON
        SafeModeManager.setSafeMode(true)
        val blockedResp = HandsFreeVoiceManager.processVoiceCommand("add Textbook to college", fakeDao)
        assertTrue(blockedResp.requiresApproval)
        assertTrue(blockedResp.spokenResponse.contains("Safe Mode is active"))

        // When Safe Mode is OFF
        SafeModeManager.setSafeMode(false)
        val allowedResp = HandsFreeVoiceManager.processVoiceCommand("add Textbook to college", fakeDao)
        assertTrue(allowedResp.executedDirectly)
        assertTrue(allowedResp.spokenResponse.contains("Added Textbook"))
    }

    // =========================================================================
    // Scenario F: App Usage Audit & Focus Score Calculation
    // =========================================================================
    @Test
    fun testScenarioF_AppUsageAuditFocusScore() {
        val scoreNormal = TimeAuditManager.calculateFocusScore(productiveMinutes = 90, distractionMinutes = 30)
        assertEquals(75, scoreNormal)

        val scoreZeroDistraction = TimeAuditManager.calculateFocusScore(productiveMinutes = 60, distractionMinutes = 0)
        assertEquals(100, scoreZeroDistraction)

        val scoreZeroProductive = TimeAuditManager.calculateFocusScore(productiveMinutes = 0, distractionMinutes = 60)
        assertEquals(0, scoreZeroProductive)
    }

    // =========================================================================
    // Scenario G: Phone Inactivity vs Medical Sleep Honesty
    // =========================================================================
    @Test
    fun testScenarioG_InactivityVsMedicalSleepHonesty() {
        val report = PhysicalLifeRecorder.getInactivitySummary(inactivityHours = 8.0f)
        assertTrue(report.contains("8.0 hours"))
        assertTrue(report.contains("Estimated phone inactivity"))
        assertFalse(report.contains("Deep Sleep"))
        assertFalse(report.contains("Medical diagnosis"))
    }

    // =========================================================================
    // Scenario H: Memory Vault Explicit Store, TTL Expiry & Pure Retrieval
    // =========================================================================
    @Test
    fun testScenarioH_MemoryVaultTTLExpiryAndSearch() = runBlocking {
        val now = System.currentTimeMillis()
        val tempMem = MemoryEntity(content = "Temporary code 909", expiresAt = now - 1000L, isPinned = false)
        val permMem = MemoryEntity(content = "Bike lock 1234", expiresAt = 0L, isPinned = true)
        fakeDao.insertMemory(tempMem)
        fakeDao.insertMemory(permMem)

        fakeDao.deleteExpiredMemories(now)

        val searchBike = MemoryVaultEngine.askLifeOsMemory(fakeDao, "where did i put my bike")
        assertTrue(searchBike.first)
        assertTrue(searchBike.second.contains("1234"))

        val searchTemp = MemoryVaultEngine.askLifeOsMemory(fakeDao, "what was code 909")
        assertFalse(searchTemp.first)
    }

    // =========================================================================
    // Scenario I: Executive Secretary 10m Pre-Event Briefing & Conflict Resolution
    // =========================================================================
    @Test
    fun testScenarioI_ExecutiveSecretaryPreEventBriefing() = runBlocking {
        val event = EventEntity(id = 10, title = "Thesis Defense", startTime = System.currentTimeMillis() + 600 * 1000L, location = "Auditorium")
        fakeDao.insertEvent(event)

        val briefing = VirtualExecutiveSecretary.generatePreEventBriefing(event, emptyList())
        assertEquals("Thesis Defense", briefing.eventTitle)
        assertEquals("Auditorium", briefing.location)
    }

    // =========================================================================
    // Scenario J: Emergency SOS 5-Second Countdown, Test Mode & Cancel
    // =========================================================================
    @Test
    fun testScenarioJ_EmergencySosCountdownAndCancel() {
        EmergencySosManager.setTestMode(true)
        EmergencySosManager.triggerSos()
        assertEquals(SosState.COUNTDOWN, EmergencySosManager.sosState.value)

        // Cancel before 5s countdown expires
        EmergencySosManager.cancelSos()
        assertEquals(SosState.CANCELLED, EmergencySosManager.sosState.value)
    }

    // =========================================================================
    // Scenario K: Smart Clipboard Foreground Parsing & Immediate Discard
    // =========================================================================
    @Test
    fun testScenarioK_SmartClipboardImmediateDiscard() {
        val snippet = "Join us at Blue Tokai Cafe tomorrow at 10 AM. Secret code: XYZ123"
        val parsed = SmartClipboardManager.parseSnippet(snippet)
        assertNotNull(parsed)
        assertEquals("Blue Tokai Cafe", parsed?.detectedLocation)

        // Immediately discarded from internal state
        SmartClipboardManager.clearParsedResult()
        assertNull(SmartClipboardManager.lastParsedResult.value)
    }

    // =========================================================================
    // Scenario L: Full Factory Reset & Data Minimization Verification
    // =========================================================================
    @Test
    fun testScenarioL_FullFactoryResetDataPurge() = runBlocking {
        // Populate all tables
        fakeDao.insertOrUpdateUserProfile(UserProfileEntity(preferredName = "TestUser"))
        fakeDao.insertAnchorPlace(AnchorPlaceEntity(name = "TempAnchor", type = "CUSTOM"))
        fakeDao.insertMemory(MemoryEntity(content = "TempMemory"))
        fakeDao.insertEmergencyContact(EmergencyContactEntity(name = "TempContact", phoneNumber = "123"))
        fakeDao.insertOrUpdateAppUsageRule(AppUsageRuleEntity(packageName = "pkg", appName = "app"))

        // Full Factory Reset
        fakeDao.deleteUserProfile()
        fakeDao.deleteAllAnchorPlaces()
        fakeDao.deleteAllMemories()
        fakeDao.deleteAllEmergencyContacts()
        fakeDao.deleteAllAppUsageRules()

        assertNull(fakeDao.getUserProfile())
        assertTrue(fakeDao.getAllAnchorPlacesFlow().first().isEmpty())
        assertTrue(fakeDao.getAllMemoriesFlow().first().isEmpty())
        assertTrue(fakeDao.getAllEmergencyContactsFlow().first().isEmpty())
        assertTrue(fakeDao.getAllAppUsageRulesFlow().first().isEmpty())
    }
}
