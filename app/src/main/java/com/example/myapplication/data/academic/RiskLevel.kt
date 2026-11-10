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
    );

    companion object {
        /**
         * Parsea valores de riesgo provenientes de Firestore (LOW, MEDIUM, HIGH)
         * o de la API externa (Bajo, Medio, Alto). Retorna null si el valor es nulo,
         * en blanco o no reconocido (ej: "URGENTE").
         */
        fun fromStringOrNull(value: String?): RiskLevel? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().uppercase()) {
                "HIGH", "ALTO" -> HIGH
                "MEDIUM", "MEDIO" -> MEDIUM
                "LOW", "BAJO" -> LOW
                else -> null
            }
        }

        /**
         * Parsea de manera tolerante valores de riesgo.
         * Mantiene compatibilidad retornando LOW si el valor no es reconocido.
         */
        fun fromString(value: String?): RiskLevel {
            return fromStringOrNull(value) ?: LOW
        }
    }
}
