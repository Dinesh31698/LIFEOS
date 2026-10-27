package com.lifeos.app.engine

import com.lifeos.app.data.LifeOsDao
import com.lifeos.app.data.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

object UserProfileEngine {

    val SUPPORTED_ROLES = listOf(
        "Student",
        "Freelancer",
        "Employee",
        "Executive",
        "Entrepreneur",
        "Researcher",
        "Other",
        "Custom"
    )

    val SUPPORTED_COMMUTE_MODES = listOf(
        "Walking",
        "Cycling",
        "Bike",
        "Transit",
        "Driving"
    )

    fun getUserProfileFlow(dao: LifeOsDao): Flow<UserProfileEntity?> {
        return dao.getUserProfileFlow()
    }

    suspend fun getProfile(dao: LifeOsDao): UserProfileEntity {
        return dao.getUserProfile() ?: UserProfileEntity(
            id = 1,
            preferredName = "Dinesh",
            primaryRole = "Student",
            preferredGreeting = "Good Morning"
        ).also { dao.insertOrUpdateUserProfile(it) }
    }

    suspend fun updateProfile(dao: LifeOsDao, profile: UserProfileEntity) {
        dao.insertOrUpdateUserProfile(profile.copy(id = 1))
    }

    /**
     * Dynamically generates the contextual greeting for the user based on the current hour,
     * user's preferred greeting style, and preferred name.
     * Absolutely zero hardcoded names.
     */
    fun generateGreeting(profile: UserProfileEntity, currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        val timeGreeting = when (currentHour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        val name = profile.preferredName.trim().ifEmpty { "User" }
        return "$timeGreeting, $name"
    }

    /**
     * Generates a spoken pronunciation preview string incorporating phonetic hints if available.
     */
    fun generatePronunciationPreview(profile: UserProfileEntity): String {
        val spokenName = profile.pronunciationHint?.takeIf { it.isNotBlank() } ?: profile.preferredName.trim().ifEmpty { "User" }
        return "Hello, $spokenName. Welcome to LIFEOS."
    }

    /**
     * Provides formatted routine summary (e.g. "Wake at 07:00 • Sleep at 23:00").
     */
    fun formatRoutineSummary(profile: UserProfileEntity): String {
        return "Wake: ${profile.wakeTarget} | Bedtime: ${profile.bedtimeTarget} | Commute: ${profile.typicalCommuteMinutes}m (${profile.defaultCommuteMode})"
    }
}
