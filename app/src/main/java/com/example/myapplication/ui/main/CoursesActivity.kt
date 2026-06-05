package com.example.myapplication.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.academic.AcademicRepository
import com.example.myapplication.utils.SessionManager

class CoursesActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val academicRepository = AcademicRepository()
    private lateinit var rvCourses: RecyclerView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_courses)

        sessionManager = SessionManager(this)
        
        rvCourses = findViewById(R.id.rvCourses)
        btnBack = findViewById(R.id.btnBack)
        val header = findViewById<View>(R.id.semesterHeaderFixed)

        btnBack.setOnClickListener {
            finish()
        }

        renderCourses()

        // Animación de entrada para el header
        header.alpha = 0f
        header.translationY = -20f
        header.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(200)
            .start()
    }

    private fun renderCourses() {
        val email = sessionManager.getUserEmail() ?: "estudiante@unab.edu.co"
        val dashboard = academicRepository.getDashboardFor(email)

        val adapter = CourseAdapter(
            courses = dashboard.courses,
            onGradesClick = { course ->
                showAlertDialog("Detalle de Notas: ${course.name}", 
                    "Nota actual: ${course.grade}\nAsistencia: ${course.attendance}%\n\nEl detalle por cortes estará disponible pronto.")
            },
            onWellnessClick = { 
                openUrl("https://bienestar.unab.edu.co/servicios/pregrado-acompanamiento/")
            }
        )
        
        rvCourses.layoutManager = LinearLayoutManager(this)
        rvCourses.adapter = adapter
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAlertDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
