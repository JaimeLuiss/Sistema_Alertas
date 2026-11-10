
package com.example.myapplication.data.academic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Datos que la API espera recibir en POST /predict.
 */
data class RiskPredictionRequest(
    val sh1: Int,
    val sf1: Int,
    val creditosAsignatura: Int,
    val asistenciaActual: Int,
    val inasistenciaActual: Int,
    val asignatura: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("SH_1", sh1)
            put("SF_1", sf1)
            put("creditos_asignatura", creditosAsignatura)
            put("asistencia_actual", asistenciaActual)
            put("inasistencia_actual", inasistenciaActual)
            put("asignatura", asignatura)
        }
    }

    companion object {
        /**
         * Valores de prueba tomados del ejemplo de Swagger.
         * No representan datos reales de un estudiante.
         */
        fun swaggerTest(): RiskPredictionRequest {
            return RiskPredictionRequest(
                sh1 = 5,
                sf1 = 5,
                creditosAsignatura = 1,
                asistenciaActual = 0,
                inasistenciaActual = 0,
                asignatura = "ALGEBRA LINEAL"
            )
        }
    }
}

data class RiskProbabilities(
    val alto: Double,
    val bajo: Double,
    val medio: Double
)

data class RiskPredictionResponse(
    val nivelRiesgo: String,
    val probabilidades: RiskProbabilities
) {
    /**
     * Convierte el resultado de la API
     * a la convención actual de Firestore.
     */
    fun toFirestoreRisk(): String {
        return when (nivelRiesgo.trim().lowercase()) {
            "bajo" -> "LOW"
            "medio" -> "MEDIUM"
            "alto" -> "HIGH"
            else -> throw IOException(
                "Nivel de riesgo desconocido: $nivelRiesgo"
            )
        }
    }
}

class RiskPredictionApi {

    companion object {
        private const val BASE_URL =
            "https://api-riesgo-academico.onrender.com"

        private const val CONNECT_TIMEOUT = 20000
        private const val READ_TIMEOUT = 60000
    }

    suspend fun predict(
        request: RiskPredictionRequest
    ): RiskPredictionResponse = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {
            val url = URL("$BASE_URL/predict")

            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            // Enviar JSON a Render.
            val jsonBody = request.toJson().toString()

            connection.outputStream.use { output ->
                output.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            // Obtener respuesta HTTP.
            val responseCode = connection.responseCode

            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = responseStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: ""

            if (responseCode !in 200..299) {
                throw IOException(
                    "Error HTTP $responseCode: ${responseBody.take(800)}"
                )
            }

            if (responseBody.isBlank()) {
                throw IOException("La API devolvió una respuesta vacía.")
            }

            // Interpretar respuesta JSON.
            val jsonResponse = JSONObject(responseBody)

            val probabilities =
                jsonResponse.optJSONObject("probabilidades")
                    ?: throw IOException(
                        "La respuesta no contiene probabilidades."
                    )

            val nivelRiesgo =
                jsonResponse.optString("nivel_riesgo")

            if (nivelRiesgo.isBlank()) {
                throw IOException(
                    "La respuesta no contiene nivel_riesgo."
                )
            }

            RiskPredictionResponse(
                nivelRiesgo = nivelRiesgo,
                probabilidades = RiskProbabilities(
                    alto = probabilities.getDouble("Alto"),
                    bajo = probabilities.getDouble("Bajo"),
                    medio = probabilities.getDouble("Medio")
                )
            )

        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(
                "No se pudo procesar la respuesta de la API: ${e.message}",
                e
            )
        } finally {
            connection?.disconnect()
        }
    }
}
