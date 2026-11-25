package com.example.meditrack.notifications

object DoseUtils {
    fun doseId(medicineId: String, date: String, time: String): String =
        "${medicineId}_${date.replace("-", "")}_${time.replace(":", "").replace(" ", "").uppercase()}"

    fun adherence(taken: Int, missed: Int): Double =
        if (taken + missed == 0) 0.0 else taken * 100.0 / (taken + missed)

    fun nextSnoozeCount(current: Int): Int? =
        if (current >= 4) null else current + 1

    fun recognizableFrequencyCount(frequency: String): Int? =
        when {
            frequency.lowercase().contains("once") || frequency.lowercase().contains("one") -> 1
            frequency.lowercase().contains("twice") || frequency.lowercase().contains("2") -> 2
            frequency.lowercase().contains("three") || frequency.lowercase().contains("3") -> 3
            frequency.lowercase().contains("four") || frequency.lowercase().contains("4") -> 4
            else -> null
        }

    fun isTakenTransitionAllowed(currentStatus: String?): Boolean =
        currentStatus != "Taken" && currentStatus != "Missed"
}
