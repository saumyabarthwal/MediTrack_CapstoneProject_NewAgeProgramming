package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.notifications.EngagementScheduler
import com.example.meditrack.notifications.NotificationHelper
import com.example.meditrack.notifications.ReminderScheduler
import androidx.activity.result.contract.ActivityResultContracts

class ProfileActivity : AppCompatActivity() {
    private lateinit var helper: FirebaseHelper
    private val preferences by lazy { getSharedPreferences("MediTrackSettings", MODE_PRIVATE) }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notifications are disabled by the system permission.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        helper = FirebaseHelper()
        val nameText = findViewById<TextView>(R.id.tvProfileName)
        val emailText = findViewById<TextView>(R.id.tvProfileEmail)
        val avatar = findViewById<TextView>(R.id.tvAvatar)
        val languageValue = findViewById<TextView>(R.id.tvLanguageValue)
        val themeValue = findViewById<TextView>(R.id.tvThemeValue)

        helper.getUserName { name ->
            val displayName = if (name == "User") getString(R.string.generic_user) else name
            nameText.text = displayName
            avatar.text = displayName.firstOrNull()?.uppercase() ?: "M"
        }
        emailText.text = helper.getUserEmail()
        helper.getMedicines { medicines, _ ->
            findViewById<TextView>(R.id.tvMedicineCount).text = medicines.size.toString()
        }
        languageValue.text = getString(
            if (preferences.getString("language", "English") == "Hindi") R.string.hindi else R.string.english
        )
        themeValue.text = getString(
            when (preferences.getString("theme", "System Default")) {
                "Light Mode" -> R.string.light_mode
                "Dark Mode" -> R.string.dark_mode
                else -> R.string.system_default
            }
        )

        findViewById<android.view.View>(R.id.layoutHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.layoutReminders).setOnClickListener {
            showReminderDialog()
        }
        findViewById<android.view.View>(R.id.layoutLanguage).setOnClickListener {
            showLanguageDialog()
        }
        findViewById<android.view.View>(R.id.layoutTheme).setOnClickListener {
            showThemeDialog()
        }
        findViewById<android.view.View>(R.id.layoutEditProfile).setOnClickListener {
            showEditProfileDialog(nameText)
        }
        findViewById<android.view.View>(R.id.layoutPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        findViewById<android.view.View>(R.id.layoutAbout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about_meditrack)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.logout) { _, _ ->
                    helper.logout()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    private fun showEditProfileDialog(nameText: TextView) {
        val input = EditText(this).apply {
            setText(nameText.text)
            hint = getString(R.string.full_name)
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_profile)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_changes) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, R.string.edit_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    helper.updateUserName(newName) { success ->
                        if (success) {
                            nameText.text = newName
                            findViewById<TextView>(R.id.tvAvatar).text =
                                newName.firstOrNull()?.uppercase() ?: "M"
                        }
                        Toast.makeText(
                            this,
                            if (success) getString(R.string.save_changes) else getString(R.string.error_generic),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showReminderDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        val scrollView = ScrollView(this).apply { addView(content) }
        fun addSwitch(label: String, key: String, default: Boolean): SwitchCompat {
            val control = SwitchCompat(this).apply {
                text = label
                isChecked = preferences.getBoolean(key, default)
                setPadding(0, 10, 0, 10)
            }
            content.addView(control, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return control
        }
        fun addLabel(label: String) {
            content.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setPadding(0, 12, 0, 4)
            })
        }
        fun addTimePicker(key: String, defaultValue: String): TimePicker {
            val value = preferences.getString(key, defaultValue) ?: defaultValue
            val parts = value.split(":")
            return TimePicker(this).apply {
                setIs24HourView(true)
                hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
                minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                content.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
        }
        val reminders = addSwitch("Medicine reminders", "remindersEnabled", true)
        val lowStock = addSwitch("Low-stock alerts", "lowStockEnabled", true)
        val engagement = addSwitch("Engagement check-ins", "engagementEnabled", false)
        val generalNotifications = addSwitch(
            "General app notifications", "generalNotificationsEnabled", true
        )
        val quietHoursEnabled = addSwitch("Respect quiet hours", "quietHoursEnabled", true)

        addLabel("Engagement frequency")
        val frequency = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ProfileActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Daily", "Weekly")
            )
            setSelection(
                if (preferences.getString("engagementFrequency", "daily") == "weekly") 1 else 0
            )
        }
        content.addView(frequency)
        addLabel("Engagement time")
        val engagementTime = addTimePicker("engagementTime", "09:00")
        addLabel("Quiet hours start")
        val quietStart = addTimePicker("quietHoursStart", "22:00")
        addLabel("Quiet hours end")
        val quietEnd = addTimePicker("quietHoursEnd", "08:00")

        AlertDialog.Builder(this)
            .setTitle(R.string.reminder_preferences)
            .setView(scrollView)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_changes) { dialog, _ ->
                val oldReminders = preferences.getBoolean("remindersEnabled", true)
                val oldEngagement = preferences.getBoolean("engagementEnabled", false)
                val remindersEnabled = reminders.isChecked
                val lowStockEnabled = lowStock.isChecked
                val engagementEnabled = engagement.isChecked
                preferences.edit()
                    .putBoolean("remindersEnabled", remindersEnabled)
                    .putBoolean("lowStockEnabled", lowStockEnabled)
                    .putBoolean("engagementEnabled", engagementEnabled)
                    .putBoolean("generalNotificationsEnabled", generalNotifications.isChecked)
                    .putBoolean("quietHoursEnabled", quietHoursEnabled.isChecked)
                    .putString("engagementFrequency", if (frequency.selectedItemPosition == 1) "weekly" else "daily")
                    .putString("engagementTime", formatTime(engagementTime))
                    .putString("quietHoursStart", formatTime(quietStart))
                    .putString("quietHoursEnd", formatTime(quietEnd))
                    .apply()

                if (!remindersEnabled) {
                    ReminderScheduler.cancelAll(this)
                } else if (!oldReminders) {
                    ReminderScheduler.cancelAll(this)
                    helper.getMedicines { medicines, _ ->
                        medicines.forEach { ReminderScheduler.scheduleMedicine(this, it) }
                    }
                }
                if (!lowStockEnabled) NotificationHelper(this).cancelLowStockNotifications()
                EngagementScheduler.update(
                    this, engagementEnabled,
                    newlyEnabled = engagementEnabled && !oldEngagement
                )
                requestNotificationPermissionIfNeeded(
                    remindersEnabled || lowStockEnabled || engagementEnabled
                )
                Toast.makeText(this, R.string.save_changes, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun formatTime(picker: TimePicker): String =
        "%02d:%02d".format(picker.hour, picker.minute)

    private fun requestNotificationPermissionIfNeeded(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showLanguageDialog() {
        val options = arrayOf(getString(R.string.english), getString(R.string.hindi))
        val selected = if (preferences.getString("language", "English") == "Hindi") 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(options, selected) { dialog, position ->
                val language = if (position == 1) "Hindi" else "English"
                preferences.edit().putString("language", language).apply()
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(if (position == 1) "hi" else "en")
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.light_mode),
            getString(R.string.dark_mode),
            getString(R.string.system_default)
        )
        val selected = when (preferences.getString("theme", "System Default")) {
            "Light Mode" -> 0
            "Dark Mode" -> 1
            else -> 2
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_theme)
            .setSingleChoiceItems(options, selected) { dialog, position ->
                val value = when (position) {
                    0 -> "Light Mode"
                    1 -> "Dark Mode"
                    else -> "System Default"
                }
                preferences.edit().putString("theme", value).apply()
                AppCompatDelegate.setDefaultNightMode(
                    when (position) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
