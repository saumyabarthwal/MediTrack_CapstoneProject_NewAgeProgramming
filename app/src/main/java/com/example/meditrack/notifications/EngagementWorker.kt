package com.example.meditrack.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class EngagementWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            "MediTrackSettings", Context.MODE_PRIVATE
        )
        if (!preferences.getBoolean("engagementEnabled", false) ||
            !preferences.getBoolean("generalNotificationsEnabled", true)) return Result.success()

        val now = System.currentTimeMillis()
        val plannedAt = inputData.getLong("plannedAt", now)
        if (now < plannedAt) {
            EngagementScheduler.scheduleAt(applicationContext, plannedAt)
            return Result.success()
        }

        val quietEnd = EngagementScheduler.quietHoursEnd(applicationContext, now)
        if (quietEnd != null) {
            EngagementScheduler.scheduleAt(applicationContext, quietEnd)
            return Result.success()
        }

        val today = EngagementScheduler.dateKey(now)
        if (preferences.getString("engagementLastSentDate", null) != today) {
            NotificationHelper(applicationContext).showEngagement()
            preferences.edit().putString("engagementLastSentDate", today).apply()
        }
        EngagementScheduler.scheduleNext(applicationContext, now)
        return Result.success()
    }
}
