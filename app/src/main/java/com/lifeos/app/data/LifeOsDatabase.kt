package com.lifeos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LifeModeEntity::class,
        ItemRequirementEntity::class,
        EventEntity::class,
        EvidenceEntity::class,
        NodeEntity::class,
        EdgeEntity::class,
        ItemHabitEntity::class,
        OfflineEventQueueEntity::class,
        UserProfileEntity::class,
        AnchorPlaceEntity::class,
        MemoryEntity::class,
        EmergencyContactEntity::class,
        AppUsageRuleEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class LifeOsDatabase : RoomDatabase() {

    abstract fun lifeOsDao(): LifeOsDao

    fun checkIntegrity(): String {
        return try {
            val cursor = openHelper.readableDatabase.query("PRAGMA integrity_check;")
            val result = if (cursor.moveToFirst()) cursor.getString(0) else "UNKNOWN"
            cursor.close()
            result
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    companion object {
        const val DATABASE_VERSION = 8

        @Volatile
        private var INSTANCE: LifeOsDatabase? = null

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE item_habits ADD COLUMN totalOpportunityCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item_habits ADD COLUMN lastPreparedTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item_habits ADD COLUMN lastForgottenTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item_habits ADD COLUMN consecutiveSuccessCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item_habits ADD COLUMN consecutiveForgetCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `preferredName` TEXT NOT NULL,
                        `pronunciationHint` TEXT,
                        `primaryRole` TEXT NOT NULL,
                        `customRole` TEXT,
                        `preferredGreeting` TEXT NOT NULL,
                        `preferredLanguage` TEXT NOT NULL,
                        `preferredVoiceOutput` TEXT NOT NULL,
                        `sleepTarget` TEXT NOT NULL,
                        `wakeTarget` TEXT NOT NULL,
                        `bedtimeTarget` TEXT NOT NULL,
                        `defaultCommuteMode` TEXT NOT NULL,
                        `typicalCommuteMinutes` INTEGER NOT NULL,
                        `prepBufferMinutes` INTEGER NOT NULL,
                        `trackingPaused` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `anchor_places` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `radiusMeters` REAL NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `isTemporarilyIgnored` INTEGER NOT NULL DEFAULT 0,
                        `associatedModeId` INTEGER
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memory_vault` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `content` TEXT NOT NULL,
                        `memoryType` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `emergency_contacts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `relationship` TEXT NOT NULL,
                        `isPrimary` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_usage_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `dailyLimitMinutes` INTEGER NOT NULL,
                        `modeLimitMinutes` INTEGER NOT NULL,
                        `targetModeId` INTEGER,
                        `blockAfterTime` TEXT
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): LifeOsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LifeOsDatabase::class.java,
                    "lifeos_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                populateInitialData(getDatabase(context).lifeOsDao())
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).lifeOsDao()
                if (dao.getModeById(1) == null) {
                    populateInitialData(dao)
                }
            }
        }

        suspend fun populateInitialData(dao: LifeOsDao) {
            // Seed Default Modes
            dao.insertLifeMode(LifeModeEntity(id = 1, name = "College", iconRes = "ic_college", isActive = true, priority = 1))
            dao.insertLifeMode(LifeModeEntity(id = 2, name = "Gym", iconRes = "ic_gym", isActive = false, priority = 2))
            dao.insertLifeMode(LifeModeEntity(id = 3, name = "Travel", iconRes = "ic_travel", isActive = false, priority = 3))
            dao.insertLifeMode(LifeModeEntity(id = 4, name = "Sports", iconRes = "ic_sports", isActive = false, priority = 4))
            dao.insertLifeMode(LifeModeEntity(id = 5, name = "Work", iconRes = "ic_work", isActive = false, priority = 5))
            dao.insertLifeMode(LifeModeEntity(id = 6, name = "Shopping", iconRes = "ic_shopping", isActive = false, priority = 6))

            // Seed Default Items for College
            dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "ID Card", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "DBMS Record", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "Laptop & Charger", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = "Water Bottle", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Default Items for Gym
            dao.insertItem(ItemRequirementEntity(modeId = 2, itemName = "Gym Clothes & Shoes", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 2, itemName = "Shaker Bottle", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 2, itemName = "Gym Towel", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Default Items for Travel
            dao.insertItem(ItemRequirementEntity(modeId = 3, itemName = "Passport / ID", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 3, itemName = "Travel Adapter", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 3, itemName = "Power Bank", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Default Items for Sports
            dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Football Boots", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Jersey & Shorts", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Shin Guards", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 4, itemName = "Energy Drink", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Default Items for Work
            dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Company Badge", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Laptop & Charger", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Office Notebook", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 5, itemName = "Access Keycard", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Default Items for Shopping
            dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Eco Grocery Bags", isReady = false, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Shopping List", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))
            dao.insertItem(ItemRequirementEntity(modeId = 6, itemName = "Wallet / Cards", isReady = true, confidenceScore = 1.0f, sourceEvidence = "Default Mode Checklist"))

            // Seed Personal Life Graph Nodes
            dao.insertNode(NodeEntity(id = 1, name = "College", type = "PLACE", metadataJson = "{\"location\":\"Engineering Campus\"}"))
            dao.insertNode(NodeEntity(id = 2, name = "Gym", type = "PLACE", metadataJson = "{\"location\":\"Fitness Center\"}"))
            dao.insertNode(NodeEntity(id = 3, name = "Travel", type = "PLACE", metadataJson = "{\"location\":\"Transit Terminal\"}"))
            dao.insertNode(NodeEntity(id = 4, name = "DBMS Lab", type = "ACTIVITY", metadataJson = "{\"room\":\"CS Lab 3\",\"time\":\"10:00 AM\"}"))
            dao.insertNode(NodeEntity(id = 5, name = "Workout", type = "ACTIVITY", metadataJson = "{\"routine\":\"Upper Body\",\"time\":\"06:00 PM\"}"))
            dao.insertNode(NodeEntity(id = 6, name = "DBMS Record", type = "ITEM", metadataJson = "{\"critical\":true}"))
            dao.insertNode(NodeEntity(id = 7, name = "ID Card", type = "ITEM", metadataJson = "{\"critical\":true}"))
            dao.insertNode(NodeEntity(id = 8, name = "Shaker Bottle", type = "ITEM", metadataJson = "{\"critical\":false}"))

            // Seed Personal Life Graph Edges
            dao.insertEdge(EdgeEntity(id = 1, sourceNodeId = 1, targetNodeId = 4, relationType = "CONTAINS", weight = 1.0f))
            dao.insertEdge(EdgeEntity(id = 2, sourceNodeId = 4, targetNodeId = 6, relationType = "REQUIRES", weight = 1.0f))
            dao.insertEdge(EdgeEntity(id = 3, sourceNodeId = 1, targetNodeId = 7, relationType = "REQUIRES", weight = 1.0f))
            dao.insertEdge(EdgeEntity(id = 4, sourceNodeId = 2, targetNodeId = 5, relationType = "CONTAINS", weight = 1.0f))
            dao.insertEdge(EdgeEntity(id = 5, sourceNodeId = 5, targetNodeId = 8, relationType = "REQUIRES", weight = 1.0f))

            // Seed Initial Item Habits (Stage 5 Personal Forgetting Model)
            dao.insertOrUpdateHabit(ItemHabitEntity(id = 1, itemName = "DBMS Record", modeId = 1, forgetCount = 3, confirmCount = 1, priorityBoost = 0.75f))
            dao.insertOrUpdateHabit(ItemHabitEntity(id = 2, itemName = "Laptop & Charger", modeId = 1, forgetCount = 1, confirmCount = 4, priorityBoost = 0.1f))
            dao.insertOrUpdateHabit(ItemHabitEntity(id = 3, itemName = "ID Card", modeId = 1, forgetCount = 0, confirmCount = 8, priorityBoost = 0.0f))

            // Seed Default User Profile & Baseline Anchor Places
            if (dao.getUserProfile() == null) {
                dao.insertOrUpdateUserProfile(UserProfileEntity(id = 1, preferredName = "Dinesh", primaryRole = "Student", preferredGreeting = "Good Morning"))
            }
            dao.insertAnchorPlace(AnchorPlaceEntity(id = 1, name = "Home", type = "HOME", radiusMeters = 100f, isEnabled = true))
            dao.insertAnchorPlace(AnchorPlaceEntity(id = 2, name = "Campus", type = "CAMPUS", radiusMeters = 300f, isEnabled = true, associatedModeId = 1))
            dao.insertAnchorPlace(AnchorPlaceEntity(id = 3, name = "Gym", type = "GYM", radiusMeters = 150f, isEnabled = true, associatedModeId = 2))
        }
    }
}