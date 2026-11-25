package com.example.meditrack.firebase

import com.example.meditrack.models.DoseHistory
import com.example.meditrack.models.Medicine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FirebaseHelper {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun registerUser(name: String, email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    callback(false, "Unable to get user information")
                    return@addOnSuccessListener
                }
                val profile = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).set(profile)
                    .addOnSuccessListener { callback(true, "Registration successful") }
                    .addOnFailureListener { callback(false, "Account created, but the profile could not be saved") }
            }
            .addOnFailureListener { error ->
                val message = when (error) {
                    is FirebaseAuthWeakPasswordException -> "Password must contain at least 6 characters"
                    is FirebaseAuthInvalidCredentialsException -> "Enter a valid email address"
                    is FirebaseAuthUserCollisionException -> "An account already exists with this email"
                    else -> "Registration failed. Check your connection and try again"
                }
                callback(false, message)
            }
    }

    fun loginUser(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { callback(true, "Login successful") }
            .addOnFailureListener { error ->
                val message = when (error) {
                    is FirebaseAuthInvalidUserException -> "No account found with this email"
                    is FirebaseAuthInvalidCredentialsException -> "Email or password is incorrect"
                    else -> "Unable to sign in. Check your connection and try again"
                }
                callback(false, message)
            }
    }

    fun sendPasswordReset(email: String, callback: (Boolean, String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(true, "Password reset email sent") }
            .addOnFailureListener {
                callback(false, "Unable to send reset email. Check the email address")
            }
    }

    fun updateMedicine(medicine: Medicine, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null || medicine.id.isEmpty()) {
            callback(false)
            return
        }
        medicine.userId = uid
        db.collection("users").document(uid).collection("medicines")
            .document(medicine.id).set(medicine, SetOptions.merge())
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun addMedicine(medicine: Medicine, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(false, "Please login first")
            return
        }
        val ref = db.collection("users").document(uid).collection("medicines").document()
        medicine.id = ref.id
        medicine.userId = uid
        ref.set(medicine)
            .addOnSuccessListener { callback(true, "Medicine added successfully") }
            .addOnFailureListener { callback(false, "Unable to add medicine") }
    }

    fun getMedicines(callback: (List<Medicine>, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(emptyList(), "Please login first")
            return
        }
        db.collection("users").document(uid).collection("medicines").get()
            .addOnSuccessListener { result ->
                val medicines = result.documents.mapNotNull { document ->
                    document.toObject(Medicine::class.java)?.apply {
                        id = document.id
                        if (reminderTimes.isEmpty() && time.isNotBlank()) {
                            reminderTimes = time.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                        }
                        if (quantityUnit.isBlank()) quantityUnit = "tablets"
                    }
                }
                callback(medicines, null)
            }
            .addOnFailureListener { callback(emptyList(), "Unable to load medicines") }
    }

    fun updateMedicineStatus(medicineId: String, status: String, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false)
        db.collection("users").document(uid).collection("medicines").document(medicineId)
            .update("status", status)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun deleteMedicine(medicineId: String, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false)
        db.collection("users").document(uid).collection("medicines").document(medicineId).delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun addDoseHistory(history: DoseHistory, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false)
        val documentId = history.doseId.ifBlank { history.id.ifBlank { db.collection("users").document().id } }
        val ref = db.collection("users").document(uid).collection("history").document(documentId)
        history.doseId = documentId
        history.userId = uid
        ref.set(history)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getDoseHistory(callback: (List<DoseHistory>, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(emptyList(), "Please login first")
            return
        }
        db.collection("users").document(uid).collection("history").get()
            .addOnSuccessListener { result ->
                val history = result.documents.mapNotNull { document ->
                    document.toObject(DoseHistory::class.java)?.apply {
                        id = document.id
                        if (doseId.isBlank()) doseId = document.id
                    }
                }.sortedByDescending { it.scheduledMillis }
                callback(history, null)
            }
            .addOnFailureListener { callback(emptyList(), "Unable to load dose history") }
    }

    fun synchronizeDoseRecords(medicines: List<Medicine>, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(false)
            return
        }
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val historyCollection = db.collection("users").document(uid).collection("history")
        var pendingWrites = 0
        var failures = 0
        var scheduledCount = 0

        fun finishOne() {
            pendingWrites--
            if (pendingWrites == 0) callback(failures == 0)
        }

        medicines.filter { it.id.isNotBlank() }.forEach { medicine ->
            val times = if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes else
                medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val start = runCatching { dateFormat.parse(medicine.startDate) }.getOrNull()
            val end = runCatching { dateFormat.parse(medicine.endDate) }.getOrNull() ?: return@forEach
            val day = Calendar.getInstance().apply {
                timeInMillis = start?.time ?: today.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endMillis = Calendar.getInstance().apply {
                timeInMillis = end.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            while (!day.after(endMillis)) {
                val date = dateFormat.format(day.time)
                times.forEach { time ->
                    val clock = runCatching { timeFormat.parse(time) }.getOrNull() ?: return@forEach
                    val scheduled = (day.clone() as Calendar).apply {
                        val clockCalendar = Calendar.getInstance().apply { timeInMillis = clock.time }
                        set(Calendar.HOUR_OF_DAY, clockCalendar.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, clockCalendar.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val doseId = "${medicine.id}_${date.replace("-", "")}_${time.replace(":", "").replace(" ", "").uppercase(Locale.US)}"
                    val ref = historyCollection.document(doseId)
                    scheduledCount++
                    pendingWrites++
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(ref)
                        val existingStatus = snapshot.getString("status")
                        if (existingStatus != "Taken" && existingStatus != "Missed") {
                            val status = if (System.currentTimeMillis() > scheduled + 90L * 60_000) "Missed" else "Pending"
                            val values = hashMapOf(
                                "doseId" to doseId,
                                "userId" to uid,
                                "medicineId" to medicine.id,
                                "medicineName" to medicine.name,
                                "dosage" to medicine.dosage,
                                "date" to date,
                                "time" to time,
                                "status" to status,
                                "scheduledMillis" to scheduled,
                                "completedMillis" to if (status == "Missed") System.currentTimeMillis() else 0L
                            )
                            transaction.set(ref, values, SetOptions.merge())
                        }
                    }.addOnSuccessListener { finishOne() }.addOnFailureListener { failures++; finishOne() }
                }
                day.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        if (scheduledCount == 0) callback(true)
    }

    fun getUserName(callback: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback("User")
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { callback(it.getString("name") ?: "User") }
            .addOnFailureListener { callback("User") }
    }

    fun getUserEmail(): String = auth.currentUser?.email.orEmpty()

    fun updateUserName(name: String, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(false)
            return
        }
        db.collection("users").document(uid).update("name", name)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun logout() {
        auth.signOut()
    }
}
