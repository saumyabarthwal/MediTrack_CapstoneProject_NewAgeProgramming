
package com.example.meditrack.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.meditrack.models.Medicine
import com.example.meditrack.models.DoseHistory

class FirebaseHelper {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // REGISTER USER
    fun registerUser(
        name: String,
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                val userId = auth.currentUser?.uid

                if (userId == null) {
                    callback(false, "Unable to get user information")
                    return@addOnSuccessListener
                }

                val user = hashMapOf(
                    "name" to name,
                    "email" to email
                )

                db.collection("users")
                    .document(userId)
                    .set(user)
                    .addOnSuccessListener {
                        callback(true, "Registration successful")
                    }
                    .addOnFailureListener {
                        callback(false, it.message ?: "Profile creation failed")
                    }
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Registration failed")
            }
    }

    // LOGIN USER
    fun loginUser(
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                callback(true, "Login successful")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Login failed")
            }
    }

    // UPDATE MEDICINE DETAILS
    fun updateMedicine(
        medicine: Medicine,
        callback: (Boolean) -> Unit
    ) {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(false)
            return
        }

        if (medicine.id.isEmpty()) {
            callback(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("medicines")
            .document(medicine.id)
            .set(medicine)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }


    // ADD MEDICINE
    fun addMedicine(
        medicine: Medicine,
        callback: (Boolean, String) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(false, "Please login first")
            return
        }

        val document = db.collection("users")
            .document(userId)
            .collection("medicines")
            .document()

        medicine.id = document.id

        document.set(medicine)
            .addOnSuccessListener {
                callback(true, "Medicine added successfully")
            }
            .addOnFailureListener {
                callback(false, it.message ?: "Unable to add medicine")
            }
    }

    // GET MEDICINES
    fun getMedicines(
        callback: (List<Medicine>, String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(emptyList(), "Please login first")
            return
        }

        db.collection("users")
            .document(userId)
            .collection("medicines")
            .get()
            .addOnSuccessListener { result ->

                val medicines = result.documents.mapNotNull { document ->
                    document.toObject(Medicine::class.java)?.apply {
                        id = document.id
                    }
                }

                callback(medicines, null)
            }
            .addOnFailureListener {
                callback(emptyList(), it.message ?: "Unable to load medicines")
            }
    }

    // UPDATE MEDICINE STATUS
    fun updateMedicineStatus(
        medicineId: String,
        status: String,
        callback: (Boolean) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("medicines")
            .document(medicineId)
            .update("status", status)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // DELETE MEDICINE
    fun deleteMedicine(
        medicineId: String,
        callback: (Boolean) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("medicines")
            .document(medicineId)
            .delete()
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // ADD DOSE HISTORY
    fun addDoseHistory(
        history: DoseHistory,
        callback: (Boolean) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("history")
            .add(history)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    // GET DOSE HISTORY
    fun getDoseHistory(
        callback: (List<DoseHistory>, String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback(emptyList(), "Please login first")
            return
        }

        db.collection("users")
            .document(userId)
            .collection("history")
            .get()
            .addOnSuccessListener { result ->

                val history = result.documents.mapNotNull { document ->
                    document.toObject(DoseHistory::class.java)?.apply {
                        id = document.id
                    }
                }

                callback(history, null)
            }
            .addOnFailureListener {
                callback(emptyList(), it.message ?: "Unable to load history")
            }
    }

    // GET USER NAME
    fun getUserName(callback: (String) -> Unit) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            callback("User")
            return
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                callback(document.getString("name") ?: "User")
            }
            .addOnFailureListener {
                callback("User")
            }
    }

    // GET USER EMAIL
    fun getUserEmail(): String {
        return auth.currentUser?.email ?: ""
    }

    // LOGOUT
    fun logout() {
        auth.signOut()
    }
}
