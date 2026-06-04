package com.example.myapplication.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.ui.login.LoginActivity
import com.example.myapplication.utils.SessionManager
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar SessionManager
        sessionManager = SessionManager(this)

        // Referencias del layout
        tvWelcome = findViewById(R.id.tvWelcome)
        btnLogout = findViewById(R.id.btnLogout)

        // Obtener correo almacenado
        val email = sessionManager.getUserEmail()

        // Extraer nombre del correo
        // estudiante@unab.edu.co -> Estudiante
        val userName = email
            ?.substringBefore("@")
            ?.replaceFirstChar { it.uppercase() }
            ?: "Estudiante"

        // Mostrar saludo personalizado
        tvWelcome.text = "Bienvenido, $userName"

        // Cerrar sesión
        btnLogout.setOnClickListener {
            sessionManager.logout()

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}