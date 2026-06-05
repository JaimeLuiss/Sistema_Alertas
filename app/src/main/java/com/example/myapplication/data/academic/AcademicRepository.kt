package com.example.myapplication.data.academic

import com.example.myapplication.utils.RecommendationEngine

class AcademicRepository {

    fun getDashboardFor(email: String): StudentDashboard {
        val courses = listOf(
            CourseStatus(
                name = "Cálculo Diferencial",
                grade = 3.2,
                attendance = 85,
                progress = 70,
                riskLevel = RiskLevel.MEDIUM,
                recommendation = "",
                credits = 4,
                trend = "Estable"
            ),
            CourseStatus(
                name = "Expresión",
                grade = 4.5,
                attendance = 95,
                progress = 80,
                riskLevel = RiskLevel.LOW,
                recommendation = "",
                credits = 4,
                trend = "Ascendente"
            ),
            CourseStatus(
                name = "Fundamentos de Programación",
                grade = 2.8,
                attendance = 60,
                progress = 65,
                riskLevel = RiskLevel.HIGH,
                recommendation = "",
                credits = 3,
                trend = "Descendente"
            ),
            CourseStatus(
                name = "Seminario de Ingeniería I",
                grade = 4.8,
                attendance = 100,
                progress = 90,
                riskLevel = RiskLevel.LOW,
                recommendation = "",
                credits = 2,
                trend = "Estable"
            ),
            CourseStatus(
                name = "Álgebra Lineal",
                grade = 3.5,
                attendance = 80,
                progress = 75,
                riskLevel = RiskLevel.MEDIUM,
                recommendation = "",
                credits = 3,
                trend = "Estable"
            )
        )

        // Generar recomendaciones dinámicas usando el motor (Simulación IA)
        val coursesWithAi = courses.map { it.copy(recommendation = RecommendationEngine.generateRecommendation(it)) }

        return StudentDashboard(
            studentEmail = email,
            period = "Primer Semestre",
            courses = coursesWithAi
        )
    }
}
