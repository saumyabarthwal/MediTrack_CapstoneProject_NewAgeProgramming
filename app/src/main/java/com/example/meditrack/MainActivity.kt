
package com.example.meditrack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.activities.AddMedicineActivity
import com.example.meditrack.activities.HistoryActivity
import com.example.meditrack.activities.ProfileActivity
import com.example.meditrack.activities.MedicineDetailsActivity
import com.example.meditrack.firebase.FirebaseHelper

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var medicineList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val medicineNames = ArrayList<String>()
    private val medicineIds = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        firebaseHelper = FirebaseHelper()

        val welcomeText = findViewById<TextView>(R.id.tvWelcome)
        medicineList = findViewById(R.id.lvMedicines)

        val addButton = findViewById<Button>(R.id.btnAddMedicine)
        val historyButton = findViewById<Button>(R.id.btnHistory)
        val profileButton = findViewById<Button>(R.id.btnProfile)

        firebaseHelper.getUserName { name ->
            welcomeText.text = "Welcome, $name!"
        }

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            medicineNames
        )

        medicineList.adapter = adapter

        addButton.setOnClickListener {
            startActivity(Intent(this, AddMedicineActivity::class.java))
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        medicineList.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, MedicineDetailsActivity::class.java)
            intent.putExtra("medicineId", medicineIds[position])
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadMedicines()
    }

    private fun loadMedicines() {
        firebaseHelper.getMedicines { medicines, error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@getMedicines
            }

            medicineNames.clear()
            medicineIds.clear()

            for (medicine in medicines) {
                medicineNames.add(
                    "${medicine.name} - ${medicine.dosage}\n${medicine.time} | ${medicine.status}"
                )
                medicineIds.add(medicine.id)
            }

            adapter.notifyDataSetChanged()
        }
    }
}
