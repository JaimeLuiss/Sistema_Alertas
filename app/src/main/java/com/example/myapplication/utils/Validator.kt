package com.example.myapplication.utils

import android.util.Patterns

object Validator {

    /**
     * Valida que el correo no esté vacío y tenga formato correcto.
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Valida que la contraseña tenga al menos 6 caracteres.
     */
    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    /**
     * Valida opcionalmente que sea un correo institucional UNAB.
     */
    fun isInstitutionalEmail(email: String): Boolean {
        return email.endsWith("@unab.edu.co", ignoreCase = true)
    }
}