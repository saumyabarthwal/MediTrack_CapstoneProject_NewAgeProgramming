
package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.DoseHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicineDetailsActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper
    private var medicineId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_details)

        firebaseHelper = FirebaseHelper()
        medicineId = intent.getStringExtra("medicineId") ?: ""

        val name = findViewById<TextView>(R.id.tvDetailName)
        val dosage = findViewById<TextView>(R.id.tvDetailDosage)
        val time = findViewById<TextView>(R.id.tvDetailTime)
        val frequency = findViewById<TextView>(R.id.tvDetailFrequency)
        val dates = findViewById<TextView>(R.id.tvDetailDates)
        val quantity = findViewById<TextView>(R.id.tvDetailQuantity)
        val status = findViewById<TextView>(R.id.tvDetailStatus)

        val editButton = findViewById<Button>(R.id.btnEditMedicine)
        val takenButton = findViewById<Button>(R.id.btnMarkTaken)
        val missedButton = findViewById<Button>(R.id.btnMarkMissed)
        val deleteButton = findViewById<Button>(R.id.btnDeleteMedicine)

        if (medicineId.isEmpty()) {
            Toast.makeText(this, "Medicine ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firebaseHelper.getMedicines { medicines, error ->

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                finish()
                return@getMedicines
            }

            val medicine = medicines.find { it.id == medicineId }

            if (medicine == null) {
                Toast.makeText(this, "Medicine not found", Toast.LENGTH_SHORT).show()
                finish()
                return@getMedicines
            }

            // Display medicine details
            name.text = medicine.name
            dosage.text = "Dosage: ${medicine.dosage}"
            time.text = "Time: ${medicine.time}"
            frequency.text = "Frequency: ${medicine.frequency}"
            dates.text = "Duration: ${medicine.startDate} to ${medicine.endDate}"
            quantity.text = "Quantity: ${medicine.quantity}"
            status.text = "Status: ${medicine.status}"

            // Edit Medicine
            editButton.setOnClickListener {
                val intent = Intent(this, AddMedicineActivity::class.java)
                intent.putExtra("medicineId", medicineId)
                startActivity(intent)
                finish()
            }

            // Mark as Taken
            takenButton.setOnClickListener {
                updateStatus(
                    "Taken",
                    medicine.name,
                    medicine.dosage,
                    medicine.time
                )
            }

            // Mark as Missed
            missedButton.setOnClickListener {
                updateStatus(
                    "Missed",
                    medicine.name,
                    medicine.dosage,
                    medicine.time
                )
            }

            // Delete Medicine
            deleteButton.setOnClickListener {
                firebaseHelper.deleteMedicine(medicineId) { success ->

                    if (success) {
                        Toast.makeText(
                            this,
                            "Medicine deleted",
                            Toast.LENGTH_SHORT
                        ).show()

                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Unable to delete medicine",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Update medicine status and save dose history
    private fun updateStatus(
        newStatus: String,
        medicineName: String,
        dosage: String,
        medicineTime: String
    ) {

        firebaseHelper.updateMedicineStatus(medicineId, newStatus) { success ->

            if (success) {

                val date = SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.getDefault()
                ).format(Date())

                val history = DoseHistory(
                    medicineName = medicineName,
                    dosage = dosage,
                    date = date,
                    time = medicineTime,
                    status = newStatus
                )

                firebaseHelper.addDoseHistory(history) { historySuccess ->

                    if (historySuccess) {
                        Toast.makeText(
                            this,
                            "Status updated to $newStatus",
                            Toast.LENGTH_SHORT
                        ).show()

                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Status updated, but history could not be saved",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } else {
                Toast.makeText(
                    this,
                    "Unable to update status",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
