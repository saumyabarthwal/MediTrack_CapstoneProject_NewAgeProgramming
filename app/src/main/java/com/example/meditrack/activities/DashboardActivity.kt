
package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper

class DashboardActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: ArrayAdapter<String>

    private val medicineNames = ArrayList<String>()
    private val medicineIds = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        firebaseHelper = FirebaseHelper()

        val welcomeText = findViewById<TextView>(R.id.tvWelcome)
        val medicineCount = findViewById<TextView>(R.id.tvMedicineCount)
        val medicineList = findViewById<ListView>(R.id.lvMedicines)

        val addButton = findViewById<Button>(R.id.btnAddMedicine)
        val historyButton = findViewById<Button>(R.id.btnHistory)
        val profileButton = findViewById<Button>(R.id.btnProfile)

        // Display user's name
        firebaseHelper.getUserName { name ->
            welcomeText.text = "Welcome, $name!"
        }

        // Medicine list adapter
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            medicineNames
        )

        medicineList.adapter = adapter

        // Open Add Medicine screen
        addButton.setOnClickListener {
            startActivity(
                Intent(this, AddMedicineActivity::class.java)
            )
        }

        // Open Dose History
        historyButton.setOnClickListener {
            startActivity(
                Intent(this, HistoryActivity::class.java)
            )
        }

        // Open Profile
        profileButton.setOnClickListener {
            startActivity(
                Intent(this, ProfileActivity::class.java)
            )
        }

        // Open medicine details

        medicineList.setOnItemClickListener { _, _, position, _ ->

            if (position < medicineIds.size) {
                val intent = Intent(
                    this,
                    MedicineDetailsActivity::class.java
                )

                intent.putExtra("medicineId", medicineIds[position])
                startActivity(intent)
            }
        }


        loadMedicines()
    }

    override fun onResume() {
        super.onResume()
        loadMedicines()
    }

    private fun loadMedicines() {

        firebaseHelper.getMedicines { medicines, error ->

            if (error != null) {
                Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_SHORT
                ).show()
                return@getMedicines
            }

            medicineNames.clear()
            medicineIds.clear()

            for (medicine in medicines) {

                medicineNames.add(
                    "${medicine.name}  •  ${medicine.dosage}\n" +
                            "Time: ${medicine.time}     |     ${medicine.status}"
                )

                medicineIds.add(medicine.id)
            }

            findViewById<TextView>(R.id.tvMedicineCount).text =
                medicines.size.toString()

            if (medicineNames.isEmpty()) {
                medicineNames.add("No medicines added yet.\nTap Add Medicine to get started.")
            }

            adapter.notifyDataSetChanged()
        }
    }
}
