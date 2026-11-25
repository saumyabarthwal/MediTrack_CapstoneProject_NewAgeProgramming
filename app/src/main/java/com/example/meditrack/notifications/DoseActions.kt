package com.example.meditrack.notifications

import android.content.Context
import com.example.meditrack.MediTrackApplication
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object DoseActions {

    fun take(
        doseId: String,
        medicineId: String,
        medicineName: String,
        dosage: String,
        date: String,
        time: String,
        scheduledMillis: Long
    ): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val db = FirebaseFirestore.getInstance()
        val historyRef = db.collection("users").document(uid).collection("history").document(doseId)
        val medicineRef = db.collection("users").document(uid).collection("medicines").document(medicineId)

        return try {
            val taken = Tasks.await(db.runTransaction { transaction ->
                val history = transaction.get(historyRef)
                val existingStatus = history.getString("status")
                if (existingStatus == "Taken" || existingStatus == "Missed") {
                    return@runTransaction false
                }

                val medicine = if (medicineId.isNotEmpty()) transaction.get(medicineRef) else null
                val amount = (medicine?.getLong("amountPerDose") ?: 1L).toInt().coerceAtLeast(1)
                val remaining = (medicine?.getLong("quantity") ?: 0L).toInt().coerceAtLeast(0)
                if (medicine != null) {
                    transaction.update(medicineRef, "quantity", (remaining - amount).coerceAtLeast(0))
                }

                val values = hashMapOf(
                    "doseId" to doseId,
                    "userId" to uid,
                    "medicineId" to medicineId,
                    "medicineName" to medicineName,
                    "dosage" to dosage,
                    "date" to date,
                    "time" to time,
                    "status" to "Taken",
                    "scheduledMillis" to scheduledMillis,
                    "completedMillis" to System.currentTimeMillis()
                )
                transaction.set(historyRef, values, SetOptions.merge())
                true
            })
            if (taken) {
                val context = MediTrackApplication.instance
                val medicine = Tasks.await(medicineRef.get())
                val remaining = medicine.getLong("quantity")?.toInt()?.coerceAtLeast(0) ?: 0
                val threshold = medicine.getLong("lowStockThreshold")?.toInt() ?: 5
                val unit = medicine.getString("quantityUnit").orEmpty().ifBlank { "tablets" }
                val name = medicine.getString("name").orEmpty().ifBlank { medicineName }
                val prefs = context.getSharedPreferences("MediTrackLowStock", Context.MODE_PRIVATE)
                val notifiedKey = "notified_$medicineId"
                if (remaining <= threshold) {
                    val shouldNotify = !prefs.getBoolean(notifiedKey, false)
                    if (shouldNotify && context.getSharedPreferences("MediTrackSettings", Context.MODE_PRIVATE)
                            .getBoolean("lowStockEnabled", true)) {
                        NotificationHelper(context).showLowStock(name, remaining, unit, medicineId)
                        prefs.edit().putBoolean(notifiedKey, true).apply()
                    }
                } else {
                    prefs.edit().remove(notifiedKey).apply()
                }
            }
            taken
        } catch (_: Exception) {
            false
        }
    }

    fun missed(
        doseId: String,
        medicineId: String,
        medicineName: String,
        dosage: String,
        date: String,
        time: String,
        scheduledMillis: Long
    ): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val db = FirebaseFirestore.getInstance()
        val historyRef = db.collection("users").document(uid).collection("history").document(doseId)
        return try {
            Tasks.await(db.runTransaction { transaction ->
                val history = transaction.get(historyRef)
                val existingStatus = history.getString("status")
                if (existingStatus == "Taken" || existingStatus == "Missed") {
                    return@runTransaction false
                }
                val values = hashMapOf(
                    "doseId" to doseId,
                    "userId" to uid,
                    "medicineId" to medicineId,
                    "medicineName" to medicineName,
                    "dosage" to dosage,
                    "date" to date,
                    "time" to time,
                    "status" to "Missed",
                    "scheduledMillis" to scheduledMillis,
                    "completedMillis" to System.currentTimeMillis()
                )
                transaction.set(historyRef, values, SetOptions.merge())
                true
            })
        } catch (_: Exception) {
            false
        }
    }

    fun status(doseId: String): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            val snapshot = Tasks.await(
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("history").document(doseId).get()
            )
            snapshot.getString("status")
        } catch (_: Exception) {
            null
        }
    }

    fun addSnooze(
        doseId: String,
        medicineId: String = "",
        medicineName: String = "",
        dosage: String = "",
        date: String = "",
        time: String = "",
        scheduledMillis: Long = 0L
    ): Int? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val ref = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("history").document(doseId)
        return try {
            Tasks.await(FirebaseFirestore.getInstance().runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val status = snapshot.getString("status")
                if (status == "Taken" || status == "Missed") return@runTransaction -1
                val count = (snapshot.getLong("snoozeCount") ?: 0L).toInt()
                if (count >= 4) return@runTransaction -1
                val values = hashMapOf(
                    "doseId" to doseId,
                    "userId" to uid,
                    "medicineId" to medicineId,
                    "medicineName" to medicineName,
                    "dosage" to dosage,
                    "date" to date,
                    "time" to time,
                    "status" to "Pending",
                    "scheduledMillis" to scheduledMillis,
                    "snoozeCount" to count + 1
                )
                transaction.set(ref, values, SetOptions.merge())
                count + 1
            })
        } catch (_: Exception) {
            null
        }
    }
}
