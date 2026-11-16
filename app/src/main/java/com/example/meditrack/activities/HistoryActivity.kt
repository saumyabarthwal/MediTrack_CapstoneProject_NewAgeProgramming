
package com.example.meditrack.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper

class HistoryActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: ArrayAdapter<String>
    private val historyItems = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        firebaseHelper = FirebaseHelper()

        val listView = findViewById<ListView>(R.id.lvHistory)

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            historyItems
        )

        listView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        firebaseHelper.getDoseHistory { history, error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                return@getDoseHistory
            }

            historyItems.clear()

            for (item in history) {
                historyItems.add(
                    "${item.medicineName} - ${item.dosage}\n${item.date} | ${item.time}\nStatus: ${item.status}"
                )
            }

            if (historyItems.isEmpty()) {
                historyItems.add("No dose history available yet.")
            }

            adapter.notifyDataSetChanged()
        }
    }
}
