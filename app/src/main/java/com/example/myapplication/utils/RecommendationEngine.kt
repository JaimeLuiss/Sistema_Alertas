package com.example.myapplication.utils

import com.example.myapplication.data.academic.CourseStatus

object RecommendationEngine {

    fun generateRecommendation(course: CourseStatus): String {
        val prompt = """
            Analiza la siguiente información de una materia y genera una recomendación breve, personalizada y accionable.
            
            Datos:
            - Materia: ${course.name}
            - Nota actual: ${course.grade}
            - Asistencia: ${course.attendance}%
            - Nivel de riesgo: ${course.riskLevel.label}
            - Créditos: ${course.credits}
            - Tendencia: ${course.trend}
            
            Reglas:
            1. Explica brevemente por qué existe el riesgo.
            2. Indica máximo 3 acciones concretas para mejorar.
            3. Usa lenguaje motivador y profesional.
            4. No excedas 80 palabras.
            5. Si el riesgo es bajo, felicita al estudiante y sugiere mantener hábitos.
            6. Si el riesgo es medio, propone acciones preventivas.
            7. Si el riesgo es alto o crítico, enfatiza acciones urgentes.
            8. Devuelve únicamente el texto de la recomendación.
        """.trimIndent()

        // Simulación de respuesta de IA basada en las reglas
        return when (course.riskLevel.label.uppercase()) {
            "BAJO" -> "¡Excelente desempeño en ${course.name}! Tu nota de ${course.grade} y asistencia del ${course.attendance}% reflejan un compromiso ejemplar. Te sugiero mantener tus hábitos de estudio actuales y participar activamente en clase para asegurar este resultado."
            
            "MEDIO" -> "Tu rendimiento en ${course.name} muestra un riesgo medio con nota de ${course.grade}. Es vital mejorar la asistencia y repasar temas clave. Acción: 1. Revisa los talleres pasados. 2. Aclara dudas con el docente. 3. Dedica 2 horas extra semanales a esta materia."
            
            else -> "La asistencia del ${course.attendance}% y la nota de ${course.grade} en ${course.name} indican un riesgo crítico. Es urgente actuar: 1. Asistir a tutorías de apoyo de inmediato. 2. Cumplir con todas las entregas restantes. 3. Reforzar temas con mayor dificultad. ¡Aún puedes recuperar la materia!"
        }
    }
}
