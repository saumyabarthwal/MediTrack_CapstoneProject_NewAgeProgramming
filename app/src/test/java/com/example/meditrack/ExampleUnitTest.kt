package com.example.meditrack

import org.junit.Test
import org.junit.Assert.*
import com.example.meditrack.notifications.DoseUtils
import com.example.meditrack.models.DoseHistory
import com.example.meditrack.models.Medicine
import com.example.meditrack.notifications.DoseVerification

class ExampleUnitTest {
    @Test
    fun doseIdIsDeterministicAndIndependent() {
        val first = DoseUtils.doseId("medicineA", "21-11-2026", "9:00 AM")
        val second = DoseUtils.doseId("medicineA", "21-11-2026", "9:00 PM")
        assertEquals(first, DoseUtils.doseId("medicineA", "21-11-2026", "9:00 AM"))
        assertNotEquals(first, second)
    }

    @Test
    fun adherenceExcludesPendingDoses() {
        assertEquals(75.0, DoseUtils.adherence(3, 1), 0.001)
        assertEquals(0.0, DoseUtils.adherence(0, 0), 0.001)
    }

    @Test
    fun snoozeLimitStopsAtFour() {
        assertEquals(1, DoseUtils.nextSnoozeCount(0))
        assertEquals(4, DoseUtils.nextSnoozeCount(3))
        assertNull(DoseUtils.nextSnoozeCount(4))
    }

    @Test
    fun recognizedFrequencyMatchesReminderCount() {
        assertEquals(2, DoseUtils.recognizableFrequencyCount("Twice a day"))
        assertEquals(3, DoseUtils.recognizableFrequencyCount("Three times a day"))
        assertNull(DoseUtils.recognizableFrequencyCount("Custom"))
    }

    @Test
    fun takenAndMissedAreMutuallyExclusive() {
        assertTrue(DoseUtils.isTakenTransitionAllowed(null))
        assertTrue(DoseUtils.isTakenTransitionAllowed("Pending"))
        assertFalse(DoseUtils.isTakenTransitionAllowed("Taken"))
        assertFalse(DoseUtils.isTakenTransitionAllowed("Missed"))
    }

    @Test
    fun allSearchAndLowStockFiltersUseUniqueMedicines() {
        val crocin = Medicine(id = "c1", name = "Crocin", quantity = 4, lowStockThreshold = 5,
            startDate = "24-11-2026", endDate = "30-11-2026")
        val dolo = Medicine(id = "d1", name = "Dolo 45", quantity = 20, lowStockThreshold = 5,
            startDate = "24-11-2026", endDate = "30-11-2026")
        val duplicateCrocin = crocin.copy()
        val medicines = listOf(crocin, duplicateCrocin, dolo)

        assertEquals(listOf("c1", "d1"), DoseVerification.allMedicines(medicines).map { it.id })
        assertEquals(listOf("c1"), DoseVerification.searchMedicines(medicines, "cro").map { it.id })
        assertEquals(listOf("c1"), DoseVerification.lowStockMedicines(medicines).map { it.id })
    }

    @Test
    fun activeFilterUsesMedicationDatesNotDoseStatus() {
        val active = Medicine(id = "active", name = "Crocin", status = "Taken",
            startDate = "24-11-2026", endDate = "30-11-2026")
        val ended = Medicine(id = "ended", name = "Dolo", status = "Taken",
            startDate = "20-11-2026", endDate = "23-11-2026")
        val todayMillis = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
            .parse("24-11-2026")!!.time

        assertEquals(listOf("active"),
            DoseVerification.activeMedicines(listOf(active, ended), todayMillis).map { it.id })
    }

    @Test
    fun doseFiltersKeepTakenMissedAndUpcomingIndependent() {
        val now = 1_700_000_000_000L
        val records = listOf(
            DoseHistory(doseId = "c1_am", medicineId = "c1", medicineName = "Crocin",
                date = "24-11-2026", time = "09:00 AM", status = "Taken",
                scheduledMillis = now - 10_000),
            DoseHistory(doseId = "c1_pm", medicineId = "c1", medicineName = "Crocin",
                date = "24-11-2026", time = "03:25 PM", status = "Pending",
                scheduledMillis = now + 300_000),
            DoseHistory(doseId = "d1_missed", medicineId = "d1", medicineName = "Dolo",
                date = "24-11-2026", time = "08:00 AM", status = "Missed",
                scheduledMillis = now - 20_000)
        )

        assertEquals(1, DoseVerification.takenHistory(records).size)
        assertEquals(1, DoseVerification.missedHistory(records).size)
        assertEquals(1, DoseVerification.upcomingHistory(records, now).size)
    }

    @Test
    fun historyFilterCombinesDateStatusAndMedicineId() {
        val records = listOf(
            DoseHistory(doseId = "crocin_taken", medicineId = "c1", medicineName = "Crocin",
                date = "24-11-2026", status = "Taken"),
            DoseHistory(doseId = "dolo_taken", medicineId = "d1", medicineName = "Dolo",
                date = "24-11-2026", status = "Taken"),
            DoseHistory(doseId = "crocin_missed", medicineId = "c1", medicineName = "Crocin",
                date = "24-11-2026", status = "Missed"),
            DoseHistory(doseId = "crocin_previous", medicineId = "c1", medicineName = "Crocin",
                date = "23-11-2026", status = "Taken")
        )

        val result = DoseVerification.historyFilter(records, "Taken", "c1", "24-11-2026")
        assertEquals(listOf("crocin_taken"), result.map { it.doseId })
    }
}