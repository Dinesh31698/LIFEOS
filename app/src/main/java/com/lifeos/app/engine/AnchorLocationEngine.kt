package com.lifeos.app.engine

import com.lifeos.app.data.AnchorPlaceEntity
import com.lifeos.app.data.LifeOsDao
import kotlinx.coroutines.flow.Flow
import kotlin.math.*

data class FrequentPlaceCandidate(
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
    val suggestedLabels: List<String> = listOf("Campus", "Gym", "Study Spot", "Custom"),
    val proposalText: String = "Frequent place detected."
)

object AnchorLocationEngine {

    val SUPPORTED_TYPES = listOf(
        "HOME",
        "CAMPUS",
        "WORK",
        "GYM",
        "LIBRARY",
        "CUSTOM"
    )

    fun getAllAnchorPlacesFlow(dao: LifeOsDao): Flow<List<AnchorPlaceEntity>> {
        return dao.getAllAnchorPlacesFlow()
    }

    suspend fun getActiveAnchors(dao: LifeOsDao): List<AnchorPlaceEntity> {
        return dao.getActiveAnchorPlaces()
    }

    suspend fun addAnchor(dao: LifeOsDao, anchor: AnchorPlaceEntity): Long {
        val existing = dao.getActiveAnchorPlaces().firstOrNull { it.name.equals(anchor.name.trim(), ignoreCase = true) }
        return if (existing != null) {
            dao.updateAnchorPlace(anchor.copy(id = existing.id))
            existing.id.toLong()
        } else {
            dao.insertAnchorPlace(anchor)
        }
    }

    suspend fun updateAnchor(dao: LifeOsDao, anchor: AnchorPlaceEntity) {
        dao.updateAnchorPlace(anchor)
    }

    suspend fun deleteAnchor(dao: LifeOsDao, anchorId: Int) {
        dao.deleteAnchorPlaceById(anchorId)
    }

    suspend fun toggleIgnoreAnchor(dao: LifeOsDao, anchorId: Int, ignored: Boolean) {
        dao.setAnchorPlaceIgnored(anchorId, ignored)
    }

    /**
     * Identifies location clusters from repeated user visits.
     * Proposes a frequent place without automatically assigning sensitive labels without confirmation.
     */
    fun detectFrequentPlace(
        visits: List<Pair<Double, Double>>,
        existingAnchors: List<AnchorPlaceEntity>,
        clusterThresholdMeters: Double = 150.0,
        minVisits: Int = 3
    ): FrequentPlaceCandidate? {
        if (visits.size < minVisits) return null

        for (i in visits.indices) {
            val center = visits[i]
            val cluster = visits.filter { calculateDistanceMeters(center.first, center.second, it.first, it.second) <= clusterThresholdMeters }
            if (cluster.size >= minVisits) {
                val alreadyCovered = existingAnchors.any { anchor ->
                    calculateDistanceMeters(center.first, center.second, anchor.latitude, anchor.longitude) <= anchor.radiusMeters
                }
                if (!alreadyCovered) {
                    return FrequentPlaceCandidate(
                        latitude = center.first,
                        longitude = center.second,
                        visitCount = cluster.size
                    )
                }
            }
        }
        return null
    }

    /**
     * Calculates distance between coordinates in meters using the Haversine formula.
     * Evaluated strictly on-demand without background location daemon loops.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Checks if given coordinates match an active anchor location.
     */
    fun matchAnchorPlace(lat: Double, lon: Double, anchors: List<AnchorPlaceEntity>): AnchorPlaceEntity? {
        return anchors.filter { it.isEnabled && !it.isTemporarilyIgnored }.firstOrNull { anchor ->
            if (anchor.latitude == 0.0 && anchor.longitude == 0.0) false
            else calculateDistanceMeters(lat, lon, anchor.latitude, anchor.longitude) <= anchor.radiusMeters
        }
    }

    /**
     * Privacy Masking: If within a privacy zone (e.g. Home), coordinates are rounded/masked
     * to protect user domicile privacy while maintaining coarse vicinity.
     */
    fun applyPrivacyMasking(lat: Double, lon: Double, isMasked: Boolean = true): Pair<Double, Double> {
        if (!isMasked) return lat to lon
        // Round to 2 decimal places (~1.1 km coarse granularity)
        val maskedLat = (lat * 100.0).roundToInt() / 100.0
        val maskedLon = (lon * 100.0).roundToInt() / 100.0
        return maskedLat to maskedLon
    }
}
