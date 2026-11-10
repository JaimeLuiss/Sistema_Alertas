package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "unab_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_STUDENT_ID = "student_id"
    }

    // Guardar sesión ampliada con StudentID
    fun saveLogin(email: String, studentId: String) {
        preferences.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_STUDENT_ID, studentId)
            .apply()
    }

    // Verificar si hay sesión activa
    fun isLoggedIn(): Boolean {
        return preferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Obtener correo guardado
    fun getUserEmail(): String? {
        return preferences.getString(KEY_USER_EMAIL, null)
    }

    // Obtener ID de estudiante guardado (U00000001, etc)
    fun getStudentId(): String? {
        return preferences.getString(KEY_STUDENT_ID, null)
    }

    // Cerrar sesión
    fun logout() {
        preferences.edit().clear().apply()
    }
}