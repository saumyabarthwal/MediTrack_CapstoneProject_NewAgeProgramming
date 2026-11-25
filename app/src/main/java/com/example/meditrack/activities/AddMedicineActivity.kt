package com.example.meditrack.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.Medicine
import com.example.meditrack.notifications.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddMedicineActivity : AppCompatActivity() {
    private lateinit var helper: FirebaseHelper
    private var medicineId = ""
    private var originalStatus = "Upcoming"
    private var originalInitialQuantity = 0
    private val reminderTimes = mutableListOf<String>()
    private lateinit var timesAdapter: ArrayAdapter<String>
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)
        helper = FirebaseHelper()
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        val name = findViewById<EditText>(R.id.etMedicineName)
        val dosage = findViewById<EditText>(R.id.etDosage)
        val frequency = findViewById<EditText>(R.id.etFrequency)
        val start = findViewById<EditText>(R.id.etStartDate)
        val end = findViewById<EditText>(R.id.etEndDate)
        val quantity = findViewById<EditText>(R.id.etQuantity)
        val unit = findViewById<EditText>(R.id.etQuantityUnit)
        val amount = findViewById<EditText>(R.id.etAmountPerDose)
        val threshold = findViewById<EditText>(R.id.etLowStockThreshold)
        val heading = findViewById<TextView>(R.id.tvFormHeading)
        val timesText = findViewById<TextView>(R.id.tvReminderTimes)
        val timesList = findViewById<ListView>(R.id.lvReminderTimes)
        timesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, reminderTimes)
        timesList.adapter = timesAdapter
        timesList.setOnItemClickListener { _, _, position, _ ->
            if (position in reminderTimes.indices) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.reminder_time_dialog)
                    .setItems(arrayOf(getString(R.string.edit), getString(R.string.remove))) { _, choice ->
                        if (choice == 0) showTimePicker(position)
                        else {
                            reminderTimes.removeAt(position)
                            refreshTimes(timesText)
                        }
                    }.show()
            }
        }

        findViewById<Button>(R.id.btnAddReminderTime).setOnClickListener { showTimePicker(null) }
        start.setOnClickListener { showDatePicker(start, null) }
        end.setOnClickListener {
            val parsedStart = runCatching { dateFormat.parse(start.text.toString()) }.getOrNull()
            showDatePicker(end, parsedStart?.time)
        }

        medicineId = intent.getStringExtra("medicineId").orEmpty()
        if (medicineId.isNotEmpty()) {
            heading.setText(R.string.edit_medicine_heading)
            findViewById<Button>(R.id.btnSaveMedicine).setText(R.string.save_changes)
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
                name.setText(medicine.name)
                dosage.setText(medicine.dosage)
                frequency.setText(medicine.frequency)
                start.setText(medicine.startDate)
                end.setText(medicine.endDate)
                quantity.setText(medicine.quantity.toString())
                unit.setText(medicine.quantityUnit)
                amount.setText(medicine.amountPerDose.toString())
                threshold.setText(medicine.lowStockThreshold.toString())
                originalInitialQuantity =
                    if (medicine.initialQuantity > 0) medicine.initialQuantity else medicine.quantity
                originalStatus = medicine.status
                reminderTimes.clear()
                reminderTimes.addAll(
                    if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes
                    else medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
                refreshTimes(timesText)
            }
        }

        findViewById<Button>(R.id.btnSaveMedicine).setOnClickListener {
            val medicineName = name.text.toString().trim()
            val medicineDosage = dosage.text.toString().trim()
            val medicineFrequency = frequency.text.toString().trim()
            val startDate = start.text.toString().trim()
            val endDate = end.text.toString().trim()
            val quantityValue = quantity.text.toString().toIntOrNull()
            val unitValue = unit.text.toString().trim()
            val amountValue = amount.text.toString().trim().toIntOrNull() ?: 1
            val thresholdValue = threshold.text.toString().trim().toIntOrNull() ?: 5

            if (medicineName.isEmpty() || medicineDosage.isEmpty() || medicineFrequency.isEmpty() ||
                startDate.isEmpty() || endDate.isEmpty() || reminderTimes.isEmpty() ||
                quantityValue == null || quantityValue < 0 || unitValue.isEmpty() ||
                amountValue < 1 ||
                thresholdValue < 0) {
                Toast.makeText(this, R.string.form_incomplete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val startMillis = runCatching { dateFormat.parse(startDate)?.time }.getOrNull()
            val endMillis = runCatching { dateFormat.parse(endDate)?.time }.getOrNull()
            if (startMillis == null || endMillis == null || endMillis < startMillis) {
                Toast.makeText(this, R.string.end_date_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val expected = frequencyExpected(medicineFrequency)
            if (expected != null && reminderTimes.size != expected) {
                Toast.makeText(this, getString(R.string.frequency_times_mismatch, expected), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val medicine = Medicine(
                id = medicineId, name = medicineName, dosage = medicineDosage,
                time = reminderTimes.joinToString(", "), frequency = medicineFrequency,
                startDate = startDate, endDate = endDate, quantity = quantityValue,
                quantityUnit = unitValue, amountPerDose = amountValue,
                reminderTimes = reminderTimes.toMutableList(), status = originalStatus,
                lowStockThreshold = thresholdValue,
                initialQuantity = if (medicineId.isEmpty()) quantityValue
                    else if (quantityValue > originalInitialQuantity) quantityValue else originalInitialQuantity
            )
            val save = findViewById<Button>(R.id.btnSaveMedicine)
            save.isEnabled = false
            if (medicineId.isEmpty()) {
                helper.addMedicine(medicine) { success, message ->
                    save.isEnabled = true
                    if (success) {
                        ReminderScheduler.scheduleMedicine(this, medicine)
                        if (medicine.quantity > medicine.lowStockThreshold) {
                            getSharedPreferences("MediTrackLowStock", MODE_PRIVATE)
                                .edit().remove("notified_${medicine.id}").apply()
                        }
                    }
                    Toast.makeText(
                        this,
                        if (success) getString(R.string.medicine_added) else getString(R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (success) finish()
                }
            } else {
                helper.getMedicines { medicines, _ ->
                    medicines.find { it.id == medicineId }?.let { existing ->
                        ReminderScheduler.cancelMedicine(this, existing)
                    }
                    helper.updateMedicine(medicine) { success ->
                        save.isEnabled = true
                        if (success) {
                            ReminderScheduler.scheduleMedicine(this, medicine)
                            if (medicine.quantity > medicine.lowStockThreshold) {
                                getSharedPreferences("MediTrackLowStock", MODE_PRIVATE)
                                    .edit().remove("notified_${medicine.id}").apply()
                            }
                            Toast.makeText(this, R.string.medicine_saved, Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, R.string.status_update_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showTimePicker(editIndex: Int?) {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val value = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
            if (reminderTimes.withIndex().any {
                    it.value.equals(value, ignoreCase = true) && it.index != editIndex
                }) {
                Toast.makeText(this, R.string.reminder_time_duplicate, Toast.LENGTH_SHORT).show()
            } else if (editIndex != null) {
                reminderTimes[editIndex] = value
                refreshTimes(findViewById(R.id.tvReminderTimes))
            } else {
                reminderTimes.add(value)
                refreshTimes(findViewById(R.id.tvReminderTimes))
            }
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun showDatePicker(field: EditText, minMillis: Long?) {
        val initial = runCatching { dateFormat.parse(field.text.toString()) }.getOrNull()?.time
            ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            field.setText(dateFormat.format(selected.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            if (minMillis != null) datePicker.minDate = minMillis
        }.show()
    }

    private fun refreshTimes(text: TextView) {
        timesAdapter.notifyDataSetChanged()
        text.text = if (reminderTimes.isEmpty()) getString(R.string.no_reminder_times)
        else reminderTimes.joinToString("\n")
    }

    private fun frequencyExpected(frequency: String): Int? {
        val value = frequency.lowercase(Locale.US)
        return when {
            value.contains("once") || value.contains("one") || value.contains("एक") -> 1
            value.contains("twice") || value.contains("2") || value.contains("दो") -> 2
            value.contains("three") || value.contains("3") || value.contains("तीन") -> 3
            value.contains("four") || value.contains("4") || value.contains("चार") -> 4
            else -> null
        }
    }
}
