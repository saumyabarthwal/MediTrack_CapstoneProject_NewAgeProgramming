
package com.example.meditrack.models

data class Medicine(
    var id: String = "",
    var userId: String = "",
    var name: String = "",
    var dosage: String = "",
    var time: String = "",
    var frequency: String = "",
    var startDate: String = "",
    var endDate: String = "",
    var quantity: Int = 0,
    var quantityUnit: String = "",
    var amountPerDose: Int = 1,
    var reminderTimes: MutableList<String> = mutableListOf(),
    var snoozeLimit: Int = 4,
    var status: String = "Upcoming",
    var lowStockThreshold: Int = 5,
    var initialQuantity: Int = 0
)
