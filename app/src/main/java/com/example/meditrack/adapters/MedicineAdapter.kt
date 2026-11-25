package com.example.meditrack.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.meditrack.R
import com.example.meditrack.models.Medicine
import java.util.Locale

class MedicineAdapter(
    private val context: Context,
    private val medicines: MutableList<Medicine>
) : BaseAdapter() {
    override fun getCount(): Int = medicines.size

    override fun getItem(position: Int): Medicine = medicines[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_medicine, parent, false)
        val medicine = getItem(position)
        view.findViewById<TextView>(R.id.tvMedicineCardName).text = medicine.name
        val times = if (medicine.reminderTimes.isNotEmpty()) medicine.reminderTimes
        else medicine.time.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        view.findViewById<TextView>(R.id.tvMedicineCardInfo).text =
            "${medicine.dosage}  ·  ${medicine.quantityUnit.ifBlank { "Tablet" }}"
        view.findViewById<TextView>(R.id.tvMedicineCardTimes).text =
            if (times.isNotEmpty()) {
                val shown = times.joinToString(", ")
                val frequency = if (medicine.frequency.isBlank()) "Daily" else medicine.frequency
                "◷  $shown   |   $frequency"
            } else "◷  ${context.getString(R.string.no_reminder_times)}"
        view.findViewById<TextView>(R.id.tvMedicineCardStock).text =
            context.getString(
                R.string.remaining_stock_format,
                medicine.quantity,
                if (medicine.initialQuantity > 0) medicine.initialQuantity else medicine.quantity,
                medicine.quantityUnit
            )

        val (status, color) = when {
            medicine.quantity <= 0 ->
                context.getString(R.string.out_of_stock) to R.color.status_missed
            medicine.quantity <= medicine.lowStockThreshold ->
                context.getString(R.string.low_stock) to R.color.status_warning
            else -> {
                val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US)
                val today = dateFormat.format(java.util.Date())
                val todayMillis = runCatching { dateFormat.parse(today)?.time }.getOrNull()
                val startMillis = runCatching { dateFormat.parse(medicine.startDate)?.time }.getOrNull()
                val endMillis = runCatching { dateFormat.parse(medicine.endDate)?.time }.getOrNull()
                if (todayMillis != null && startMillis != null && endMillis != null &&
                    todayMillis in startMillis..endMillis)
                    context.getString(R.string.filter_active) to R.color.status_taken
                else context.getString(R.string.status_upcoming) to R.color.accent
            }
        }
        view.findViewById<TextView>(R.id.tvMedicineCardStatus).apply {
            text = status
            setTextColor(ContextCompat.getColor(context, color))
        }
        return view
    }
}