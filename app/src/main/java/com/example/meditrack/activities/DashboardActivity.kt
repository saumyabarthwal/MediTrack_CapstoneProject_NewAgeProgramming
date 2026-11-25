package com.example.meditrack.activities

import android.content.Intent
import android.app.AlarmManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.meditrack.R
import com.example.meditrack.adapters.MedicineAdapter
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.Medicine
import com.example.meditrack.models.DoseHistory
import com.example.meditrack.notifications.DoseVerification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {
    private val helper = FirebaseHelper()
    private var medicines = listOf<Medicine>()
    private var history = listOf<DoseHistory>()
    private val visibleMedicines = mutableListOf<Medicine>()
    private lateinit var adapter: MedicineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboardRoot)) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            windowInsets
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
        adapter = MedicineAdapter(this, visibleMedicines)
        findViewById<ListView>(R.id.lvMedicines).adapter = adapter
        helper.getUserName {
            val displayName = if (it == "User") getString(R.string.generic_user) else it
            findViewById<TextView>(R.id.tvWelcome).text = getString(R.string.welcome_user, displayName)
        }

        findViewById<Button>(R.id.btnAddMedicine).setOnClickListener {
            startActivity(Intent(this, AddMedicineActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnDashboardHome).setOnClickListener {
            findViewById<android.widget.ScrollView>(R.id.dashboardScroll).smoothScrollTo(0, 0)
            selectNavigation(R.id.btnDashboardHome)
        }
        findViewById<android.view.View>(R.id.btnDashboardMeds).setOnClickListener {
            val scroll = findViewById<android.widget.ScrollView>(R.id.dashboardScroll)
            scroll.smoothScrollTo(0, findViewById<android.view.View>(R.id.tvMedicineSection).top)
            selectNavigation(R.id.btnDashboardMeds)
        }
        findViewById<android.view.View>(R.id.btnMissed).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java).putExtra("filterStatus", "Missed"))
        }
        findViewById<android.view.View>(R.id.btnMore).setOnClickListener {
            val options = arrayOf(
                getString(R.string.analytics),
                getString(R.string.profile),
                getString(R.string.settings)
            )
            AlertDialog.Builder(this).setTitle(R.string.profile_settings).setItems(options) { _, selected ->
                when (selected) {
                    0 -> startActivity(Intent(this, AnalyticsActivity::class.java))
                    1, 2 -> startActivity(Intent(this, ProfileActivity::class.java))
                }
            }.show()
        }
        val spinner = findViewById<Spinner>(R.id.spMedicineStatus)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                getString(R.string.filter_all), getString(R.string.filter_upcoming),
                getString(R.string.filter_taken), getString(R.string.filter_missed),
                getString(R.string.filter_active), getString(R.string.low_stock)
            ))
        findViewById<EditText>(R.id.etMedicineSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMedicines(s?.toString().orEmpty(), canonicalFilter(spinner.selectedItemPosition))
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                filterMedicines(
                    findViewById<EditText>(R.id.etMedicineSearch).text.toString(),
                    canonicalFilter(position)
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        findViewById<ListView>(R.id.lvMedicines).setOnItemClickListener { _, _, position, _ ->
            if (position in visibleMedicines.indices) {
                startActivity(Intent(this, MedicineDetailsActivity::class.java)
                    .putExtra("medicineId", visibleMedicines[position].id))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        helper.getMedicines { list, error ->
            if (error != null) Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            medicines = list
            helper.synchronizeDoseRecords(list) {
                loadDoseData(list)
            }
            findViewById<TextView>(R.id.tvMedicineCount).text = getString(R.string.medicines_count, list.size)
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
            val todayMillis = runCatching {
                SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(today)?.time ?: 0L
            }.getOrDefault(0L)
            val todayCount = list.sumOf { medicine ->
                val times = if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes else
                    medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val startMillis = runCatching {
                    SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(medicine.startDate)?.time ?: Long.MAX_VALUE
                }.getOrDefault(Long.MAX_VALUE)
                val endMillis = runCatching {
                    SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(medicine.endDate)?.time ?: Long.MIN_VALUE
                }.getOrDefault(Long.MIN_VALUE)
                if (todayMillis in startMillis..endMillis) times.size else 0
            }
            findViewById<TextView>(R.id.tvTodayCount).text = getString(R.string.today_count, todayCount)
        }
    }

    private fun loadDoseData(list: List<Medicine>) {
            helper.getDoseHistory { records, historyError ->
                if (historyError != null) {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                    findViewById<TextView>(R.id.tvTakenCount).text = getString(R.string.taken_count, 0)
                    findViewById<TextView>(R.id.tvMissedCount).text = getString(R.string.missed_count, 0)
                    findViewById<TextView>(R.id.tvAdherence).setText(R.string.adherence_empty)
                    findViewById<TextView>(R.id.tvUpcomingCount).text = getString(R.string.upcoming_count, 0)
                    return@getDoseHistory
                }
                history = records.distinctBy { it.doseId.ifBlank { it.id } }
                val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
                val todayRecords = history.filter { it.date == today }
                val taken = todayRecords.distinctBy { it.doseId.ifBlank { it.id } }.count { it.status == "Taken" }
                val missed = todayRecords.distinctBy { it.doseId.ifBlank { it.id } }.count { it.status == "Missed" }
                findViewById<TextView>(R.id.tvTakenCount).text = getString(R.string.taken_count, taken)
                findViewById<TextView>(R.id.tvMissedCount).text = getString(R.string.missed_count, missed)
                val completed = history.count { it.status == "Taken" || it.status == "Missed" }
                val adherence = if (completed == 0) null else
                    history.count { it.status == "Taken" } * 100.0 / completed
                findViewById<TextView>(R.id.tvAdherence).text =
                    if (adherence == null) getString(R.string.adherence_empty)
                    else getString(R.string.adherence_value, adherence.toInt())
                findViewById<TextView>(R.id.tvUpcomingCount).text =
                    getString(R.string.upcoming_count,
                        history.count { it.status == "Pending" && it.scheduledMillis > System.currentTimeMillis() })
                val lowStock = list.filter { it.quantity <= it.lowStockThreshold }
                val lowStockView = findViewById<TextView>(R.id.tvLowStock)
                if (lowStock.isEmpty()) {
                    lowStockView.visibility = android.view.View.GONE
                } else {
                    lowStockView.visibility = android.view.View.VISIBLE
                    lowStockView.text = getString(R.string.low_stock_medicines) + ": " +
                        lowStock.joinToString(", ") { it.name }
                }
            }
            filterMedicines(
                findViewById<EditText>(R.id.etMedicineSearch).text.toString(),
                canonicalFilter(findViewById<Spinner>(R.id.spMedicineStatus).selectedItemPosition)
            )
    }

    private fun filterMedicines(search: String, status: String) {
        val now = System.currentTimeMillis()
        val filtered = when (status) {
            "Upcoming" -> DoseVerification.upcomingHistory(history, now)
                .mapNotNull { record -> medicines.find { it.id == record.medicineId } }
                .distinctBy { it.id }
            "Taken" -> DoseVerification.takenHistory(history)
                .mapNotNull { record -> medicines.find { it.id == record.medicineId } }
                .distinctBy { it.id }
            "Missed" -> DoseVerification.missedHistory(history)
                .mapNotNull { record -> medicines.find { it.id == record.medicineId } }
                .distinctBy { it.id }
            "Active" -> DoseVerification.activeMedicines(medicines)
            "Low Stock" -> DoseVerification.lowStockMedicines(medicines)
            else -> DoseVerification.allMedicines(medicines)
        }.let { DoseVerification.searchMedicines(it, search) }
        visibleMedicines.clear()
        visibleMedicines.addAll(filtered)
        findViewById<ListView>(R.id.lvMedicines).visibility =
            if (filtered.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<TextView>(R.id.tvEmptyMedicines).visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun canonicalFilter(position: Int): String =
        arrayOf("All", "Upcoming", "Taken", "Missed", "Active", "Low Stock").getOrElse(position) { "All" }

    private fun selectNavigation(selectedId: Int) {
        val selectedColor = getColor(R.color.accent)
        val defaultColor = getColor(R.color.text_secondary)
        listOf(R.id.btnDashboardHome, R.id.btnDashboardMeds, R.id.btnHistory, R.id.btnMore)
            .forEach { id ->
                findViewById<TextView>(id).setTextColor(
                    if (id == selectedId) selectedColor else defaultColor
                )
            }
    }

}
