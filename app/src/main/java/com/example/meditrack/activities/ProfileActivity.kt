
package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper

class ProfileActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        firebaseHelper = FirebaseHelper()

        val nameText = findViewById<TextView>(R.id.tvProfileName)
        val emailText = findViewById<TextView>(R.id.tvProfileEmail)
        val avatarText = findViewById<TextView>(R.id.tvAvatar)
        val medicineCount = findViewById<TextView>(R.id.tvMedicineCount)
        val languageValue = findViewById<TextView>(R.id.tvLanguageValue)

        // Load user name
        firebaseHelper.getUserName { name ->
            nameText.text = name

            avatarText.text = if (name.isNotEmpty()) {
                name.first().uppercase()
            } else {
                "U"
            }
        }

        // Load user email
        emailText.text = firebaseHelper.getUserEmail()

        // Load medicine count
        firebaseHelper.getMedicines { medicines, error ->
            if (error == null) {
                medicineCount.text = medicines.size.toString()
            }
        }

        // Load saved language
        val preferences = getSharedPreferences(
            "MediTrackSettings",
            MODE_PRIVATE
        )

        languageValue.text = preferences.getString(
            "language",
            "English"
        )

        // Dose History
        findViewById<android.view.View>(R.id.layoutHistory)
            .setOnClickListener {
                startActivity(
                    Intent(this, HistoryActivity::class.java)
                )
            }

        // Reminder Preferences
        findViewById<android.view.View>(R.id.layoutReminders)
            .setOnClickListener {
                showReminderDialog()
            }

        // Language
        findViewById<android.view.View>(R.id.layoutLanguage)
            .setOnClickListener {
                showLanguageDialog(languageValue)
            }

        // Privacy Information
        findViewById<android.view.View>(R.id.layoutPrivacy)
            .setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Privacy Information")
                    .setMessage(
                        "MediTrack stores your account and medicine " +
                                "records in Firebase Firestore. Your records " +
                                "are associated with your signed-in account."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }

        // About MediTrack
        findViewById<android.view.View>(R.id.layoutAbout)
            .setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("About MediTrack")
                    .setMessage(
                        "MediTrack is a medicine tracking application " +
                                "developed as a university project.\n\n" +
                                "Features include medicine management, " +
                                "dose history and reminder support.\n\n" +
                                "Version 1.0"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }

        // Logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout") { _, _ ->

                    firebaseHelper.logout()

                    Toast.makeText(
                        this,
                        "Logged out successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this,
                        LoginActivity::class.java
                    )

                    intent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    // Reminder Preferences
    private fun showReminderDialog() {

        val preferences = getSharedPreferences(
            "MediTrackSettings",
            MODE_PRIVATE
        )

        val isEnabled = preferences.getBoolean(
            "remindersEnabled",
            true
        )

        val options = arrayOf("Enabled", "Disabled")

        AlertDialog.Builder(this)
            .setTitle("Reminder Preferences")
            .setSingleChoiceItems(
                options,
                if (isEnabled) 0 else 1
            ) { dialog, position ->

                preferences.edit()
                    .putBoolean("remindersEnabled", position == 0)
                    .apply()

                Toast.makeText(
                    this,
                    if (position == 0)
                        "Reminder preference enabled"
                    else
                        "Reminder preference disabled",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Language Selection
    private fun showLanguageDialog(languageValue: TextView) {

        val preferences = getSharedPreferences(
            "MediTrackSettings",
            MODE_PRIVATE
        )

        val languages = arrayOf("English", "Hindi")

        val currentLanguage = preferences.getString(
            "language",
            "English"
        )

        val selectedIndex =
            if (currentLanguage == "Hindi") 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Choose Language")
            .setSingleChoiceItems(
                languages,
                selectedIndex
            ) { dialog, position ->

                val selectedLanguage = languages[position]

                preferences.edit()
                    .putString("language", selectedLanguage)
                    .apply()

                languageValue.text = selectedLanguage

                Toast.makeText(
                    this,
                    "$selectedLanguage selected",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
