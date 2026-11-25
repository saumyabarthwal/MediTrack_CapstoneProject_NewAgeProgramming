package com.example.meditrack.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var firebaseHelper: FirebaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseHelper = FirebaseHelper()
        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login)
        val email = findViewById<EditText>(R.id.etLoginEmail)
        val password = findViewById<EditText>(R.id.etLoginPassword)
        val login = findViewById<Button>(R.id.btnLogin)
        val toggle = findViewById<TextView>(R.id.tvTogglePassword)
        toggle.setOnClickListener {
            val visible = password.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            password.inputType = if (visible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            password.setSelection(password.text.length)
            toggle.setText(if (visible) R.string.show_password else R.string.hide_password)
        }
        login.setOnClickListener {
            val userEmail = email.text.toString().trim()
            val userPassword = password.text.toString()
            if (userEmail.isEmpty() || userPassword.isEmpty()) {
                Toast.makeText(this, R.string.enter_email_password, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
                Toast.makeText(this, R.string.enter_valid_email, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login.isEnabled = false
            firebaseHelper.loginUser(userEmail, userPassword) { success, message ->
                login.isEnabled = true
                Toast.makeText(this, authMessage(message, success), Toast.LENGTH_SHORT).show()
                if (success) {
                    requestNotificationPermission()
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            }
        }
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val field = EditText(this).apply {
                hint = getString(R.string.email_hint)
                setText(email.text)
            }
            field.hint = getString(R.string.email_hint)
            AlertDialog.Builder(this).setTitle(R.string.reset_password).setView(field)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.send) { _, _ ->
                    val resetEmail = field.text.toString().trim()
                    if (resetEmail.isEmpty()) {
                        Toast.makeText(this, R.string.enter_email, Toast.LENGTH_SHORT).show()
                    } else {
                        firebaseHelper.sendPasswordReset(resetEmail) { resetSuccess, _ ->
                            Toast.makeText(
                                this,
                                if (resetSuccess) R.string.password_reset_success else R.string.error_reset,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.show()
        }
        findViewById<TextView>(R.id.tvGoToRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun authMessage(message: String, success: Boolean): String {
        if (success) return getString(R.string.login_success)
        return when {
            message.contains("No account found", true) -> getString(R.string.error_user_not_found)
            message.contains("incorrect", true) -> getString(R.string.error_invalid_password)
            message.contains("valid email", true) -> getString(R.string.enter_valid_email)
            else -> getString(R.string.error_signin)
        }
    }
}
