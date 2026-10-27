package com.lifeos.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracyMeters: Float = 5.0f,
    val speedMps: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class TravelHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val startTime: Long,
    val endTime: Long,
    val originName: String,
    val destinationName: String,
    val durationMinutes: Int,
    val transitMode: String = "Transit", // "Walking", "Cycling", "Transit", "Driving"
    val distanceMeters: Double = 0.0,
    val points: List<Pair<Double, Double>> = emptyList()
)

data class PlaceVisitRecord(
    val id: String = UUID.randomUUID().toString(),
    val anchorPlaceId: Int? = null,
    val placeName: String,
    val placeType: String = "CUSTOM",
    val arrivalTime: Long,
    val departureTime: Long,
    val durationMinutes: Int,
    val modeName: String = "General",
    val readinessPercentage: Int = 100,
    val visitCountThisMonth: Int = 1,
    val averageStayMinutes: Int = 60
)

data class PrivateZoneRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200.0f,
    val policy: String = "BLUR" // "BLUR", "GENERALIZE", "EXCLUDE", "DELETE"
)

enum class TimeScrubberWindow {
    ALL,
    MORNING,    // 06:00 - 12:00
    AFTERNOON,  // 12:00 - 17:00
    EVENING,    // 17:00 - 21:00
    NIGHT       // 21:00 - 06:00
}

/**
 * Single source of truth repository for local-only, offline Life Map data.
 * Manages atomic local JSON file persistence, retention policies, and atomic deletions.
 */
object LifeMapRepository {

    private val _samples = MutableStateFlow<List<LocationSample>>(emptyList())
    val samples: StateFlow<List<LocationSample>> = _samples.asStateFlow()

    private val _travelHistory = MutableStateFlow<List<TravelHistoryRecord>>(emptyList())
    val travelHistory: StateFlow<List<TravelHistoryRecord>> = _travelHistory.asStateFlow()

    private val _placeVisits = MutableStateFlow<List<PlaceVisitRecord>>(emptyList())
    val placeVisits: StateFlow<List<PlaceVisitRecord>> = _placeVisits.asStateFlow()

    private val _privateZones = MutableStateFlow<List<PrivateZoneRecord>>(emptyList())
    val privateZones: StateFlow<List<PrivateZoneRecord>> = _privateZones.asStateFlow()

    private var storageFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun initialize(context: Context) {
        val dir = File(context.filesDir, "lifemap")
        if (!dir.exists()) dir.mkdirs()
        storageFile = File(dir, "life_map_store.json")
        loadFromDisk()

        if (_travelHistory.value.isEmpty() && _placeVisits.value.isEmpty()) {
            seedDefaultJourneyData()
        }
    }

    private fun seedDefaultJourneyData() {
        val todayStr = dateFormat.format(Date())
        val now = System.currentTimeMillis()

        val homeVisit = PlaceVisitRecord(
            placeName = "Home",
            placeType = "HOME",
            arrivalTime = now - 14 * 3600000L,
            departureTime = now - 12 * 3600000L,
            durationMinutes = 120,
            modeName = "Recharge",
            readinessPercentage = 95,
            visitCountThisMonth = 28,
            averageStayMinutes = 480
        )
        val campusVisit = PlaceVisitRecord(
            placeName = "Campus",
            placeType = "CAMPUS",
            arrivalTime = now - 11 * 3600000L,
            departureTime = now - 5 * 3600000L,
            durationMinutes = 360,
            modeName = "College",
            readinessPercentage = 88,
            visitCountThisMonth = 22,
            averageStayMinutes = 340
        )
        val gymVisit = PlaceVisitRecord(
            placeName = "Gym",
            placeType = "GYM",
            arrivalTime = now - 4 * 3600000L,
            departureTime = now - 2 * 3600000L,
            durationMinutes = 90,
            modeName = "Gym",
            readinessPercentage = 94,
            visitCountThisMonth = 16,
            averageStayMinutes = 85
        )

        val routeHomeToCampus = TravelHistoryRecord(
            date = todayStr,
            startTime = now - 12 * 3600000L,
            endTime = now - 11 * 3600000L,
            originName = "Home",
            destinationName = "Campus",
            durationMinutes = 25,
            transitMode = "Transit",
            distanceMeters = 2400.0,
            points = listOf(
                17.3850 to 78.4867,
                17.3882 to 78.4901,
                17.3920 to 78.4935,
                17.3955 to 78.4960,
                17.3985 to 78.4985
            )
        )

        val routeCampusToGym = TravelHistoryRecord(
            date = todayStr,
            startTime = now - 5 * 3600000L,
            endTime = now - 4 * 3600000L,
            originName = "Campus",
            destinationName = "Gym",
            durationMinutes = 28,
            transitMode = "Cycling",
            distanceMeters = 3800.0,
            points = listOf(
                17.3985 to 78.4985,
                17.3920 to 78.4910,
                17.3860 to 78.4840,
                17.3800 to 78.4780,
                17.3745 to 78.4725
            )
        )

        val routeGymToHome = TravelHistoryRecord(
            date = todayStr,
            startTime = now - 2 * 3600000L,
            endTime = now - 1 * 3600000L,
            originName = "Gym",
            destinationName = "Home",
            durationMinutes = 20,
            transitMode = "Walking",
            distanceMeters = 2100.0,
            points = listOf(
                17.3745 to 78.4725,
                17.3780 to 78.4770,
                17.3815 to 78.4820,
                17.3850 to 78.4867
            )
        )

        val homeZone = PrivateZoneRecord(
            name = "Home Privacy Bubble",
            latitude = 17.3850,
            longitude = 78.4867,
            radiusMeters = 200.0f,
            policy = "BLUR"
        )

        _placeVisits.value = listOf(homeVisit, campusVisit, gymVisit)
        _travelHistory.value = listOf(routeHomeToCampus, routeCampusToGym, routeGymToHome)
        _privateZones.value = listOf(homeZone)
        saveToDisk()
    }

    @Synchronized
    fun setCustomStorageFileForTest(file: File) {
        storageFile = file
        loadFromDisk()
    }

    @Synchronized
    fun addLocationSample(sample: LocationSample) {
        val current = _samples.value.toMutableList()
        current.add(sample)
        _samples.value = current
        saveToDisk()
    }

    @Synchronized
    fun addTravelHistory(record: TravelHistoryRecord) {
        val current = _travelHistory.value.toMutableList()
        current.add(record)
        _travelHistory.value = current
        saveToDisk()
    }

    @Synchronized
    fun addPlaceVisit(visit: PlaceVisitRecord) {
        val current = _placeVisits.value.toMutableList()
        current.add(visit)
        _placeVisits.value = current
        saveToDisk()
    }

    @Synchronized
    fun addPrivateZone(zone: PrivateZoneRecord) {
        val current = _privateZones.value.toMutableList()
        current.add(zone)
        _privateZones.value = current
        saveToDisk()
    }

    @Synchronized
    fun removePrivateZone(id: String) {
        _privateZones.value = _privateZones.value.filter { it.id != id }
        saveToDisk()
    }

    @Synchronized
    fun deleteToday() {
        val todayStr = dateFormat.format(Date())
        val startOfDay = getStartOfDayMillis(System.currentTimeMillis())

        _samples.value = _samples.value.filter { it.timestamp < startOfDay }
        _travelHistory.value = _travelHistory.value.filter { it.date != todayStr }
        _placeVisits.value = _placeVisits.value.filter { it.arrivalTime < startOfDay }
        saveToDisk()
    }

    @Synchronized
    fun deleteThisWeek() {
        val oneWeekAgo = System.currentTimeMillis() - (7L * 86400000L)

        _samples.value = _samples.value.filter { it.timestamp < oneWeekAgo }
        _travelHistory.value = _travelHistory.value.filter { it.startTime < oneWeekAgo }
        _placeVisits.value = _placeVisits.value.filter { it.arrivalTime < oneWeekAgo }
        saveToDisk()
    }

    @Synchronized
    fun deleteAll() {
        _samples.value = emptyList()
        _travelHistory.value = emptyList()
        _placeVisits.value = emptyList()
        _privateZones.value = emptyList()
        saveToDisk()
    }

    @Synchronized
    fun enforceRetentionPolicy(retentionDays: Int) {
        if (retentionDays <= 0) return // 0 or negative = never delete
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 86400000L)

        _samples.value = _samples.value.filter { it.timestamp >= cutoff }
        _travelHistory.value = _travelHistory.value.filter { it.startTime >= cutoff }
        _placeVisits.value = _placeVisits.value.filter { it.arrivalTime >= cutoff }
        saveToDisk()
    }

    fun getVisitsFiltered(
        period: String, // "TODAY", "WEEK", "MONTH", "ALL"
        scrubber: TimeScrubberWindow = TimeScrubberWindow.ALL
    ): List<PlaceVisitRecord> {
        val now = System.currentTimeMillis()
        val periodFiltered = when (period.uppercase()) {
            "TODAY" -> {
                val startOfDay = getStartOfDayMillis(now)
                _placeVisits.value.filter { it.arrivalTime >= startOfDay }
            }
            "WEEK" -> {
                val startOfWeek = now - (7L * 86400000L)
                _placeVisits.value.filter { it.arrivalTime >= startOfWeek }
            }
            "MONTH" -> {
                val startOfMonth = now - (30L * 86400000L)
                _placeVisits.value.filter { it.arrivalTime >= startOfMonth }
            }
            else -> _placeVisits.value
        }

        if (scrubber == TimeScrubberWindow.ALL) return periodFiltered

        return periodFiltered.filter { visit ->
            val cal = Calendar.getInstance().apply { timeInMillis = visit.arrivalTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (scrubber) {
                TimeScrubberWindow.MORNING -> hour in 6..11
                TimeScrubberWindow.AFTERNOON -> hour in 12..16
                TimeScrubberWindow.EVENING -> hour in 17..20
                TimeScrubberWindow.NIGHT -> hour >= 21 || hour < 6
                TimeScrubberWindow.ALL -> true
            }
        }
    }

    fun getTravelHistoryFiltered(
        period: String,
        scrubber: TimeScrubberWindow = TimeScrubberWindow.ALL
    ): List<TravelHistoryRecord> {
        val now = System.currentTimeMillis()
        val periodFiltered = when (period.uppercase()) {
            "TODAY" -> {
                val todayStr = dateFormat.format(Date(now))
                _travelHistory.value.filter { it.date == todayStr }
            }
            "WEEK" -> {
                val startOfWeek = now - (7L * 86400000L)
                _travelHistory.value.filter { it.startTime >= startOfWeek }
            }
            "MONTH" -> {
                val startOfMonth = now - (30L * 86400000L)
                _travelHistory.value.filter { it.startTime >= startOfMonth }
            }
            else -> _travelHistory.value
        }

        if (scrubber == TimeScrubberWindow.ALL) return periodFiltered

        return periodFiltered.filter { record ->
            val cal = Calendar.getInstance().apply { timeInMillis = record.startTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (scrubber) {
                TimeScrubberWindow.MORNING -> hour in 6..11
                TimeScrubberWindow.AFTERNOON -> hour in 12..16
                TimeScrubberWindow.EVENING -> hour in 17..20
                TimeScrubberWindow.NIGHT -> hour >= 21 || hour < 6
                TimeScrubberWindow.ALL -> true
            }
        }
    }

    private fun getStartOfDayMillis(time: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun buildJson(): String = buildString {
        append("{\n")

        // Samples
        append("  \"samples\": [\n")
        _samples.value.forEachIndexed { i, s ->
            append("    {\"lat\":${s.latitude},\"lon\":${s.longitude},\"alt\":${s.altitude},\"acc\":${s.accuracyMeters},\"spd\":${s.speedMps},\"ts\":${s.timestamp}}")
            if (i < _samples.value.size - 1) append(",")
            append("\n")
        }
        append("  ],\n")

        // Travel History
        append("  \"travel\": [\n")
        _travelHistory.value.forEachIndexed { i, t ->
            append("    {\"id\":\"${escape(t.id)}\",\"date\":\"${escape(t.date)}\",\"start\":${t.startTime},\"end\":${t.endTime},\"origin\":\"${escape(t.originName)}\",\"dest\":\"${escape(t.destinationName)}\",\"dur\":${t.durationMinutes},\"mode\":\"${escape(t.transitMode)}\",\"dist\":${t.distanceMeters},\"pts\":[")
            t.points.forEachIndexed { pi, p ->
                append("{\"lat\":${p.first},\"lon\":${p.second}}")
                if (pi < t.points.size - 1) append(",")
            }
            append("]}")
            if (i < _travelHistory.value.size - 1) append(",")
            append("\n")
        }
        append("  ],\n")

        // Place Visits
        append("  \"visits\": [\n")
        _placeVisits.value.forEachIndexed { i, v ->
            append("    {\"id\":\"${escape(v.id)}\"")
            if (v.anchorPlaceId != null) append(",\"anchorId\":${v.anchorPlaceId}")
            append(",\"name\":\"${escape(v.placeName)}\",\"type\":\"${escape(v.placeType)}\",\"arr\":${v.arrivalTime},\"dep\":${v.departureTime},\"dur\":${v.durationMinutes},\"mode\":\"${escape(v.modeName)}\",\"ready\":${v.readinessPercentage},\"visitsMonth\":${v.visitCountThisMonth},\"avgStay\":${v.averageStayMinutes}}")
            if (i < _placeVisits.value.size - 1) append(",")
            append("\n")
        }
        append("  ],\n")

        // Private Zones
        append("  \"zones\": [\n")
        _privateZones.value.forEachIndexed { i, z ->
            append("    {\"id\":\"${escape(z.id)}\",\"name\":\"${escape(z.name)}\",\"lat\":${z.latitude},\"lon\":${z.longitude},\"rad\":${z.radiusMeters},\"policy\":\"${escape(z.policy)}\"}")
            if (i < _privateZones.value.size - 1) append(",")
            append("\n")
        }
        append("  ]\n")

        append("}")
    }

    @Synchronized
    private fun saveToDisk() {
        val file = storageFile ?: return
        try {
            val json = buildJson()
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) {
            _samples.value = emptyList()
            _travelHistory.value = emptyList()
            _placeVisits.value = emptyList()
            _privateZones.value = emptyList()
            return
        }

        try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isBlank()) return
            val root = SimpleJson.parse(content)

            // Samples
            val samplesList = mutableListOf<LocationSample>()
            val samplesRaw = root["samples"] as? List<*> ?: emptyList<Any>()
            for (item in samplesRaw) {
                val obj = item as? Map<*, *> ?: continue
                samplesList.add(
                    LocationSample(
                        latitude = (obj["lat"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (obj["lon"] as? Number)?.toDouble() ?: 0.0,
                        altitude = (obj["alt"] as? Number)?.toDouble() ?: 0.0,
                        accuracyMeters = (obj["acc"] as? Number)?.toFloat() ?: 5.0f,
                        speedMps = (obj["spd"] as? Number)?.toFloat() ?: 0.0f,
                        timestamp = (obj["ts"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                )
            }
            _samples.value = samplesList

            // Travel History
            val travelList = mutableListOf<TravelHistoryRecord>()
            val travelRaw = root["travel"] as? List<*> ?: emptyList<Any>()
            for (item in travelRaw) {
                val obj = item as? Map<*, *> ?: continue
                val ptsList = mutableListOf<Pair<Double, Double>>()
                val ptsRaw = obj["pts"] as? List<*> ?: emptyList<Any>()
                for (ptItem in ptsRaw) {
                    val ptObj = ptItem as? Map<*, *> ?: continue
                    val lat = (ptObj["lat"] as? Number)?.toDouble() ?: 0.0
                    val lon = (ptObj["lon"] as? Number)?.toDouble() ?: 0.0
                    ptsList.add(lat to lon)
                }

                travelList.add(
                    TravelHistoryRecord(
                        id = (obj["id"] as? String) ?: UUID.randomUUID().toString(),
                        date = (obj["date"] as? String) ?: "",
                        startTime = (obj["start"] as? Number)?.toLong() ?: 0L,
                        endTime = (obj["end"] as? Number)?.toLong() ?: 0L,
                        originName = (obj["origin"] as? String) ?: "",
                        destinationName = (obj["dest"] as? String) ?: "",
                        durationMinutes = (obj["dur"] as? Number)?.toInt() ?: 0,
                        transitMode = (obj["mode"] as? String) ?: "Transit",
                        distanceMeters = (obj["dist"] as? Number)?.toDouble() ?: 0.0,
                        points = ptsList
                    )
                )
            }
            _travelHistory.value = travelList

            // Place Visits
            val visitsList = mutableListOf<PlaceVisitRecord>()
            val visitsRaw = root["visits"] as? List<*> ?: emptyList<Any>()
            for (item in visitsRaw) {
                val obj = item as? Map<*, *> ?: continue
                visitsList.add(
                    PlaceVisitRecord(
                        id = (obj["id"] as? String) ?: UUID.randomUUID().toString(),
                        anchorPlaceId = (obj["anchorId"] as? Number)?.toInt(),
                        placeName = (obj["name"] as? String) ?: "",
                        placeType = (obj["type"] as? String) ?: "CUSTOM",
                        arrivalTime = (obj["arr"] as? Number)?.toLong() ?: 0L,
                        departureTime = (obj["dep"] as? Number)?.toLong() ?: 0L,
                        durationMinutes = (obj["dur"] as? Number)?.toInt() ?: 0,
                        modeName = (obj["mode"] as? String) ?: "General",
                        readinessPercentage = (obj["ready"] as? Number)?.toInt() ?: 100,
                        visitCountThisMonth = (obj["visitsMonth"] as? Number)?.toInt() ?: 1,
                        averageStayMinutes = (obj["avgStay"] as? Number)?.toInt() ?: 60
                    )
                )
            }
            _placeVisits.value = visitsList

            // Private Zones
            val zonesList = mutableListOf<PrivateZoneRecord>()
            val zonesRaw = root["zones"] as? List<*> ?: emptyList<Any>()
            for (item in zonesRaw) {
                val obj = item as? Map<*, *> ?: continue
                zonesList.add(
                    PrivateZoneRecord(
                        id = (obj["id"] as? String) ?: UUID.randomUUID().toString(),
                        name = (obj["name"] as? String) ?: "",
                        latitude = (obj["lat"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (obj["lon"] as? Number)?.toDouble() ?: 0.0,
                        radiusMeters = (obj["rad"] as? Number)?.toFloat() ?: 200.0f,
                        policy = (obj["policy"] as? String) ?: "BLUR"
                    )
                )
            }
            _privateZones.value = zonesList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

internal object SimpleJson {
    fun parse(input: String): Map<String, Any?> {
        var idx = 0
        fun skipWhitespace() {
            while (idx < input.length && input[idx].isWhitespace()) idx++
        }
        fun parseString(): String {
            idx++ // skip opening quote
            val sb = StringBuilder()
            while (idx < input.length) {
                val c = input[idx++]
                if (c == '"') break
                if (c == '\\' && idx < input.length) {
                    val esc = input[idx++]
                    when (esc) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }
        fun parseNumber(): Any {
            val start = idx
            while (idx < input.length && (input[idx].isDigit() || input[idx] == '-' || input[idx] == '.' || input[idx] == '+' || input[idx] == 'e' || input[idx] == 'E')) {
                idx++
            }
            val numStr = input.substring(start, idx)
            return if (numStr.contains('.') || numStr.contains('e') || numStr.contains('E')) {
                numStr.toDoubleOrNull() ?: 0.0
            } else {
                numStr.toLongOrNull() ?: 0L
            }
        }

        lateinit var parseObject: () -> Map<String, Any?>
        lateinit var parseArray: () -> List<Any?>

        fun parseValue(): Any? {
            skipWhitespace()
            if (idx >= input.length) return null
            return when (input[idx]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> {
                    if (input.startsWith("true", idx)) { idx += 4; true }
                    else { idx += 5; false }
                }
                'n' -> { idx += 4; null }
                else -> parseNumber()
            }
        }

        parseArray = {
            idx++ // skip '['
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (idx < input.length && input[idx] == ']') {
                idx++
                list
            } else {
                while (idx < input.length) {
                    list.add(parseValue())
                    skipWhitespace()
                    if (idx < input.length && input[idx] == ',') { idx++; continue }
                    if (idx < input.length && input[idx] == ']') { idx++; break }
                }
                list
            }
        }

        parseObject = {
            idx++ // skip '{'
            val map = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (idx < input.length && input[idx] == '}') {
                idx++
                map
            } else {
                while (idx < input.length) {
                    skipWhitespace()
                    if (idx < input.length && input[idx] == '"') {
                        val key = parseString()
                        skipWhitespace()
                        if (idx < input.length && input[idx] == ':') idx++
                        val value = parseValue()
                        map[key] = value
                    }
                    skipWhitespace()
                    if (idx < input.length && input[idx] == ',') { idx++; continue }
                    if (idx < input.length && input[idx] == '}') { idx++; break }
                }
                map
            }
        }

        skipWhitespace()
        if (idx < input.length && input[idx] == '{') {
            return parseObject()
        }
        return emptyMap()
    }
}
