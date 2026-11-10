package com.example.myapplication.ui.main

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.academic.AcademicRepository
import com.example.myapplication.data.academic.RiskLevel
import com.example.myapplication.data.academic.StudentDashboard
import com.example.myapplication.ui.login.LoginActivity
import com.example.myapplication.utils.Animations
import com.example.myapplication.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sessionManager: SessionManager
    private val academicRepository = AcademicRepository()
    private var cachedDashboard: StudentDashboard? = null
    
    // UI Elements
    private lateinit var tvWelcome: TextView
    private lateinit var tvAverage: TextView
    private lateinit var tvAverageStatus: TextView
    private lateinit var tvRiskWarning: TextView
    private lateinit var tvRiskDetail: TextView
    private lateinit var btnViewPensum: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private lateinit var mainScrollView: ScrollView
    private lateinit var cardDashboard: MaterialCardView
    private lateinit var cardAverage: MaterialCardView
    private lateinit var cardAiAssistant: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        sessionManager = SessionManager(this)

        bindViews()
        loadDashboardData()
        configureActions()
        
        // Aplicar animaciones de entrada
        Animations.slideUp(cardDashboard, 100)
    }

    private fun bindViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvAverage = findViewById(R.id.tvAverage)
        tvAverageStatus = findViewById(R.id.tvAverageStatus)
        tvRiskWarning = findViewById(R.id.tvRiskWarning)
        tvRiskDetail = findViewById(R.id.tvRiskDetail)
        btnViewPensum = findViewById(R.id.btnViewPensum)
        btnLogout = findViewById(R.id.btnLogout)
        mainScrollView = findViewById(R.id.mainScrollView)
        cardDashboard = findViewById(R.id.cardDashboard)
        cardAverage = findViewById(R.id.cardAverage)
        cardAiAssistant = findViewById(R.id.cardAiAssistant)
    }

    private fun loadDashboardData() {
        val email = sessionManager.getUserEmail() ?: "estudiante@unab.edu.co"
        val studentId = sessionManager.getStudentId() ?: ""
        
        val userName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        tvWelcome.text = "Bienvenido, $userName"

        lifecycleScope.launch {
            val dashboard = academicRepository.getDashboardFor(email, studentId)
            cachedDashboard = dashboard
            
            val average = dashboard.averageGrade()
            val riskCourses = dashboard.courses.filter { it.riskLevel != RiskLevel.LOW }
            val overallRisk = dashboard.overallRisk()
            val riskColor = Color.parseColor(overallRisk.color)

            tvAverage.text = String.format(Locale.US, "%.1f", average)
            tvAverage.setTextColor(riskColor)
            tvAverageStatus.text = overallRisk.summary
            tvAverageStatus.setTextColor(riskColor)
            tvRiskWarning.text = "Materias en Riesgo: ${riskCourses.size}"
            tvRiskWarning.setTextColor(if (riskCourses.isEmpty()) Color.parseColor("#2E7D32") else riskColor)
            
            tvRiskDetail.text = if (riskCourses.isEmpty()) {
                "¡Excelente trabajo! No tienes alertas."
            } else {
                "Tienes ${riskCourses.size} materias que requieren atención."
            }
            cardAiAssistant.visibility = View.VISIBLE
        }
    }

    private fun configureActions() {
        cardAverage.setOnClickListener {
            val intent = Intent(this, CoursesActivity::class.java)
            startActivity(intent)
        }

        btnViewPensum.setOnClickListener {
            openUrl("https://miportalu.unab.edu.co/modulos/wwwunab/planestudios.php?programa=1791")
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            sessionManager.logout()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        
        findViewById<View>(R.id.topBanner).setOnClickListener {
            mainScrollView.smoothScrollTo(0, 0)
        }

        cardAiAssistant.setOnClickListener {
            showAiBottomSheet()
        }
    }

    private fun showAiBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_ai, null)

        val tvAiResponse = view.findViewById<TextView>(R.id.tvAiResponse)
        val cardAiResponse = view.findViewById<MaterialCardView>(R.id.cardAiResponse)
        
        val email = sessionManager.getUserEmail() ?: "estudiante@unab.edu.co"
        val studentId = sessionManager.getStudentId() ?: ""

        lifecycleScope.launch {
            val dashboard = cachedDashboard ?: academicRepository.getDashboardFor(email, studentId).also {
                cachedDashboard = it
            }
            val riskCourses = dashboard.courses.filter { it.riskLevel != RiskLevel.LOW }

            view.findViewById<MaterialButton>(R.id.btnQuestionRisk).setOnClickListener {
                val response = if (riskCourses.isEmpty()) {
                    "Actualmente no presentas riesgos académicos. Tu promedio de ${String.format(Locale.US, "%.1f", dashboard.averageGrade())} es sólido. ¡Sigue así!"
                } else {
                    "Estás en riesgo principalmente en ${riskCourses.first().name} debido a una nota de ${riskCourses.first().grade}."
                }
                showAiResponse(tvAiResponse, cardAiResponse, response)
            }

            view.findViewById<MaterialButton>(R.id.btnQuestionImprove).setOnClickListener {
                showAiResponse(tvAiResponse, cardAiResponse, "Para mejorar tu promedio de ${String.format(Locale.US, "%.1f", dashboard.averageGrade())}, te sugiero priorizar las materias con mayor carga de créditos.")
            }

            view.findViewById<MaterialButton>(R.id.btnQuestionAttention).setOnClickListener {
                val target = riskCourses.maxByOrNull { it.riskLevel.priority } ?: dashboard.courses.minByOrNull { it.grade }
                val targetName = target?.name ?: "N/A"
                showAiResponse(tvAiResponse, cardAiResponse, "La materia que requiere atención inmediata es $targetName.")
            }

            view.findViewById<MaterialButton>(R.id.btnQuestionMath).setOnClickListener {
                showAiResponse(tvAiResponse, cardAiResponse, "El cálculo de aprobación depende del peso de los cortes restantes en tu materia más crítica.")
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showAiResponse(tv: TextView, card: MaterialCardView, text: String) {
        card.visibility = View.VISIBLE
        tv.text = text
        card.alpha = 0f
        card.animate().alpha(1f).setDuration(500).start()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }
}
