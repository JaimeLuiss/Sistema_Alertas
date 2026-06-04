package com.example.myapplication.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.ui.main.MainActivity
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.Validator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    // Referencias del layout activity_login.xml
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar SessionManager
        sessionManager = SessionManager(this)

        // Si ya hay una sesión activa, ir directamente al MainActivity
        if (sessionManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)

        // Vincular vistas con los IDs definidos en activity_login.xml
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        // Botón Iniciar Sesión
        btnLogin.setOnClickListener {
            attemptLogin()
        }

        // Texto "¿Olvidaste tu contraseña?"
        tvForgotPassword.setOnClickListener {
            Toast.makeText(
                this,
                "Funcionalidad disponible próximamente",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()

        // Limpiar errores previos
        tilEmail.error = null
        tilPassword.error = null

        // Validar correo
        if (!Validator.isValidEmail(email)) {
            tilEmail.error = "Ingrese un correo válido"
            etEmail.requestFocus()
            return
        }

        // Validar contraseña
        if (!Validator.isValidPassword(password)) {
            tilPassword.error = "La contraseña debe tener al menos 6 caracteres"
            etPassword.requestFocus()
            return
        }

        // Mostrar estado de carga
        showLoading(true)

        // Simular autenticación (1.5 segundos)
        btnLogin.postDelayed({

            // Credenciales de prueba
            val validEmail = "estudiante@unab.edu.co"
            val validPassword = "123456"

            if (email.equals(validEmail, ignoreCase = true) &&
                password == validPassword
            ) {
                // Guardar sesión
                sessionManager.saveLogin(email)

                Toast.makeText(
                    this,
                    "Bienvenido a Alertas Académicas",
                    Toast.LENGTH_SHORT
                ).show()

                // Ir al MainActivity
                navigateToMain()
            } else {
                // Restaurar UI
                showLoading(false)

                Toast.makeText(
                    this,
                    "Credenciales incorrectas",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, 1500)
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        // Habilitar o deshabilitar el botón
        btnLogin.isEnabled = !isLoading

        if (isLoading) {
            btnLogin.text = "Validando..."
            btnLogin.alpha = 0.7f
        } else {
            btnLogin.text = "Iniciar Sesión"
            btnLogin.alpha = 1.0f
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}