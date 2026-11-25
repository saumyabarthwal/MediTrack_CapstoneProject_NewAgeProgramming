package com.example.meditrack

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.activities.DashboardActivity
import com.example.meditrack.activities.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val signedIn = FirebaseAuth.getInstance().currentUser != null
        startActivity(Intent(this, if (signedIn) DashboardActivity::class.java else LoginActivity::class.java))
        finish()
    }
}
