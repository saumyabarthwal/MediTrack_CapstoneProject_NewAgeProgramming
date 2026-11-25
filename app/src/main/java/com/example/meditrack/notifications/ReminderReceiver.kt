package com.example.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val doseId = intent.getStringExtra("doseId").orEmpty()
                val medicineId = intent.getStringExtra("medicineId").orEmpty()
                val medicineName = intent.getStringExtra("medicineName").orEmpty()
                val dosage = intent.getStringExtra("dosage").orEmpty()
                val date = intent.getStringExtra("date").orEmpty()
                val time = intent.getStringExtra("time").orEmpty()
                val scheduledMillis = intent.getLongExtra("scheduledMillis", 0L)
                val stage = intent.getLongExtra("stage", 0L)

                when (intent.action) {
                    ReminderScheduler.ACTION_TAKEN -> {
                        val success = DoseActions.take(
                            doseId, medicineId, medicineName, dosage, date, time, scheduledMillis
                        )
                        if (success) {
                            ReminderScheduler.cancelDose(context, doseId)
                            context.getSystemService(android.app.NotificationManager::class.java)
                                ?.cancel(ReminderScheduler.alarmRequestCode(doseId, "notification"))
                        }
                    }
                    ReminderScheduler.ACTION_SNOOZE -> {
                        val remindersEnabled = context.getSharedPreferences(
                            "MediTrackSettings", Context.MODE_PRIVATE
                        ).getBoolean("remindersEnabled", true)
                        if (!remindersEnabled) return@Thread
                        val count = DoseActions.addSnooze(
                            doseId, medicineId, medicineName, dosage, date, time, scheduledMillis
                        )
                        if ((count ?: 0) > 0 && System.currentTimeMillis() + 20 * 60_000 < scheduledMillis + 90 * 60_000) {
                            ReminderScheduler.snooze(
                                context, doseId, medicineId, medicineName, dosage, date, time, scheduledMillis
                            )
                        }
                    }
                    ReminderScheduler.ACTION_ALARM -> {
                        val status = DoseActions.status(doseId)
                        if (status == "Taken" || status == "Missed") {
                            ReminderScheduler.cancelDose(context, doseId)
                        } else if (stage == 90L) {
                            DoseActions.missed(
                                doseId, medicineId, medicineName, dosage, date, time, scheduledMillis
                            )
                            ReminderScheduler.cancelDose(context, doseId)
                        } else if (System.currentTimeMillis() < scheduledMillis + 90 * 60_000 &&
                            context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                                .getBoolean("remindersEnabled", true)) {
                            val notificationId = ReminderScheduler.alarmRequestCode(doseId, "notification")
                            NotificationHelper(context).showReminder(
                                notificationId, doseId, medicineId, medicineName,
                                dosage, date, time, scheduledMillis
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}