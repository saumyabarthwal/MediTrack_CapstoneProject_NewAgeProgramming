package com.example.meditrack.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object EngagementScheduler {
    private const val UNIQUE_WORK = "meditrack_engagement_notification"
    private const val SETTINGS = "MediTrackSettings"
    private const val PLANNED_AT = "plannedAt"

    fun update(context: Context, enabled: Boolean, newlyEnabled: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            NotificationHelper(context).cancelEngagement()
            return
        }
        val preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        if (newlyEnabled || !preferences.contains("engagementAnchorDay")) {
            preferences.edit().putInt(
                "engagementAnchorDay", Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            ).apply()
        }
        scheduleNext(context)
    }

    fun scheduleNext(context: Context, now: Long = System.currentTimeMillis()) {
        val preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean("engagementEnabled", false)) return
        val planned = nextOccurrence(context, now)
        scheduleAt(context, planned)
    }

    fun scheduleAt(context: Context, plannedAt: Long) {
        val delay = (plannedAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<EngagementWorker>()
            .setInputData(Data.Builder().putLong(PLANNED_AT, plannedAt).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request
        )
    }

    fun quietHoursEnd(context: Context, now: Long): Long? {
        val preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        if (!preferences.getBoolean("quietHoursEnabled", true)) return null
        val start = parseTime(preferences.getString("quietHoursStart", "22:00") ?: "22:00")
        val end = parseTime(preferences.getString("quietHoursEnd", "08:00") ?: "08:00")
        if (start == end) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val quiet = if (start < end) minute in start until end else minute >= start || minute < end
        if (!quiet) return null
        calendar.set(Calendar.HOUR_OF_DAY, end / 60)
        calendar.set(Calendar.MINUTE, end % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (start > end && minute >= start) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun nextOccurrence(context: Context, now: Long): Long {
        val preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val time = parseTime(preferences.getString("engagementTime", "09:00") ?: "09:00")
        val frequency = preferences.getString("engagementFrequency", "daily") ?: "daily"
        val anchorDay = preferences.getInt("engagementAnchorDay", Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        val lastSent = preferences.getString("engagementLastSentDate", null)
        val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }
        for (offset in 0..14) {
            val candidate = (nowCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, time / 60)
                set(Calendar.MINUTE, time % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (frequency.equals("weekly", ignoreCase = true) &&
                candidate.get(Calendar.DAY_OF_WEEK) != anchorDay) continue
            val adjusted = quietHoursEnd(context, candidate.timeInMillis) ?: candidate.timeInMillis
            if (adjusted <= now || dateKey(candidate.timeInMillis) == lastSent ||
                dateKey(adjusted) == lastSent) continue
            return adjusted
        }
        return now + TimeUnit.DAYS.toMillis(if (frequency.equals("weekly", true)) 7 else 1)
    }

    fun dateKey(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMillis))

    private fun parseTime(value: String): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour * 60 + minute
    }
}
