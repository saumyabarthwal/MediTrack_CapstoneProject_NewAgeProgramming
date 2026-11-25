package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper

class RegisterActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        val helper = FirebaseHelper()
        val name = findViewById<EditText>(R.id.etRegisterName)
        val email = findViewById<EditText>(R.id.etRegisterEmail)
        val password = findViewById<EditText>(R.id.etRegisterPassword)
        val confirm = findViewById<EditText>(R.id.etConfirmPassword)
        val button = findViewById<Button>(R.id.btnRegister)
        val toggle = findViewById<TextView>(R.id.tvTogglePassword)
        toggle.setOnClickListener {
            val isHidden = password.inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD
            val type = if (isHidden)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            password.inputType = type
            confirm.inputType = type
            password.setSelection(password.text.length)
            confirm.setSelection(confirm.text.length)
            toggle.setText(if (isHidden) R.string.hide_passwords else R.string.show_passwords)
        }
        button.setOnClickListener {
            val n = name.text.toString().trim()
            val e = email.text.toString().trim()
            val p = password.text.toString()
            val c = confirm.text.toString()
            if (n.isEmpty() || e.isEmpty() || p.isEmpty() || c.isEmpty()) {
                Toast.makeText(this, R.string.please_fill_all, Toast.LENGTH_SHORT).show()
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
                Toast.makeText(this, R.string.enter_valid_email, Toast.LENGTH_SHORT).show()
            } else if (p.length < 6) {
                Toast.makeText(this, R.string.password_min_length, Toast.LENGTH_SHORT).show()
            } else if (p != c) {
                Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            } else {
                button.isEnabled = false
                helper.registerUser(n, e, p) { success, message ->
                    button.isEnabled = true
                    val localized = if (success) getString(R.string.registration_success) else when {
                        message.contains("already exists", true) -> getString(R.string.error_email_collision)
                        message.contains("Password must", true) -> getString(R.string.error_password_weak)
                        message.contains("valid email", true) -> getString(R.string.enter_valid_email)
                        message.contains("profile", true) -> getString(R.string.error_profile_save)
                        else -> getString(R.string.error_registration)
                    }
                    Toast.makeText(this, localized, Toast.LENGTH_SHORT).show()
                    if (success) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finishAffinity()
                    }
                }
            }
        }
        findViewById<TextView>(R.id.tvGoToLogin).setOnClickListener { finish() }
    }
}
