package com.example.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.meditrack.models.Medicine

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Thread
                val snapshot = Tasks.await(
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid).collection("medicines").get()
                )
                val medicines = snapshot.documents.mapNotNull {
                    it.toObject(Medicine::class.java)?.apply { id = it.id }
                }
                val remindersEnabled = context.getSharedPreferences(
                    "MediTrackSettings", Context.MODE_PRIVATE
                ).getBoolean("remindersEnabled", true)
                if (remindersEnabled) {
                    for (medicine in medicines) {
                        ReminderScheduler.scheduleMedicine(context, medicine)
                    }
                }
            } catch (_: Exception) {
            } finally {
                result.finish()
            }
        }.start()
    }
}
