package com.example.meditrack.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.meditrack.R
import com.example.meditrack.activities.DashboardActivity
import com.example.meditrack.activities.MedicineDetailsActivity

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "meditrack_reminders"
        const val LOW_STOCK_CHANNEL_ID = "meditrack_low_stock"
        const val ENGAGEMENT_CHANNEL_ID = "meditrack_engagement"

        private val reminderMessages = listOf(
            "Hey, where did you disappear? 👀\nYour medicine is waiting for you!",
            "Knock knock! 🚪\nYour scheduled dose is here.",
            "Your health called! 📞\nIt wants you to check your medicine reminder.",
            "Hello, responsible human! 😎\nIt’s medicine time!",
            "A tiny reminder from MediTrack 💜\nYour scheduled dose is ready.",
            "Don’t ghost your schedule! 👻\nYour medicine reminder is waiting.",
            "Mission: Stay on Track 🎯\nIt’s time for your scheduled medicine.",
            "Psst... one little thing! 🤫\nDon’t forget to check your medicine schedule."
        )
        private val engagementMessages = listOf(
            "Oye! Where are you? 👀\nMediTrack misses your daily check-in!",
            "Long time no see! 💜\nYour health dashboard is waiting for you.",
            "Hello, stranger! 👋\nCome check what's happening in your MediTrack today.",
            "Your dashboard is getting lonely! 🥹\nCome take a quick look at your progress.",
            "A little health check? 🌸\nOpen MediTrack and see how your day is going.",
            "Psst... it's check-in time! 🤭\nYour daily health routine deserves a quick look.",
            "We've got updates! ✨\nCheck your medicine schedule and stock.",
            "Your future self says thanks! 💜\nTake a moment to review your medicine routine."
        )
        private val lowStockMessages = listOf(
            "Uh-oh! Running low! 🚨\n%1\$s stock is getting low.",
            "Refill o'clock! 📦\n%1\$s needs a refill soon.",
            "Last few standing! 💊\nOnly %2\$d %3\$s left.",
            "Empty shelf alert! 🚨\n%1\$s is out of stock."
        )
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    LOW_STOCK_CHANNEL_ID,
                    "Low-stock alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts when a medicine needs a refill." },
                NotificationChannel(
                    ENGAGEMENT_CHANNEL_ID,
                    "MediTrack check-ins",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Optional reminders to check your health routine." }
            )
        )
    }

    fun createChannel() = createChannels()

    fun showReminder(
        notificationId: Int,
        doseId: String,
        medicineId: String,
        medicineName: String,
        dosage: String,
        date: String,
        time: String,
        scheduledMillis: Long
    ) {
        createChannels()
        val extras = intentExtras(doseId, medicineId, medicineName, dosage, date, time, scheduledMillis)
        val takenIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_TAKEN
            putExtras(extras)
        }
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_SNOOZE
            putExtras(extras)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = nextMessage("reminderMessageIndex", reminderMessages)
        val parts = message.split("\n", limit = 2)
        val title = parts.first()
        val body = "${parts.getOrElse(1) { "" }}\n$medicineName • $dosage • $time"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_meditrack)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(medicineIntent(medicineId, notificationId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.status_taken), takenPending)
            .addAction(0, context.getString(R.string.snooze_20_minutes), snoozePending)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    fun showLowStock(
        medicineName: String,
        remaining: Int,
        unit: String,
        medicineId: String = ""
    ) {
        if (!context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                .getBoolean("lowStockEnabled", true)) return
        createChannels()
        val id = ("low_${medicineId.ifBlank { medicineName }}").hashCode()
        val message = nextMessage("lowStockMessageIndex", lowStockMessages)
        val parts = message.split("\n", limit = 2)
        val title = parts.first()
        val body = parts.getOrElse(1) { "" }.format(medicineName, remaining, unit)
        val notification = NotificationCompat.Builder(context, LOW_STOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_meditrack)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(medicineIntent(medicineId, id))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSharedPreferences("MediTrackLowStock", Context.MODE_PRIVATE)
            .edit().putInt("notification_$id", id).apply()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancelLowStockNotifications() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("MediTrackLowStock", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.filterKeys { it.startsWith("notification_") }.forEach { (key, value) ->
            (value as? Int)?.takeIf { it != 0 }?.let(manager::cancel)
            editor.remove(key)
        }
        editor.apply()
    }

    fun showEngagement() {
        if (!context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                .getBoolean("engagementEnabled", false) ||
            !context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                .getBoolean("generalNotificationsEnabled", true)) return
        createChannels()
        val id = 7401
        val message = nextMessage("engagementMessageIndex", engagementMessages)
        val parts = message.split("\n", limit = 2)
        val title = parts.first()
        val body = parts.getOrElse(1) { "" }
        val notification = NotificationCompat.Builder(context, ENGAGEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_meditrack)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(dashboardIntent(id))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancelEngagement() {
        context.getSystemService(NotificationManager::class.java).cancel(7401)
    }

    private fun nextMessage(key: String, messages: List<String>): String {
        val prefs = context.getSharedPreferences("MediTrackNotificationRotation", Context.MODE_PRIVATE)
        val index = prefs.getInt(key, 0).mod(messages.size)
        prefs.edit().putInt(key, (index + 1) % messages.size).apply()
        return messages[index]
    }

    private fun medicineIntent(medicineId: String, requestCode: Int): PendingIntent {
        val intent = if (medicineId.isBlank()) {
            Intent(context, DashboardActivity::class.java)
        } else {
            Intent(context, MedicineDetailsActivity::class.java).putExtra("medicineId", medicineId)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dashboardIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intentExtras(
        doseId: String, medicineId: String, medicineName: String, dosage: String,
        date: String, time: String, scheduledMillis: Long
    ): android.os.Bundle = android.os.Bundle().apply {
        putString("doseId", doseId)
        putString("medicineId", medicineId)
        putString("medicineName", medicineName)
        putString("dosage", dosage)
        putString("date", date)
        putString("time", time)
        putLong("scheduledMillis", scheduledMillis)
    }
}
