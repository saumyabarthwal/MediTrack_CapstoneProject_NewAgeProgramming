package com.example.meditrack.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.meditrack.models.Medicine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.CRC32

object ReminderScheduler {
    const val ACTION_ALARM = "com.example.meditrack.ACTION_ALARM"
    const val ACTION_TAKEN = "com.example.meditrack.ACTION_TAKEN"
    const val ACTION_SNOOZE = "com.example.meditrack.ACTION_SNOOZE"

    fun doseId(medicineId: String, date: String, time: String): String =
        "${medicineId}_${date.replace("-", "")}_${time.replace(":", "").replace(" ", "").uppercase(Locale.US)}"

    fun scheduleMedicine(context: Context, medicine: Medicine) {
        if (!context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                .getBoolean("remindersEnabled", true)) return
        val times = if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes else
            medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val start = runCatching { sdf.parse(medicine.startDate) }.getOrNull()
        val end = runCatching { sdf.parse(medicine.endDate) }.getOrNull() ?: return
        val startTime = maxOf(start?.time ?: System.currentTimeMillis(), System.currentTimeMillis())
        val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = end.time }

        val day = Calendar.getInstance().apply { timeInMillis = startCal.timeInMillis }
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        while (!day.after(endCal)) {
            val date = sdf.format(day.time)
            for (time in times) {
                val scheduled = scheduledMillis(day, time)
                if (scheduled >= System.currentTimeMillis()) {
                    val id = doseId(medicine.id, date, time)
                    for (stage in listOf(0L, 25L, 50L, 75L, 90L)) {
                        scheduleAlarm(context, id, medicine, date, time, scheduled + stage * 60_000, stage)
                    }
                }
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    fun scheduledMillis(day: Calendar, time: String): Long {
        val cal = day.clone() as Calendar
        val parsed = runCatching {
            SimpleDateFormat("hh:mm a", Locale.US).parse(time)
        }.getOrNull()
        if (parsed != null) {
            val clock = Calendar.getInstance().apply { timeInMillis = parsed.time }
            cal.set(Calendar.HOUR_OF_DAY, clock.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, clock.get(Calendar.MINUTE))
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun scheduleAlarm(context: Context, doseId: String, medicine: Medicine, date: String,
                       time: String, triggerMillis: Long, stage: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = alarmRequestCode(doseId, "stage_$stage")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra("doseId", doseId)
            putExtra("medicineId", medicine.id)
            putExtra("medicineName", medicine.name)
            putExtra("dosage", medicine.dosage)
            putExtra("date", date)
            putExtra("time", time)
            putExtra("scheduledMillis", triggerMillis - stage * 60_000)
            putExtra("stage", stage)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rememberAlarm(context, requestCode, pendingIntentKey(doseId, "stage_$stage"))
        if (Build.VERSION.SDK_INT >= 31) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis, 5 * 60_000, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }
    }

    fun snooze(context: Context, doseId: String, medicineId: String, medicineName: String,
               dosage: String, date: String, time: String, scheduledMillis: Long) {
        val triggerMillis = System.currentTimeMillis() + 20 * 60_000
        val deadline = scheduledMillis + 90 * 60_000
        if (triggerMillis >= deadline) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = alarmRequestCode(doseId, "snooze_${triggerMillis}")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra("doseId", doseId)
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)
            putExtra("date", date)
            putExtra("time", time)
            putExtra("scheduledMillis", scheduledMillis)
            putExtra("stage", -20L)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rememberAlarm(context, requestCode, pendingIntentKey(doseId, "snooze_$triggerMillis"))
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis, 5 * 60_000, pending)
        }
    }

    fun cancelMedicine(context: Context, medicine: Medicine) {
        val prefs = context.getSharedPreferences("meditrack_alarm_codes", Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val entries = prefs.all.toMap()
        for ((code, value) in entries) {
            val key = value as? String ?: continue
            if (key.startsWith("${medicine.id}_")) {
                val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM)
                val pending = PendingIntent.getBroadcast(
                    context, code.toIntOrNull() ?: continue, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pending)
                prefs.edit().remove(code).apply()
            }
        }
    }

    fun cancelDose(context: Context, doseId: String) {
        val prefs = context.getSharedPreferences("meditrack_alarm_codes", Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val entries = prefs.all.toMap()
        for ((code, value) in entries) {
            if ((value as? String)?.startsWith(doseId) == true) {
                val pending = PendingIntent.getBroadcast(
                    context, code.toIntOrNull() ?: continue,
                    Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pending)
                prefs.edit().remove(code).apply()
            }
        }
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences("meditrack_alarm_codes", Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        for ((code, value) in prefs.all) {
            val pending = PendingIntent.getBroadcast(
                context, code.toIntOrNull() ?: continue,
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
            val doseId = (value as? String)?.substringBefore('|') ?: continue
            notificationManager.cancel(alarmRequestCode(doseId, "notification"))
        }
        prefs.edit().clear().apply()
    }

    private fun rememberAlarm(context: Context, requestCode: Int, key: String) {
        context.getSharedPreferences("meditrack_alarm_codes", Context.MODE_PRIVATE)
            .edit().putString(requestCode.toString(), key).apply()
    }

    fun alarmRequestCode(doseId: String, suffix: String): Int {
        val crc = CRC32()
        crc.update("$doseId|$suffix".toByteArray(Charsets.UTF_8))
        return (crc.value and 0x7fffffffL).toInt()
    }

    private fun pendingIntentKey(doseId: String, suffix: String): String = "$doseId|$suffix"
}
