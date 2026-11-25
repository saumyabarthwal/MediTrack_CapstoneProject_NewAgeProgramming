package com.example.meditrack.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.meditrack.R
import com.example.meditrack.models.DoseHistory

class HistoryAdapter(
    private val context: Context,
    private val records: MutableList<DoseHistory>
) : BaseAdapter() {
    override fun getCount(): Int = records.size
    override fun getItem(position: Int): DoseHistory = records[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_history, parent, false)
        val record = getItem(position)
        view.findViewById<TextView>(R.id.tvHistoryMedicine).text = record.medicineName
        view.findViewById<TextView>(R.id.tvHistoryDosage).text = record.dosage
        val statusText = when (record.status.lowercase()) {
            "taken" -> "✓  Taken"
            "missed" -> "×  Missed"
            else -> "◷  Upcoming"
        }
        val statusView = view.findViewById<TextView>(R.id.tvHistoryStatus)
        statusView.text = statusText
        val statusColor = when (record.status.lowercase()) {
            "taken" -> R.color.status_taken
            "missed" -> R.color.status_missed
            else -> R.color.accent
        }
        statusView.setTextColor(ContextCompat.getColor(context, statusColor))
        view.findViewById<TextView>(R.id.tvHistoryTime).text =
            "◷  ${record.time}  •  ${
                if (record.status.equals("Taken", true) && record.completedMillis > 0L)
                    "Taken at " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                        .format(java.util.Date(record.completedMillis))
                else record.status
            }"
        return view
    }
}