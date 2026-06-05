package com.example.myapplication.data.academic

enum class RiskLevel(
    val label: String,
    val summary: String,
    val color: String,
    val priority: Int
) {
    LOW(
        label = "Bajo",
        summary = "Rendimiento academico estable",
        color = "#2E7D32",
        priority = 0
    ),
    MEDIUM(
        label = "Medio",
        summary = "Seguimiento academico recomendado",
        color = "#EF6C00",
        priority = 1
    ),
    HIGH(
        label = "Alto",
        summary = "Alerta temprana activa",
        color = "#D32F2F",
        priority = 2
    )
}
