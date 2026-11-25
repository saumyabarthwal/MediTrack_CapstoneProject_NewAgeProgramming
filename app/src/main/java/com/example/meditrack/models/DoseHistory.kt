
package com.example.meditrack.models

data class DoseHistory(
    var id: String = "",
    var doseId: String = "",
    var userId: String = "",
    var medicineId: String = "",
    var medicineName: String = "",
    var dosage: String = "",
    var date: String = "",
    var time: String = "",
    var status: String = "Pending",
    var scheduledMillis: Long = 0L,
    var completedMillis: Long = 0L,
    var snoozeCount: Int = 0
)
