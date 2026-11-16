
package com.example.meditrack.activities


import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.Medicine

class AddMedicineActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper
    private var medicineId = ""
    private var originalStatus = "Upcoming"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)

        firebaseHelper = FirebaseHelper()

        val name = findViewById<EditText>(R.id.etMedicineName)
        val dosage = findViewById<EditText>(R.id.etDosage)
        val time = findViewById<EditText>(R.id.etMedicineTime)
        val frequency = findViewById<EditText>(R.id.etFrequency)
        val startDate = findViewById<EditText>(R.id.etStartDate)
        val endDate = findViewById<EditText>(R.id.etEndDate)
        val quantity = findViewById<EditText>(R.id.etQuantity)

        val saveButton = findViewById<Button>(R.id.btnSaveMedicine)
        val heading = findViewById<TextView>(R.id.tvFormHeading)

        medicineId = intent.getStringExtra("medicineId") ?: ""

        // Check whether this is Add or Edit mode
        if (medicineId.isNotEmpty()) {

            heading.text = "Edit Medicine"
            saveButton.text = "Save Changes"

            firebaseHelper.getMedicines { medicines, error ->

                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    finish()
                    return@getMedicines
                }

                val medicine = medicines.find { it.id == medicineId }

                if (medicine == null) {
                    Toast.makeText(
                        this,
                        "Medicine not found",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@getMedicines
                }

                name.setText(medicine.name)
                dosage.setText(medicine.dosage)
                time.setText(medicine.time)
                frequency.setText(medicine.frequency)
                startDate.setText(medicine.startDate)
                endDate.setText(medicine.endDate)
                quantity.setText(medicine.quantity.toString())

                originalStatus = medicine.status
            }
        }

        saveButton.setOnClickListener {

            val medicineName = name.text.toString().trim()
            val medicineDosage = dosage.text.toString().trim()
            val medicineTime = time.text.toString().trim()
            val medicineFrequency = frequency.text.toString().trim()
            val medicineStartDate = startDate.text.toString().trim()
            val medicineEndDate = endDate.text.toString().trim()
            val medicineQuantity = quantity.text.toString().toIntOrNull()

            if (
                medicineName.isEmpty() ||
                medicineDosage.isEmpty() ||
                medicineTime.isEmpty() ||
                medicineFrequency.isEmpty() ||
                medicineStartDate.isEmpty() ||
                medicineEndDate.isEmpty() ||
                medicineQuantity == null ||
                medicineQuantity < 0
            ) {
                Toast.makeText(
                    this,
                    "Please enter valid details in all fields",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val medicine = Medicine(
                id = medicineId,
                name = medicineName,
                dosage = medicineDosage,
                time = medicineTime,
                frequency = medicineFrequency,
                startDate = medicineStartDate,
                endDate = medicineEndDate,
                quantity = medicineQuantity,
                status = originalStatus
            )

            saveButton.isEnabled = false

            if (medicineId.isEmpty()) {

                // Add new medicine
                firebaseHelper.addMedicine(medicine) { success, message ->

                    saveButton.isEnabled = true

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()

                    if (success) {
                        finish()
                    }
                }

            } else {

                // Update existing medicine
                firebaseHelper.updateMedicine(medicine) { success ->

                    saveButton.isEnabled = true

                    if (success) {
                        Toast.makeText(
                            this,
                            "Medicine details updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Unable to update medicine",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
