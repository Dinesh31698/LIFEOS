package com.lifeos.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifeos.app.data.LifeOsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TtlCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val db = LifeOsDatabase.getDatabase(applicationContext)
            val deletedCount = db.lifeOsDao().deleteExpiredItems(now)
            Log.d(TAG, "TtlCleanupWorker executed: purged $deletedCount expired temporary items at $now")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TtlCleanupWorker execution failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TtlCleanupWorker"
        const val WORK_NAME = "lifeos_ttl_cleanup_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TtlCleanupWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "TtlCleanupWorker scheduled for execution every 1 hour")
        }
    }
}
