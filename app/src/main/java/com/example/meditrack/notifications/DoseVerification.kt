package com.example.meditrack.notifications

import com.example.meditrack.models.DoseHistory
import com.example.meditrack.models.Medicine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DoseVerification {
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    fun allMedicines(medicines: List<Medicine>): List<Medicine> =
        medicines.distinctBy { it.id }

    fun searchMedicines(medicines: List<Medicine>, query: String): List<Medicine> =
        allMedicines(medicines).filter { it.name.contains(query.trim(), ignoreCase = true) }

    fun activeMedicines(medicines: List<Medicine>, todayMillis: Long = startOfTodayMillis()): List<Medicine> =
        allMedicines(medicines).filter { medicine ->
            val start = runCatching { dateFormat.parse(medicine.startDate)?.time }.getOrNull()
            val end = runCatching { dateFormat.parse(medicine.endDate)?.time }.getOrNull()
            start != null && end != null && todayMillis in start..end
        }

    fun lowStockMedicines(medicines: List<Medicine>): List<Medicine> =
        allMedicines(medicines).filter { it.quantity <= it.lowStockThreshold }

    fun historyFilter(
        history: List<DoseHistory>,
        status: String,
        medicineId: String?,
        selectedDate: String?
    ): List<DoseHistory> =
        history.distinctBy { it.doseId.ifBlank { it.id } }.filter { record ->
            (status == "All" || record.status.equals(status, ignoreCase = true)) &&
                (selectedDate == null || record.date == selectedDate) &&
                (medicineId == null || record.medicineId == medicineId)
        }

    fun upcomingHistory(history: List<DoseHistory>, nowMillis: Long): List<DoseHistory> =
        history.distinctBy { it.doseId.ifBlank { it.id } }
            .filter { it.status.equals("Pending", true) && it.scheduledMillis > nowMillis }

    fun takenHistory(history: List<DoseHistory>): List<DoseHistory> =
        history.distinctBy { it.doseId.ifBlank { it.id } }
            .filter { it.status.equals("Taken", true) }

    fun missedHistory(history: List<DoseHistory>): List<DoseHistory> =
        history.distinctBy { it.doseId.ifBlank { it.id } }
            .filter { it.status.equals("Missed", true) }

    private fun startOfTodayMillis(): Long =
        runCatching { dateFormat.parse(dateFormat.format(Date()))?.time }.getOrNull()
            ?: 0L
}
