package com.example.meditrack

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MediTrackApplication : Application() {
    companion object {
        lateinit var instance: MediTrackApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val preferences = getSharedPreferences("MediTrackSettings", MODE_PRIVATE)
        val language = preferences.getString("language", "English")
            ?.let { if (it == "Hindi") "hi" else "en" } ?: "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        AppCompatDelegate.setDefaultNightMode(
            when (preferences.getString("theme", "System Default")) {
                "Light Mode" -> AppCompatDelegate.MODE_NIGHT_NO
                "Dark Mode" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
