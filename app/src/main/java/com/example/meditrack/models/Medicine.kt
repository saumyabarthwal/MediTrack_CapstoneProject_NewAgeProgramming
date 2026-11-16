
package com.example.meditrack.models

data class Medicine(
    var id: String = "",
    var name: String = "",
    var dosage: String = "",
    var time: String = "",
    var frequency: String = "",
    var startDate: String = "",
    var endDate: String = "",
    var quantity: Int = 0,
    var status: String = "Upcoming"
)
