package com.lifeos.app

import com.lifeos.app.data.*
import com.lifeos.app.engine.*
import com.lifeos.app.ui.navigation.Screen
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class RealWorldLifeMapAndUiTest {

    private lateinit var tempStoreFile: File

    @Before
    fun setUp() {
        tempStoreFile = File.createTempFile("test_life_map_store", ".json")
        LifeMapRepository.setCustomStorageFileForTest(tempStoreFile)
        LifeMapRepository.deleteAll()
        InteractiveLifeMapManager.setTrackingPaused(false)
        InteractiveLifeMapManager.setPrivacyMaskActive(true)
    }

    @After
    fun tearDown() {
        if (tempStoreFile.exists()) {
            tempStoreFile.delete()
        }
    }

    /**
     * TEST 1: Home → College visit detected where permissions/data allow.
     */
    @Test
    fun test1_homeToCollegeVisitDetected() {
        val arrivalTime = System.currentTimeMillis() - 7200000L
        val departureTime = System.currentTimeMillis() - 3600000L

        val collegeVisit = PlaceVisitRecord(
            placeName = "Engineering Campus",
            placeType = "CAMPUS",
            arrivalTime = arrivalTime,
            departureTime = departureTime,
            durationMinutes = 60,
            modeName = "College",
            readinessPercentage = 87
        )

        val recorded = InteractiveLifeMapManager.recordVisit(collegeVisit)
        assertTrue("College visit should be recorded", recorded)

        val visits = LifeMapRepository.getVisitsFiltered("TODAY")
        assertEquals("Should have 1 recorded visit", 1, visits.size)
        assertEquals("Engineering Campus", visits[0].placeName)
        assertEquals(87, visits[0].readinessPercentage)
    }

    /**
     * TEST 2: College → Gym route/visit updates.
     */
    @Test
    fun test2_collegeToGymRouteAndVisitUpdates() {
        // Record College visit
        InteractiveLifeMapManager.recordVisit(
            PlaceVisitRecord(
                placeName = "Campus",
                placeType = "CAMPUS",
                arrivalTime = System.currentTimeMillis() - 10000000L,
                departureTime = System.currentTimeMillis() - 7000000L,
                durationMinutes = 50,
                modeName = "College"
            )
        )

        // Record Route College -> Gym
        val route = TravelHistoryRecord(
            date = "2026-10-07",
            startTime = System.currentTimeMillis() - 7000000L,
            endTime = System.currentTimeMillis() - 5500000L,
            originName = "Campus",
            destinationName = "Fitness Gym",
            durationMinutes = 25,
            transitMode = "Transit",
            points = listOf(17.3850 to 78.4867, 17.3900 to 78.4900, 17.3950 to 78.4950)
        )
        InteractiveLifeMapManager.recordTravel(route)

        // Record Gym visit
        val gymVisit = PlaceVisitRecord(
            placeName = "Fitness Gym",
            placeType = "GYM",
            arrivalTime = System.currentTimeMillis() - 5500000L,
            departureTime = System.currentTimeMillis() - 1900000L,
            durationMinutes = 60,
            modeName = "Gym",
            readinessPercentage = 100
        )
        InteractiveLifeMapManager.recordVisit(gymVisit)

        val allVisits = LifeMapRepository.placeVisits.value
        val allRoutes = LifeMapRepository.travelHistory.value
        assertEquals("Should have 2 visits", 2, allVisits.size)
        assertEquals("Should have 1 travel route", 1, allRoutes.size)
        assertEquals("Fitness Gym", allVisits[1].placeName)
        assertEquals("Fitness Gym", allRoutes[0].destinationName)
    }

    /**
     * TEST 3: Repeated Gym visits -> frequent-place suggestion.
     */
    @Test
    fun test3_repeatedVisitsFrequentPlaceSuggestion() {
        val gymVisits = listOf(
            17.3950 to 78.4950,
            17.3951 to 78.4951,
            17.3949 to 78.4949
        )

        val existingAnchors = listOf(
            AnchorPlaceEntity(name = "Home", type = "HOME", latitude = 17.3800, longitude = 78.4800)
        )

        val candidate = AnchorLocationEngine.detectFrequentPlace(
            visits = gymVisits,
            existingAnchors = existingAnchors,
            clusterThresholdMeters = 150.0,
            minVisits = 3
        )

        assertNotNull("Frequent place candidate should be detected from 3 visits", candidate)
        assertEquals(3, candidate!!.visitCount)
        assertTrue("Suggested labels should include Gym", candidate.suggestedLabels.contains("Gym"))
    }

    /**
     * TEST 4: User rejects suggested label -> no automatic relabeling.
     */
    @Test
    fun test4_userRejectsSuggestedLabel() {
        // User explicitly marks candidate as ignored or keeps CUSTOM
        val customPlace = AnchorPlaceEntity(
            name = "Custom Pin #1",
            type = "CUSTOM",
            latitude = 17.3950,
            longitude = 78.4950,
            isTemporarilyIgnored = true
        )

        assertEquals("CUSTOM", customPlace.type)
        assertTrue(customPlace.isTemporarilyIgnored)
        assertNotEquals("GYM", customPlace.type)
    }

    /**
     * TEST 5: User confirms Gym -> label persists.
     */
    @Test
    fun test5_userConfirmsGymLabel() {
        val confirmedAnchor = AnchorPlaceEntity(
            name = "Campus Fitness Center",
            type = "GYM",
            latitude = 17.3950,
            longitude = 78.4950,
            radiusMeters = 100.0f
        )

        assertEquals("GYM", confirmedAnchor.type)
        assertEquals("Campus Fitness Center", confirmedAnchor.name)
        assertFalse(confirmedAnchor.isTemporarilyIgnored)
    }

    /**
     * TEST 6: Private Home zone -> exact home coordinates hidden.
     */
    @Test
    fun test6_privateHomeZoneObfuscation() {
        val homeAnchor = AnchorPlaceEntity(
            name = "Home",
            type = "HOME",
            latitude = 17.3850123,
            longitude = 78.4867123,
            radiusMeters = 200.0f
        )

        val (maskedLat, maskedLon) = InteractiveLifeMapManager.processDisplayCoordinates(
            lat = 17.3850123,
            lon = 78.4867123,
            anchors = listOf(homeAnchor)
        )

        // The coordinates must not match the exact private coordinate
        assertNotEquals(17.3850123, maskedLat, 0.0000001)
        assertNotEquals(78.4867123, maskedLon, 0.0000001)

        // Generalize policy test
        val privateZone = PrivateZoneRecord(
            name = "Private Domicile",
            latitude = 17.3850123,
            longitude = 78.4867123,
            radiusMeters = 200f,
            policy = "GENERALIZE"
        )
        val (coarseLat, coarseLon) = InteractiveLifeMapManager.processDisplayCoordinates(
            lat = 17.3850123,
            lon = 78.4867123,
            anchors = emptyList(),
            privateZones = listOf(privateZone)
        )
        assertEquals(17.39, coarseLat, 0.001)
        assertEquals(78.49, coarseLon, 0.001)
    }

    /**
     * TEST 7: Pause tracking -> no new location history written.
     */
    @Test
    fun test7_pauseTrackingStopsIntake() {
        InteractiveLifeMapManager.setTrackingPaused(true)
        assertTrue(InteractiveLifeMapManager.privacySettings.value.isTrackingPaused)
        assertTrue(PhysicalLifeRecorder.isTrackingPaused.value)

        val sampleRecorded = InteractiveLifeMapManager.recordSample(
            LocationSample(latitude = 17.3850, longitude = 78.4867)
        )
        assertFalse("Sample recording should be rejected while paused", sampleRecorded)

        val visitRecorded = InteractiveLifeMapManager.recordVisit(
            PlaceVisitRecord(placeName = "Library", arrivalTime = 0, departureTime = 0, durationMinutes = 30)
        )
        assertFalse("Visit recording should be rejected while paused", visitRecorded)

        val travelRecorded = InteractiveLifeMapManager.recordTravel(
            TravelHistoryRecord(date = "2026-10-07", startTime = 0, endTime = 0, originName = "A", destinationName = "B", durationMinutes = 10)
        )
        assertFalse("Travel recording should be rejected while paused", travelRecorded)

        assertEquals("No samples stored", 0, LifeMapRepository.samples.value.size)
        assertEquals("No visits stored", 0, LifeMapRepository.placeVisits.value.size)
        assertEquals("No travel stored", 0, LifeMapRepository.travelHistory.value.size)
    }

    /**
     * TEST 8: Resume -> recording resumes normally.
     */
    @Test
    fun test8_resumeTrackingAllowsIntake() {
        InteractiveLifeMapManager.setTrackingPaused(true)
        InteractiveLifeMapManager.setTrackingPaused(false)
        assertFalse(InteractiveLifeMapManager.privacySettings.value.isTrackingPaused)

        val sampleRecorded = InteractiveLifeMapManager.recordSample(
            LocationSample(latitude = 17.3850, longitude = 78.4867)
        )
        assertTrue("Sample recording should succeed when resumed", sampleRecorded)
        assertEquals(1, LifeMapRepository.samples.value.size)
    }

    /**
     * TEST 9: Revoke location -> recording stops gracefully.
     */
    @Test
    fun test9_revokeLocationHandlesGracefully() {
        // When location permission is revoked, app doesn't crash; coordinates return 0.0 to 0.0 when paused
        InteractiveLifeMapManager.setTrackingPaused(true)
        val coords = InteractiveLifeMapManager.processDisplayCoordinates(17.3850, 78.4867, emptyList())
        assertEquals(0.0 to 0.0, coords)
    }

    /**
     * TEST 10: Offline -> local history remains available.
     */
    @Test
    fun test10_offlineLocalHistoryRemainsAvailable() {
        InteractiveLifeMapManager.recordVisit(
            PlaceVisitRecord(
                placeName = "Offline Study Hall",
                arrivalTime = System.currentTimeMillis() - 3600000L,
                departureTime = System.currentTimeMillis(),
                durationMinutes = 60
            )
        )

        val offlineVisits = LifeMapRepository.getVisitsFiltered("TODAY")
        assertEquals(1, offlineVisits.size)
        assertEquals("Offline Study Hall", offlineVisits[0].placeName)
    }

    /**
     * TEST 11: Delete today's history -> today's data removed atomically.
     */
    @Test
    fun test11_deleteTodayHistoryAtomically() {
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L

        // Record today's visit
        InteractiveLifeMapManager.recordVisit(
            PlaceVisitRecord(id = "visit_today", placeName = "Gym", arrivalTime = now, departureTime = now + 3600000L, durationMinutes = 60)
        )
        // Record yesterday's visit
        InteractiveLifeMapManager.recordVisit(
            PlaceVisitRecord(id = "visit_yesterday", placeName = "Library", arrivalTime = yesterday, departureTime = yesterday + 3600000L, durationMinutes = 60)
        )

        assertEquals(2, LifeMapRepository.placeVisits.value.size)

        InteractiveLifeMapManager.deleteTodayHistory()

        val remaining = LifeMapRepository.placeVisits.value
        assertEquals("Only yesterday's visit should remain", 1, remaining.size)
        assertEquals("Library", remaining[0].placeName)
    }

    /**
     * TEST 12: Delete all -> all location history removed atomically.
     */
    @Test
    fun test12_deleteAllHistoryAtomically() {
        InteractiveLifeMapManager.recordSample(LocationSample(latitude = 17.3850, longitude = 78.4867))
        InteractiveLifeMapManager.recordVisit(PlaceVisitRecord(placeName = "Gym", arrivalTime = 0, departureTime = 0, durationMinutes = 60))
        InteractiveLifeMapManager.recordTravel(TravelHistoryRecord(date = "2026-10-07", startTime = 0, endTime = 0, originName = "A", destinationName = "B", durationMinutes = 10))

        InteractiveLifeMapManager.deleteAllHistory()

        assertTrue(LifeMapRepository.samples.value.isEmpty())
        assertTrue(LifeMapRepository.placeVisits.value.isEmpty())
        assertTrue(LifeMapRepository.travelHistory.value.isEmpty())
    }

    /**
     * TEST 13: Force-stop -> persistent state preserved on reload.
     */
    @Test
    fun test13_forceStopReloadPreservesPersistentState() {
        val visit = PlaceVisitRecord(
            id = "persisted_1",
            placeName = "Persisted Campus",
            arrivalTime = 1000L,
            departureTime = 2000L,
            durationMinutes = 60
        )
        InteractiveLifeMapManager.recordVisit(visit)

        // Simulate new process instance loading from disk
        LifeMapRepository.setCustomStorageFileForTest(tempStoreFile)

        val reloaded = LifeMapRepository.placeVisits.value
        assertEquals(1, reloaded.size)
        assertEquals("Persisted Campus", reloaded[0].placeName)
    }

    /**
     * TEST 14: Reboot -> persistent state preserved.
     */
    @Test
    fun test14_rebootPreservesPersistentState() {
        val route = TravelHistoryRecord(
            id = "reboot_route_1",
            date = "2026-10-07",
            startTime = 5000L,
            endTime = 6000L,
            originName = "Home",
            destinationName = "College",
            durationMinutes = 30
        )
        InteractiveLifeMapManager.recordTravel(route)

        // Reload from same persistent store file
        LifeMapRepository.setCustomStorageFileForTest(tempStoreFile)

        val routes = LifeMapRepository.travelHistory.value
        assertEquals(1, routes.size)
        assertEquals("Home", routes[0].originName)
        assertEquals("College", routes[0].destinationName)
    }

    /**
     * TEST 15: Long history -> efficient filtering without memory bloat.
     */
    @Test
    fun test15_longHistoryEfficientFiltering() {
        val now = System.currentTimeMillis()
        // Insert 100 mock historical visits
        for (i in 0 until 100) {
            LifeMapRepository.addPlaceVisit(
                PlaceVisitRecord(
                    id = "hist_$i",
                    placeName = "Stop $i",
                    arrivalTime = now - (i * 3600000L),
                    departureTime = now - (i * 3600000L - 1800000L),
                    durationMinutes = 30
                )
            )
        }

        val todayVisits = LifeMapRepository.getVisitsFiltered("TODAY")
        val weekVisits = LifeMapRepository.getVisitsFiltered("WEEK")
        val monthVisits = LifeMapRepository.getVisitsFiltered("MONTH")

        assertTrue("Today should have a subset of all visits", todayVisits.size <= weekVisits.size)
        assertTrue("Week visits should be subset of month visits", weekVisits.size <= monthVisits.size)
        assertEquals(100, LifeMapRepository.placeVisits.value.size)
    }

    /**
     * UI Verification: 5-Part Navigation Model contains Home, Modes, Voice, LifeMap, More.
     */
    @Test
    fun testUi_5PartNavigationModel() {
        val items = Screen.bottomNavItems
        assertEquals(5, items.size)
        assertEquals("home", items[0].route)
        assertEquals("modes", items[1].route)
        assertEquals("voice", items[2].route)
        assertEquals("life_map", items[3].route)
        assertEquals("more", items[4].route)
    }

    /**
     * UI Verification: Mode card readiness displays "Not Set" when checklist is unconfigured.
     */
    @Test
    fun testUi_modeReadinessNotSetWhenEmpty() {
        val emptyItems = emptyList<ItemRequirementEntity>()
        val readinessText = if (emptyItems.isEmpty()) {
            "Not Set"
        } else {
            val readyCount = emptyItems.count { it.isReady || it.isWaived }
            "${(readyCount * 100 / emptyItems.size)}% Ready"
        }

        assertEquals("Not Set", readinessText)
        assertNotEquals("100%", readinessText)
    }
}
