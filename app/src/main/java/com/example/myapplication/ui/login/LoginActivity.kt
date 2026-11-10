package com.example.myapplication.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sessionManager: SessionManager

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        if (auth.currentUser != null && sessionManager.getStudentId() != null) {
            Log.d("LoginActivity", "[DIAGNOSTIC] Sesión previa activa. User email: '${auth.currentUser?.email}', studentId: '${sessionManager.getStudentId()}'")
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_login)

        bindViews()
        configureActions()
    }

    private fun bindViews() {
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun configureActions() {
        btnLogin.setOnClickListener {
            attemptLogin()
        }

        tvForgotPassword.setOnClickListener {
            openUrl("https://correo.unab.edu.co/recuperarClave.jsp")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun attemptLogin() {
        val userInput = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()
        val email = if (userInput.contains("@")) {
            userInput
        } else {
            "$userInput@unab.edu.co"
        }
        tilEmail.error = null
        tilPassword.error = null
        if (userInput.isEmpty()) {
            tilEmail.error = "Ingrese su ID de usuario"
            return
        }
        if (!Validator.isValidEmail(email)) {
            tilEmail.error = "ID de usuario no válido"
            return
        }
        if (password.isEmpty()) {
            tilPassword.error = "Ingrese su contraseña"
            return
        }
        showLoading(true)
        Log.d("LoginActivity", "[DIAGNOSTIC] Intentando signInWithEmailAndPassword con email: '$email'")
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                Log.d("LoginActivity", "[DIAGNOSTIC] Auth exitoso para: '$email'")
                fetchStudentData(email)
            }
            .addOnFailureListener { e ->
                Log.w("LoginActivity", "[DIAGNOSTIC] Auth falló para '$email': ${e.message}")
                when (e) {
                    is FirebaseAuthInvalidUserException,
                    is FirebaseAuthInvalidCredentialsException -> {
                        // El usuario no pudo iniciar sesión.
                        // Revisamos si existe como estudiante en Firestore.
                        autoRegisterFromFirestore(email, password)
                    }
                    else -> {
                        showLoading(false)
                        Toast.makeText(
                            this,
                            "Error de autenticación: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    private fun autoRegisterFromFirestore(email: String, password: String) {
        Log.d("LoginActivity", "[DIAGNOSTIC] Intentando autoRegisterFromFirestore para email: '$email'")
        firestore.collection("students")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    showLoading(false)
                    Log.e("LoginActivity", "[DIAGNOSTIC] ✗ Usuario no encontrado en 'students' con email: '$email'")
                    Toast.makeText(this, "Este usuario no está registrado en la base de datos universitaria", Toast.LENGTH_LONG).show()
                } else {
                    val studentDoc = snapshot.documents[0]
                    val studentId = studentDoc.id
                    Log.d("LoginActivity", "[DIAGNOSTIC] ✓ Usuario encontrado en 'students'. Doc ID: '$studentId'. Creando cuenta Auth...")
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            sessionManager.saveLogin(email, studentId)
                            Log.d("LoginActivity", "[DIAGNOSTIC] ✓ Cuenta Auth creada. SessionManager guardó email: '$email', studentId: '$studentId'")
                            Toast.makeText(this, "Cuenta activada correctamente", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Log.e("LoginActivity", "[DIAGNOSTIC] ✗ Fallo al crear cuenta Auth: ${e.message}", e)
                            Toast.makeText(this, "No se pudo activar la cuenta: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("LoginActivity", "[DIAGNOSTIC] ✗ Error Firestore en autoRegisterFromFirestore: ${e.message}", e)
                Toast.makeText(this, "Error de conexión con la base de datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchStudentData(email: String) {
        val currentUser = auth.currentUser
        Log.d("LoginActivity", "[DIAGNOSTIC] fetchStudentData -> Auth uid: '${currentUser?.uid}', auth email: '${currentUser?.email}'")
        Log.d("LoginActivity", "[DIAGNOSTIC] Buscando en Firestore 'students' con email: '$email'")

        firestore.collection("students")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                showLoading(false)

                if (snapshot.isEmpty) {
                    Log.e("LoginActivity", "[DIAGNOSTIC] ✗ NO se encontró estudiante en 'students' con email: '$email'")
                    auth.signOut()
                    Toast.makeText(
                        this,
                        "Perfil no encontrado en Firestore",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val studentDoc = snapshot.documents[0]
                    val studentId = studentDoc.id
                    Log.d("LoginActivity", "[DIAGNOSTIC] ✓ Estudiante encontrado! Doc ID (studentId): '$studentId', Email doc: '${studentDoc.getString("email")}'")
                    sessionManager.saveLogin(email, studentId)
                    navigateToMain()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                auth.signOut()
                Log.e("LoginActivity", "[DIAGNOSTIC] ✗ Error Firestore en fetchStudentData: ${e.message}", e)

                Toast.makeText(
                    this,
                    "Error Firestore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        btnLogin.text = if (isLoading) "Validando..." else "Iniciar Sesión"
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
