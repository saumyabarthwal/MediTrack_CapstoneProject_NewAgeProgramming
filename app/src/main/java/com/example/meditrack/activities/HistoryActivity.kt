package com.example.meditrack.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.adapters.HistoryAdapter
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.DoseHistory
import com.example.meditrack.models.Medicine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.meditrack.notifications.DoseVerification

class HistoryActivity : AppCompatActivity() {
    private val helper = FirebaseHelper()
    private var history = listOf<DoseHistory>()
    private var selectedStatus = "All"
    private var selectedMedicineId: String? = null
    private var selectedMedicineName: String? = null
    private var selectedDate: String? = null
    private var medicines = listOf<Medicine>()
    private val visibleHistory = mutableListOf<DoseHistory>()
    private lateinit var adapter: HistoryAdapter
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        adapter = HistoryAdapter(this, visibleHistory)
        findViewById<ListView>(R.id.lvDoseHistory).adapter = adapter
        val requested = intent.getStringExtra("filterStatus").orEmpty()
        val spinner = findViewById<Spinner>(R.id.spHistoryFilter)
        val options = arrayOf(
            getString(R.string.filter_all), getString(R.string.filter_taken),
            getString(R.string.filter_missed), getString(R.string.filter_upcoming)
        )
        val canonical = arrayOf("All", "Taken", "Missed", "Pending")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val initial = canonical.indexOf(requested).takeIf { it >= 0 } ?: 0
        spinner.setSelection(initial)
        selectedStatus = canonical[initial]
        findViewById<TextView>(R.id.tvHistoryTitle).text =
            if (requested == "Taken") getString(R.string.taken_doses)
            else if (requested == "Missed") getString(R.string.missed_doses)
            else getString(R.string.status_all_title)
        findViewById<Button>(R.id.btnFilterAll).setOnClickListener { setStatusFilter("All") }
        findViewById<Button>(R.id.btnFilterTaken).setOnClickListener { setStatusFilter("Taken") }
        findViewById<Button>(R.id.btnFilterMissed).setOnClickListener { setStatusFilter("Missed") }
        findViewById<Button>(R.id.btnFilterUpcoming).setOnClickListener { setStatusFilter("Pending") }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatus = canonical[position]
                filter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Spinner>(R.id.spHistoryMedicine).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val medicine = if (position == 0) null else medicines.getOrNull(position - 1)
                    selectedMedicineId = medicine?.id
                    selectedMedicineName = medicine?.name
                    filter()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        findViewById<Button>(R.id.btnHistoryDate).setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.btnClearHistoryDate).setOnClickListener {
            selectedDate = null
            updateDateFilterLabel()
            filter()
        }
        updateDateFilterLabel()
    }

    override fun onResume() {
        super.onResume()
        helper.getMedicines { records, error ->
            if (error != null) Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            medicines = records
            helper.synchronizeDoseRecords(records) {
                helper.getDoseHistory { doseRecords, historyError ->
                    if (historyError != null) Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                    history = doseRecords.distinctBy { it.doseId.ifBlank { it.id } }
                    filter()
                }
            }
            val uniqueMedicines = records.distinctBy { it.id }
            val options = listOf(getString(R.string.all_medicines_filter)) + uniqueMedicines.map { it.name }
            val spinner = findViewById<Spinner>(R.id.spHistoryMedicine)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            medicines = uniqueMedicines
            val selectedId = selectedMedicineId
            val restoredPosition = if (selectedId == null) 0
                else uniqueMedicines.indexOfFirst { it.id == selectedId }.let { if (it >= 0) it + 1 else 0 }
            if (spinner.selectedItemPosition != restoredPosition) spinner.setSelection(restoredPosition)
        }
    }

    private fun showDatePicker() {
        val initialDate = selectedDate?.let { runCatching { dateFormat.parse(it) }.getOrNull() } ?: Date()
        val calendar = Calendar.getInstance().apply { time = initialDate }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedDate = dateFormat.format(selected.time)
                updateDateFilterLabel()
                filter()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateFilterLabel() {
        findViewById<Button>(R.id.btnClearHistoryDate).visibility =
            if (selectedDate == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvHistoryDateFilter).text =
            selectedDate?.let { getString(R.string.history_date_filter, it) }.orEmpty()
    }

    private fun filter() {
        val filtered = DoseVerification.historyFilter(
            history,
            selectedStatus,
            selectedMedicineId,
            selectedDate
        )
        visibleHistory.clear()
        visibleHistory.addAll(filtered)
        val today = dateFormat.format(Date())
        val daily = history.filter { it.date == today }
        val taken = daily.count { it.status.equals("Taken", true) }
        findViewById<TextView>(R.id.tvDoseSummary).text =
            "$taken / ${daily.size} Doses"
        adapter.notifyDataSetChanged()
    }

    private fun setStatusFilter(status: String) {
        selectedStatus = status
        val canonical = arrayOf("All", "Taken", "Missed", "Pending")
        findViewById<Spinner>(R.id.spHistoryFilter).setSelection(canonical.indexOf(status))
        filter()
    }

    private fun localizedStatus(status: String): String = when (status.lowercase()) {
        "taken" -> getString(R.string.status_taken)
        "missed" -> getString(R.string.status_missed)
        "pending" -> getString(R.string.status_pending)
        else -> status
    }
}
