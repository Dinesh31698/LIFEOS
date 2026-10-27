package com.lifeos.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "life_modes")
data class LifeModeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val iconRes: String = "ic_mode",
    @ColumnInfo(name = "active")
    val isActive: Boolean = false,
    val priority: Int = 1
)

@Entity(tableName = "item_requirements")
data class ItemRequirementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val modeId: Int,
    val itemName: String,
    val isReady: Boolean = false,
    val isTemporary: Boolean = false,
    val ttlTimestamp: Long = 0L,
    val confidenceScore: Float = 1.0f,
    val sourceEvidence: String? = null,
    val isGhostMode: Boolean = false,
    val isWaived: Boolean = false
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val location: String? = null,
    val associatedModeId: Int = 1,
    val isGhostMode: Boolean = false,
    val confidence: Float = 1.0f,
    val evidenceText: String? = null
)

@Entity(tableName = "evidence_logs")
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val eventId: Int? = null,
    val sourcePackage: String = "unknown",
    val rawText: String,
    val parsedIntent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Float = 1.0f
)

// --- Stage 3: Personal Life Graph Entities ---

@Entity(tableName = "graph_nodes")
data class NodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // "PERSON", "PLACE", "ACTIVITY", "EVENT", "ITEM"
    val metadataJson: String = "{}"
)

@Entity(tableName = "graph_edges")
data class EdgeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sourceNodeId: Int,
    val targetNodeId: Int,
    val relationType: String, // "REQUIRES", "BELONGS_TO", "LOCATED_AT", "DEPENDS_ON", "CONTAINS"
    val weight: Float = 1.0f
)

// --- Stage 5: Personal Forgetting & Habit Learning Model ---

@Entity(tableName = "item_habits")
data class ItemHabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val itemName: String,
    val modeId: Int,
    val forgetCount: Int = 0,
    val waiveCount: Int = 0,
    val confirmCount: Int = 0,
    val priorityBoost: Float = 0.0f,
    val totalOpportunityCount: Int = 0,
    val lastPreparedTimestamp: Long = 0L,
    val lastForgottenTimestamp: Long = 0L,
    val consecutiveSuccessCount: Int = 0,
    val consecutiveForgetCount: Int = 0
)

// --- Stage 6 & Module 5: Offline Event Queue Entity ---

@Entity(tableName = "offline_event_queue")
data class OfflineEventQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskType: String,
    val payloadJson: String = "{}",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val retryCount: Int = 0
)

// --- Final Phase: User Identity Profile Entity ---
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,
    val preferredName: String = "User",
    val pronunciationHint: String? = null,
    val primaryRole: String = "Student", // Student, Freelancer, Employee, Executive, Entrepreneur, Researcher, Other, Custom
    val customRole: String? = null,
    val preferredGreeting: String = "Good Morning",
    val preferredLanguage: String = "English",
    val preferredVoiceOutput: String = "System Default",
    val sleepTarget: String = "23:00",
    val wakeTarget: String = "07:00",
    val bedtimeTarget: String = "22:30",
    val defaultCommuteMode: String = "Transit", // Walking, Cycling, Transit, Driving
    val typicalCommuteMinutes: Int = 30,
    val prepBufferMinutes: Int = 15,
    val trackingPaused: Boolean = false
)

// --- Final Phase: Anchor Location Baseline Entity ---
@Entity(tableName = "anchor_places")
data class AnchorPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // "HOME", "CAMPUS", "WORK", "GYM", "LIBRARY", "CUSTOM"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 100.0f,
    val isEnabled: Boolean = true,
    val isTemporarilyIgnored: Boolean = false,
    val associatedModeId: Int? = null
)

// --- Final Phase: Semantic Micro-Memory Vault Entity ---
@Entity(tableName = "memory_vault")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val memoryType: String = "SHORT_TERM", // "EPHEMERAL", "SHORT_TERM", "LONG_TERM", "PINNED"
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 86400000L, // default 24h
    val isPinned: Boolean = false
)

// --- Final Phase: Emergency SOS Contact Entity ---
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Emergency Contact",
    val isPrimary: Boolean = false
)

// --- Final Phase: App Usage Rule Entity ---
@Entity(tableName = "app_usage_rules")
data class AppUsageRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val appName: String,
    val category: String = "DISTRACTION", // "PRODUCTIVE", "NEUTRAL", "DISTRACTION", "CUSTOM"
    val dailyLimitMinutes: Int = 60,
    val modeLimitMinutes: Int = 0,
    val targetModeId: Int? = null,
    val blockAfterTime: String? = null
)


