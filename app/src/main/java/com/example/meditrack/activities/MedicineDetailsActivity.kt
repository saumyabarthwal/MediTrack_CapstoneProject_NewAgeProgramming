package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.notifications.DoseActions
import com.example.meditrack.notifications.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MedicineDetailsActivity : AppCompatActivity() {
    private val helper = FirebaseHelper()
    private var medicineId = ""
    private var loadedMedicine: com.example.meditrack.models.Medicine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_details)
        medicineId = intent.getStringExtra("medicineId").orEmpty()
        if (medicineId.isEmpty()) {
            Toast.makeText(this, R.string.medicine_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadDetails()
    }

    override fun onResume() {
        super.onResume()
        if (medicineId.isNotEmpty()) loadDetails()
    }

    private fun loadDetails() {
        helper.getMedicines { medicines, error ->
            val medicine = medicines.find { it.id == medicineId }
            if (medicine == null) {
                Toast.makeText(
                    this,
                    if (error == null) getString(R.string.medicine_not_found) else getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@getMedicines
            }
            loadedMedicine = medicine
            findViewById<TextView>(R.id.tvDetailName).text = medicine.name
            findViewById<TextView>(R.id.tvDetailDosage).text = getString(R.string.dosage_label, medicine.dosage)
            val times = if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes else
                medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            findViewById<TextView>(R.id.tvDetailTime).text = getString(R.string.times_label, times.joinToString(", "))
            findViewById<TextView>(R.id.tvDetailFrequency).text = getString(R.string.frequency_label, medicine.frequency)
            findViewById<TextView>(R.id.tvDetailDates).text =
                getString(R.string.medicine_detail_duration, medicine.startDate, medicine.endDate)
            findViewById<TextView>(R.id.tvDetailQuantity).text =
                getString(R.string.quantity_label, medicine.quantity, medicine.quantityUnit)
            findViewById<TextView>(R.id.tvDetailRemaining).text =
                getString(R.string.remaining_stock_format,
                    medicine.quantity,
                    if (medicine.initialQuantity > 0) medicine.initialQuantity else medicine.quantity,
                    medicine.quantityUnit)
            findViewById<TextView>(R.id.tvDetailAmount).text =
                getString(R.string.amount_per_dose_label, medicine.amountPerDose, medicine.quantityUnit)
            val stockStatus = when {
                medicine.quantity <= 0 -> getString(R.string.out_of_stock)
                medicine.quantity <= medicine.lowStockThreshold -> getString(R.string.low_stock)
                else -> getString(R.string.sufficient_stock)
            }
            val statusView = findViewById<TextView>(R.id.tvDetailStatus)
            statusView.text = stockStatus
            statusView.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                if (medicine.quantity <= 0) R.color.status_missed else
                    if (medicine.quantity <= medicine.lowStockThreshold) R.color.status_warning else R.color.status_taken))
            findViewById<android.widget.ProgressBar>(R.id.progressStock).apply {
                val initial = if (medicine.initialQuantity > 0) medicine.initialQuantity else medicine.quantity
                progress = if (initial <= 0) 0 else (medicine.quantity * 100 / initial).coerceIn(0, 100)
            }

            findViewById<Button>(R.id.btnEditMedicine).setOnClickListener {
                startActivity(Intent(this, AddMedicineActivity::class.java).putExtra("medicineId", medicineId))
                finish()
            }
            findViewById<Button>(R.id.btnMarkTaken).setOnClickListener {
                if (times.isEmpty()) {
                    Toast.makeText(this, R.string.no_reminder_time_configured, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
                val day = Calendar.getInstance()
                val scheduledTime = times.map { time ->
                    time to ReminderScheduler.scheduledMillis(day, time)
                }.filter { it.second <= System.currentTimeMillis() }
                    .maxByOrNull { it.second }
                    ?: times.map { time -> time to ReminderScheduler.scheduledMillis(day, time) }
                        .filter { it.second > System.currentTimeMillis() }
                        .minByOrNull { it.second }
                if (scheduledTime == null) {
                    Toast.makeText(this, R.string.no_reminder_time_configured, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val time = scheduledTime.first
                val scheduled = scheduledTime.second
                val doseId = ReminderScheduler.doseId(medicineId, date, time)
                val success = DoseActions.take(
                    doseId, medicineId, medicine.name, medicine.dosage,
                    date, time, scheduled
                )
                if (success) {
                    ReminderScheduler.cancelDose(this, doseId)
                    Toast.makeText(this, R.string.dose_recorded_taken, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.dose_already_completed, Toast.LENGTH_SHORT).show()
                }
            }
            findViewById<Button>(R.id.btnMarkMissed).apply {
                setText(R.string.automatic_missed_status)
                isEnabled = false
                alpha = 0.65f
            }
            findViewById<Button>(R.id.btnDeleteMedicine).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_medicine_title)
                    .setMessage(R.string.delete_medicine_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_medicine) { _, _ ->
                        ReminderScheduler.cancelMedicine(this, medicine)
                        helper.deleteMedicine(medicineId) { success ->
                            Toast.makeText(this,
                                if (success) getString(R.string.medicine_deleted)
                                else getString(R.string.delete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            if (success) finish()
                        }
                    }.show()
            }
        }
    }

    private fun localizedStatus(status: String): String = when (status.lowercase(Locale.ROOT)) {
        "taken" -> getString(R.string.status_taken)
        "missed" -> getString(R.string.status_missed)
        "upcoming", "pending" -> getString(R.string.status_upcoming)
        else -> status
    }
}
