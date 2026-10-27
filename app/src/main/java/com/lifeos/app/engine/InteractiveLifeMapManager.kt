package com.lifeos.app.engine

import com.lifeos.app.data.AnchorPlaceEntity
import com.lifeos.app.data.LifeMapRepository
import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.LocationSample
import com.lifeos.app.data.PlaceVisitRecord
import com.lifeos.app.data.PrivateZoneRecord
import com.lifeos.app.data.TimeScrubberWindow
import com.lifeos.app.data.TravelHistoryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapPrivacySettings(
    val isTrackingPaused: Boolean = false,
    val isPrivacyMaskActive: Boolean = true,
    val maskRadiusMeters: Float = 200f,
    val retentionDays: Int = 30
)

data class MapLayerToggles(
    val showRoute: Boolean = true,
    val showPlaces: Boolean = true,
    val showHeatmap: Boolean = false,
    val showContext: Boolean = true,
    val showTime: Boolean = true
)

object InteractiveLifeMapManager {

    private val _privacySettings = MutableStateFlow(MapPrivacySettings())
    val privacySettings = _privacySettings.asStateFlow()

    private val _layerToggles = MutableStateFlow(MapLayerToggles())
    val layerToggles = _layerToggles.asStateFlow()

    private val _timeScrubberWindow = MutableStateFlow(TimeScrubberWindow.ALL)
    val timeScrubberWindow = _timeScrubberWindow.asStateFlow()

    private val _selectedPlaceForDetails = MutableStateFlow<PlaceVisitRecord?>(null)
    val selectedPlaceForDetails = _selectedPlaceForDetails.asStateFlow()

    fun setTrackingPaused(paused: Boolean) {
        _privacySettings.value = _privacySettings.value.copy(isTrackingPaused = paused)
        // Synchronously pause physical movement recording
        PhysicalLifeRecorder.setTrackingPaused(paused)
    }

    fun setPrivacyMaskActive(active: Boolean) {
        _privacySettings.value = _privacySettings.value.copy(isPrivacyMaskActive = active)
    }

    fun setRetentionDays(days: Int) {
        _privacySettings.value = _privacySettings.value.copy(retentionDays = days)
        LifeMapRepository.enforceRetentionPolicy(days)
    }

    fun toggleLayer(layerName: String) {
        val current = _layerToggles.value
        _layerToggles.value = when (layerName.uppercase()) {
            "ROUTE" -> current.copy(showRoute = !current.showRoute)
            "PLACES" -> current.copy(showPlaces = !current.showPlaces)
            "HEATMAP" -> current.copy(showHeatmap = !current.showHeatmap)
            "CONTEXT" -> current.copy(showContext = !current.showContext)
            "TIME" -> current.copy(showTime = !current.showTime)
            else -> current
        }
    }

    fun setTimeScrubber(window: TimeScrubberWindow) {
        _timeScrubberWindow.value = window
    }

    fun selectPlaceForDetails(visit: PlaceVisitRecord?) {
        _selectedPlaceForDetails.value = visit
    }

    /**
     * Records a new location sample if tracking is not paused.
     */
    fun recordSample(sample: LocationSample): Boolean {
        if (_privacySettings.value.isTrackingPaused) return false
        LifeMapRepository.addLocationSample(sample)
        return true
    }

    /**
     * Records a new visited place if tracking is not paused.
     */
    fun recordVisit(visit: PlaceVisitRecord): Boolean {
        if (_privacySettings.value.isTrackingPaused) return false
        LifeMapRepository.addPlaceVisit(visit)
        return true
    }

    /**
     * Records a travel route if tracking is not paused.
     */
    fun recordTravel(record: TravelHistoryRecord): Boolean {
        if (_privacySettings.value.isTrackingPaused) return false
        LifeMapRepository.addTravelHistory(record)
        return true
    }

    suspend fun clearLocationHistory(dao: LifeOsDao) {
        LifeMapRepository.deleteAll()
        val anchors = dao.getActiveAnchorPlaces()
        anchors.forEach { anchor ->
            if (anchor.type == "CUSTOM") {
                dao.updateAnchorPlace(anchor.copy(latitude = 0.0, longitude = 0.0))
            }
        }
    }

    fun deleteTodayHistory() {
        LifeMapRepository.deleteToday()
    }

    fun deleteWeekHistory() {
        LifeMapRepository.deleteThisWeek()
    }

    fun deleteAllHistory() {
        LifeMapRepository.deleteAll()
    }

    /**
     * Obfuscates coordinates if user is within an active privacy mask zone or near private anchors.
     * Respects BLUR, GENERALIZE, and EXCLUDE policies without leaking raw coordinates.
     */
    fun processDisplayCoordinates(
        lat: Double,
        lon: Double,
        anchors: List<AnchorPlaceEntity>,
        privateZones: List<PrivateZoneRecord> = LifeMapRepository.privateZones.value
    ): Pair<Double, Double> {
        val settings = _privacySettings.value
        if (settings.isTrackingPaused) return 0.0 to 0.0
        if (!settings.isPrivacyMaskActive) return lat to lon

        // Check custom private zones
        for (zone in privateZones) {
            val dist = AnchorLocationEngine.calculateDistanceMeters(lat, lon, zone.latitude, zone.longitude)
            if (dist <= zone.radiusMeters) {
                return when (zone.policy.uppercase()) {
                    "EXCLUDE" -> 0.0 to 0.0
                    "GENERALIZE" -> {
                        // Coarsen to ~1km grid
                        val coarseLat = Math.round(lat * 100.0) / 100.0
                        val coarseLon = Math.round(lon * 100.0) / 100.0
                        coarseLat to coarseLon
                    }
                    else -> AnchorLocationEngine.applyPrivacyMasking(lat, lon, true)
                }
            }
        }

        // Check HOME anchor
        val homeAnchor = anchors.firstOrNull { it.type == "HOME" }
        return if (homeAnchor != null && homeAnchor.latitude != 0.0) {
            val dist = AnchorLocationEngine.calculateDistanceMeters(lat, lon, homeAnchor.latitude, homeAnchor.longitude)
            if (dist <= settings.maskRadiusMeters) {
                AnchorLocationEngine.applyPrivacyMasking(lat, lon, true)
            } else {
                lat to lon
            }
        } else {
            AnchorLocationEngine.applyPrivacyMasking(lat, lon, true)
        }
    }
}

