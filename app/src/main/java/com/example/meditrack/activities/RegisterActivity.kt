
package com.example.meditrack.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.firebase.FirebaseHelper
import com.example.meditrack.MainActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var firebaseHelper: FirebaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        firebaseHelper = FirebaseHelper()

        val name = findViewById<EditText>(R.id.etRegisterName)
        val email = findViewById<EditText>(R.id.etRegisterEmail)
        val password = findViewById<EditText>(R.id.etRegisterPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)

        val registerButton = findViewById<Button>(R.id.btnRegister)
        val loginText = findViewById<TextView>(R.id.tvGoToLogin)

        registerButton.setOnClickListener {
            val userName = name.text.toString().trim()
            val userEmail = email.text.toString().trim()
            val userPassword = password.text.toString()
            val confirm = confirmPassword.text.toString()

            if (userName.isEmpty() || userEmail.isEmpty() ||
                userPassword.isEmpty() || confirm.isEmpty()) {

                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()

            } else if (userPassword.length < 6) {

                Toast.makeText(this, "Password must contain at least 6 characters", Toast.LENGTH_SHORT).show()

            } else if (userPassword != confirm) {

                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()

            } else {
                registerButton.isEnabled = false

                firebaseHelper.registerUser(
                    userName,
                    userEmail,
                    userPassword
                ) { success, message ->

                    registerButton.isEnabled = true
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                    if (success) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }

        loginText.setOnClickListener {
            finish()
        }
    }
}
