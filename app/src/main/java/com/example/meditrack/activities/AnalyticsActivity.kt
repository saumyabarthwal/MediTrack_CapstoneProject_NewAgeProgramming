package com.example.meditrack.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.models.DoseHistory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : AppCompatActivity() {
    private val helper = FirebaseHelper()
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private var customStart: Long? = null
    private var customEnd: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        val filters = arrayOf(
            getString(R.string.filter_last_7_days),
            getString(R.string.filter_last_30_days),
            getString(R.string.filter_this_month),
            getString(R.string.filter_custom_range)
        )
        val spinner = findViewById<Spinner>(R.id.spAnalyticsFilter)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 3) selectCustomRange()
                else load(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        load(0)
    }

    private fun selectCustomRange() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val start = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            customStart = start.timeInMillis
            DatePickerDialog(this, { _, ey, em, ed ->
                val end = Calendar.getInstance().apply { set(ey, em, ed, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                customEnd = end.timeInMillis
                load(3)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = start.timeInMillis
            }.show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun load(filter: Int) {
        helper.getDoseHistory { history, error ->
            if (error != null) {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                render(emptyList())
                return@getDoseHistory
            }
            val now = Calendar.getInstance()
            val (start, end) = when (filter) {
                0 -> {
                    val s = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                    s.timeInMillis to now.timeInMillis
                }
                1 -> {
                    val s = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                    s.timeInMillis to now.timeInMillis
                }
                2 -> {
                    val s = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                    s.timeInMillis to now.timeInMillis
                }
                else -> (customStart ?: now.timeInMillis) to (customEnd ?: now.timeInMillis)
            }
            val records = history.filter {
                val millis = if (it.scheduledMillis > 0) it.scheduledMillis else
                    runCatching { dateFormat.parse(it.date)?.time }.getOrNull() ?: 0L
                millis in start..end && (it.status == "Taken" || it.status == "Missed")
            }
            render(records)
        }
    }

    private fun render(records: List<DoseHistory>) {
        val chartTextColor = ContextCompat.getColor(this, R.color.text_secondary)
        val chartAccent = ContextCompat.getColor(this, R.color.accent)
        val taken = records.count { it.status == "Taken" }
        val missed = records.count { it.status == "Missed" }
        val completed = taken + missed
        val adherence = if (completed == 0) 0.0 else taken * 100.0 / completed
        findViewById<TextView>(R.id.tvAnalyticsSummary).text =
            if (completed == 0) getString(R.string.analytics_no_records)
            else getString(R.string.analytics_summary, adherence.toInt(), taken, missed, completed)

        val bar = findViewById<BarChart>(R.id.barChart)
        val labels = mutableListOf<String>()
        val entries = mutableListOf<BarEntry>()
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        for (i in 0..6) {
            val date = dateFormat.format(cal.time)
            val dayRecords = records.filter { it.date == date }
            val dayTaken = dayRecords.count { it.status == "Taken" }
            val dayCompleted = dayRecords.count { it.status == "Taken" || it.status == "Missed" }
            val percent = if (dayCompleted == 0) 0f else dayTaken * 100f / dayCompleted
            val appLocale = resources.configuration.locales[0]
            labels.add(SimpleDateFormat("EEE", appLocale).format(cal.time))
            entries.add(BarEntry(i.toFloat(), percent))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        bar.data = BarData(BarDataSet(entries, getString(R.string.chart_adherence)).apply { color = chartAccent })
        bar.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        bar.xAxis.position = XAxis.XAxisPosition.BOTTOM
        bar.xAxis.textColor = chartTextColor
        bar.axisLeft.textColor = chartTextColor
        bar.axisRight.isEnabled = false
        bar.legend.textColor = chartTextColor
        bar.setNoDataText(getString(R.string.no_completed_records))
        bar.setNoDataTextColor(chartTextColor)
        bar.axisLeft.axisMaximum = 100f
        bar.axisLeft.axisMinimum = 0f
        bar.description.isEnabled = false
        bar.invalidate()

        val line = findViewById<LineChart>(R.id.lineChart)
        val lineEntries = mutableListOf<Entry>()
        val monthLabels = mutableListOf<String>()
        val current = Calendar.getInstance()
        val monthStart = (current.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        var i = 0
        while (monthStart <= current) {
            val date = dateFormat.format(monthStart.time)
            val dayRecords = records.filter { it.date == date }
            val dayTaken = dayRecords.count { it.status == "Taken" }
            val dayCompleted = dayRecords.count { it.status == "Taken" || it.status == "Missed" }
            val percent = if (dayCompleted == 0) 0f else dayTaken * 100f / dayCompleted
            lineEntries.add(Entry(i.toFloat(), percent))
            monthLabels.add(monthStart.get(Calendar.DAY_OF_MONTH).toString())
            monthStart.add(Calendar.DAY_OF_YEAR, 1)
            i++
        }
        line.data = LineData(LineDataSet(lineEntries, getString(R.string.chart_monthly_trend)).apply {
            color = chartAccent
            setCircleColor(chartAccent)
        })
        line.xAxis.valueFormatter = IndexAxisValueFormatter(monthLabels)
        line.xAxis.textColor = chartTextColor
        line.axisLeft.textColor = chartTextColor
        line.axisRight.isEnabled = false
        line.legend.textColor = chartTextColor
        line.setNoDataText(getString(R.string.no_completed_records))
        line.setNoDataTextColor(chartTextColor)
        line.axisLeft.axisMaximum = 100f
        line.axisLeft.axisMinimum = 0f
        line.description.isEnabled = false
        line.invalidate()

        val pie = findViewById<PieChart>(R.id.pieChart)
        val pieEntries = mutableListOf<PieEntry>()
        if (taken > 0) pieEntries.add(PieEntry(taken.toFloat(), getString(R.string.chart_label_taken)))
        if (missed > 0) pieEntries.add(PieEntry(missed.toFloat(), getString(R.string.chart_label_missed)))
        pie.data = PieData(PieDataSet(pieEntries, "").apply {
            colors = listOf(0xFF75B99A.toInt(), 0xFFC96F78.toInt())
        })
        pie.setUsePercentValues(true)
        pie.description.isEnabled = false
        pie.legend.textColor = chartTextColor
        pie.setNoDataText(getString(R.string.no_completed_records))
        pie.setNoDataTextColor(chartTextColor)
        pie.centerText = if (completed == 0) getString(R.string.no_completed_records)
        else "${completed} ${getString(R.string.chart_doses)}"
        pie.invalidate()

        val grouped = records.groupBy { it.medicineName.ifBlank { it.medicineId } }
        val medicineText = if (grouped.isEmpty()) getString(R.string.no_completed_records)
        else grouped.entries.joinToString("\n\n") { (name, items) ->
            val t = items.count { it.status == "Taken" }
            val m = items.count { it.status == "Missed" }
            val total = t + m
            val pct = if (total == 0) 0 else t * 100 / total
            getString(R.string.analytics_medicine_summary, name, total, t, m, pct)
        }
        findViewById<TextView>(R.id.tvMedicineAnalytics).text = medicineText
    }
}
