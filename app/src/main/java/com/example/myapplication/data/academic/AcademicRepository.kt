package com.example.myapplication.data.academic

import android.util.Log
import com.example.myapplication.utils.RecommendationEngine
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.math.roundToInt

class AcademicRepository {

    private val db = FirebaseFirestore.getInstance()
    private val riskPredictionApi = RiskPredictionApi()

    companion object {
        private const val TAG = "AcademicRepository"


        const val DEFAULT_SH1 = 5
        const val DEFAULT_SF1 = 5
        const val DEFAULT_CREDITS = 3
        const val TOTAL_SEMESTER_CLASSES = 20
        const val MAX_CONCURRENT_PREDICTIONS = 3

        /**
         * Semáforo global compartido entre TODAS las instancias de AcademicRepository
         * y TODAS las llamadas concurrentes a getDashboardFor() dentro del proceso Android.
         * Garantiza que nunca existan más de 3 peticiones HTTP activas simultáneamente hacia Render.
         */
        private val globalSemaphore = Semaphore(MAX_CONCURRENT_PREDICTIONS)

        /**
         * CoroutineScope independiente de las Activities para ejecutar operaciones compartidas Single Flight.
         */
        private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Mutex exclusivamente para administrar de forma segura el mapa de solicitudes en curso.
         */
        private val singleFlightMutex = Mutex()

        /**
         * Registro compartido de las solicitudes de dashboard activas por clave de estudiante.
         */
        private val inFlightRequests = mutableMapOf<String, Deferred<StudentDashboard>>()
    }

    /**
     * Obtiene el dashboard del estudiante reutilizando una operación activa mediante Single Flight
     * si existen llamadas simultáneas para el mismo estudiante.
     */
    suspend fun getDashboardFor(email: String, studentId: String): StudentDashboard {
        val key = if (studentId.isNotBlank()) {
            "student:${studentId.trim()}"
        } else {
            "email:${email.trim().lowercase(Locale.ROOT)}"
        }

        while (true) {
            val (deferredToAwait, deferredToJoin) = singleFlightMutex.withLock {
                val existing = inFlightRequests[key]
                when {
                    existing == null || existing.isCompleted -> {
                        // 1. Si no existe o ya completó, crear nueva operación
                        val newDeferred = repositoryScope.async {
                            fetchDashboardInternal(email, studentId)
                        }
                        inFlightRequests[key] = newDeferred

                        newDeferred.invokeOnCompletion {
                            repositoryScope.launch {
                                singleFlightMutex.withLock {
                                    if (inFlightRequests[key] === newDeferred) {
                                        inFlightRequests.remove(key)
                                    }
                                }
                            }
                        }

                        Pair(newDeferred, null)
                    }
                    existing.isCancelled -> {
                        // 2. Si está cancelándose pero aún no ha completado (isCompleted == false)
                        Pair(null, existing)
                    }
                    else -> {
                        // 3. Tarea activa o completándose normalmente (!isCompleted && !isCancelled)
                        Pair(existing, null)
                    }
                }
            }

            if (deferredToJoin != null) {
                // Esperar cancelación fuera del Mutex para volver a comprobar el mapa
                deferredToJoin.join()
                continue
            }

            if (deferredToAwait != null) {
                // Esperar el resultado fuera del Mutex
                return deferredToAwait.await()
            }
        }
    }

    /**
     * Lógica interna de consulta a Firestore y Render con regla académica híbrida.
     */
    private suspend fun fetchDashboardInternal(email: String, studentId: String): StudentDashboard {
        Log.d(TAG, "[DIAGNOSTIC] === Inicio de fetchDashboardInternal ===")
        Log.d(TAG, "[DIAGNOSTIC] Parámetros recibidos -> email: '$email', studentId: '$studentId'")

        try {
            var studentDoc: DocumentSnapshot? = null

            // 1. Consultar documento del estudiante en students/{studentId}
            if (studentId.isNotBlank()) {
                val targetPath = "students/$studentId"
                Log.d(TAG, "[DIAGNOSTIC] 1. Intentando consultar Firestore por ID. Ruta objetivo: '$targetPath'")
                try {
                    val docById = db.collection("students").document(studentId).get().await()
                    if (docById.exists()) {
                        studentDoc = docById
                        Log.d(TAG, "[DIAGNOSTIC] ✓ Estudiante ENCONTRADO por ID en '$targetPath'. Doc ID: '${studentDoc.id}'")
                    } else {
                        Log.w(TAG, "[DIAGNOSTIC] ✗ Documento NO EXISTE en ruta: '$targetPath'")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[DIAGNOSTIC] ✗ Excepción al consultar '$targetPath': ${e.message}", e)
                }
            } else {
                Log.d(TAG, "[DIAGNOSTIC] studentId está vacío o en blanco, se omite búsqueda por ID.")
            }

            // Fallback si no se encontró por ID: buscar por correo electrónico
            if (studentDoc == null && email.isNotBlank()) {
                val normalizedEmail = email.trim().lowercase(Locale.ROOT)
                Log.d(TAG, "[DIAGNOSTIC] 2. Intentando búsqueda fallback por email en colección 'students'. Email exacto: '$email', Normalizado: '$normalizedEmail'")
                try {
                    var queryByEmail = db.collection("students")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .await()
                    studentDoc = queryByEmail.documents.firstOrNull()

                    if (studentDoc == null && email != normalizedEmail) {
                        Log.d(TAG, "[DIAGNOSTIC] Reintentando consulta con email normalizado en minúsculas: '$normalizedEmail'")
                        queryByEmail = db.collection("students")
                            .whereEqualTo("email", normalizedEmail)
                            .limit(1)
                            .get()
                            .await()
                        studentDoc = queryByEmail.documents.firstOrNull()
                    }

                    if (studentDoc != null) {
                        Log.d(TAG, "[DIAGNOSTIC] ✓ Estudiante ENCONTRADO por email. Doc ID: '${studentDoc.id}', Ruta: '${studentDoc.reference.path}'")
                    } else {
                        Log.w(TAG, "[DIAGNOSTIC] ✗ Búsqueda por email '$email' / '$normalizedEmail' no devolvió ningún documento en 'students'.")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[DIAGNOSTIC] ✗ Excepción al consultar por email en 'students': ${e.message}", e)
                }
            }

            if (studentDoc == null || !studentDoc.exists()) {
                Log.e(
                    TAG,
                    "[DIAGNOSTIC] MOTIVO DE FALLBACK A MOCK: No se encontró el documento del estudiante ni por ID ('$studentId') ni por email ('$email') en Firestore."
                )
                return getMockDashboard(email)
            }

            val docPath = studentDoc.reference.path
            val period = when (val rawPeriod = studentDoc.get("Semestre")) {
                is String -> rawPeriod
                is Number -> rawPeriod.toString()
                null -> "Primer Semestre"
                else -> {
                    Log.w(TAG, "Tipo inesperado en Semestre: ${rawPeriod::class.java.name}")
                    "Primer Semestre"
                }
            }
            val userEmail = studentDoc.getString("email") ?: email
            Log.d(TAG, "[DIAGNOSTIC] Documento de estudiante validado. Ruta: '$docPath', Semestre: '$period', email del doc: '$userEmail'")

            // 2. Consultar subcolección de Cursos: students/{studentId}/Cursos
            val cursosPath = "$docPath/Cursos"
            Log.d(TAG, "[DIAGNOSTIC] 3. Consultando subcolección 'Cursos' en ruta: '$cursosPath'")

            val cursosSnapshot = try {
                studentDoc.reference.collection("Cursos").get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[DIAGNOSTIC] ✗ Excepción al consultar subcolección '$cursosPath': ${e.message}", e)
                throw e
            }

            Log.d(TAG, "[DIAGNOSTIC] Documentos encontrados en '$cursosPath': ${cursosSnapshot.size()}")

            if (cursosSnapshot.isEmpty) {
                Log.w(
                    TAG,
                    "[DIAGNOSTIC] SUBCOLECCIÓN VACÍA: El estudiante en '$docPath' existe pero la subcolección 'Cursos' en '$cursosPath' no contiene documentos."
                )
                return StudentDashboard(
                    studentEmail = userEmail,
                    period = period,
                    courses = emptyList()
                )
            }

            // 3. Procesar cada materia de forma concurrente desde students/{studentId}/Cursos/{courseId}
            val realCourses = coroutineScope {
                cursosSnapshot.documents.map { doc ->
                    async {
                        val activeStudentId = studentDoc.id

                        val materiaName = parseStringField(
                            doc = doc,
                            primaryField = "Materia",
                            secondaryField = "name",
                            defaultValue = doc.id
                        )

                        // 1. Leer la nota real mediante parseGradeField()
                        val rawGradeValue = doc.get("nota") ?: doc.get("Nota") ?: doc.get("grade")
                        val isGradePresent = doc.contains("nota") || doc.contains("Nota") || doc.contains("grade")
                        
                        val grade = parseGradeField(doc = doc, materiaName = materiaName)

                        // Determinar si la nota leída fue válida / presente en Firestore
                        val realGradeOrNull: Double? = if (isGradePresent && rawGradeValue != null) {
                            when (rawGradeValue) {
                                is Number -> rawGradeValue.toDouble()
                                is String -> rawGradeValue.trim().replace(',', '.').toDoubleOrNull()
                                else -> null
                            }
                        } else {
                            null
                        }

                        val attendance = parseIntField(
                            doc = doc,
                            studentId = activeStudentId,
                            materiaName = materiaName,
                            primaryField = "asistencia",
                            defaultValue = 0
                        )

                        val progress = parseIntField(
                            doc = doc,
                            studentId = activeStudentId,
                            materiaName = materiaName,
                            primaryField = "progreso",
                            defaultValue = 0
                        )

                        val credits = parseIntField(
                            doc = doc,
                            studentId = activeStudentId,
                            materiaName = materiaName,
                            primaryField = "creditos",
                            secondaryField = "creditos_asignatura",
                            defaultValue = DEFAULT_CREDITS
                        )

                        val rawRiesgoPredicho = parseStringField(
                            doc = doc,
                            primaryField = "riesgo_predicho",
                            defaultValue = ""
                        )

                        val rawRiesgoOriginal = parseStringField(
                            doc = doc,
                            primaryField = "riesgo",
                            secondaryField = "riskLevel",
                            defaultValue = ""
                        )

                        val storedFechaPrediccion = doc.getTimestamp("fecha_prediccion")

                        // Validar campos usando fromStringOrNull()
                        val validRiesgoPredicho = RiskLevel.fromStringOrNull(rawRiesgoPredicho)
                        val validRiesgoOriginal = RiskLevel.fromStringOrNull(rawRiesgoOriginal)

                        // Priorizar riesgo_predicho si es válido; si es inválido (ej: "URGENTE"), usar riesgo original válido
                        val initialRisk = validRiesgoPredicho ?: validRiesgoOriginal ?: RiskLevel.LOW

                        val trend = parseStringField(
                            doc = doc,
                            primaryField = "trend",
                            defaultValue = "Estable"
                        )

                        val (asistenciaActual, inasistenciaActual) = calculateAttendanceCounts(attendance)
                        val normalizedSubject = normalizeSubjectName(materiaName)

                        // 2 & 3. Consultar Render utilizando contrato JSON actual y obtener riesgo devuelto por la API
                        val apiRiskLevel = try {
                            val request = RiskPredictionRequest(
                                sh1 = DEFAULT_SH1,
                                sf1 = DEFAULT_SF1,
                                creditosAsignatura = credits,
                                asistenciaActual = asistenciaActual,
                                inasistenciaActual = inasistenciaActual,
                                asignatura = normalizedSubject
                            )

                            // Adquirir permiso del semáforo global ÚNICAMENTE para la petición HTTP a Render
                            val response = globalSemaphore.withPermit {
                                riskPredictionApi.predict(request)
                            }

                            val newRiskStr = response.toFirestoreRisk()

                            // IMPORTANTE: Persistir ÚNICAMENTE la respuesta original de Render en los campos autorizados
                            if (checkShouldUpdateFirestore(newRiskStr, rawRiesgoPredicho, storedFechaPrediccion)) {
                                persistPredictionInFirestore(
                                    docRef = doc.reference,
                                    materiaName = materiaName,
                                    newRiskStr = newRiskStr
                                )
                            }

                            RiskLevel.fromString(newRiskStr)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(
                                TAG,
                                "Falla al consultar API de predicción para '$materiaName'. Se conserva el último riesgo disponible de Firestore (${initialRisk.name}): ${e.message}"
                            )
                            initialRisk // Conservar riesgo disponible en Firestore si falla la API
                        }

                        // 4. Aplicar la regla académica local sobre la nota y el riesgo de la API
                        val finalRisk = calculateHybridRisk(
                            apiRisk = apiRiskLevel,
                            realGrade = realGradeOrNull,
                            studentId = activeStudentId,
                            materiaName = materiaName
                        )

                        // 5. Asignar el riesgo final a CourseStatus.riskLevel
                        val courseStatus = CourseStatus(
                            name = materiaName,
                            grade = grade,
                            attendance = attendance,
                            progress = progress,
                            riskLevel = finalRisk,
                            recommendation = "",
                            credits = credits,
                            trend = trend
                        )

                        // La recomendación generada por RecommendationEngine utiliza el riesgo final
                        courseStatus.copy(
                            recommendation = RecommendationEngine.generateRecommendation(courseStatus)
                        )
                    }
                }.awaitAll()
            }

            Log.d(TAG, "[DIAGNOSTIC] === ÉXITO: Dashboard real procesado exitosamente con ${realCourses.size} cursos para '$email' ===")
            return StudentDashboard(
                studentEmail = userEmail,
                period = period,
                courses = realCourses
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[DIAGNOSTIC] MOTIVO DE FALLBACK A MOCK POR EXCEPCIÓN: Excepción capturada en fetchDashboardInternal: ${e.message}. Retornando getMockDashboard().", e)
            return getMockDashboard(email)
        }
    }

    /**
     * Aplica la regla académica local híbrida combinando el riesgo devuelto por la API de Render (o fallback)
     * con la calificación real recuperada de Firestore.
     *
     * Criterios:
     * 1. Escala 0.0 - 5.0. Nota mínima aprobatoria UNAB: 3.0.
     * 2. Si la nota es válida y menor que 3.0 -> riesgo académico local es HIGH.
     * 3. Si la nota es igual o superior a 3.0 -> conservar el riesgo de la API.
     * 4. Si la nota está ausente o es inválida (null) -> conservar el riesgo de la API y registrar una advertencia.
     * 5. El riesgo final es siempre el más severo (mayor prioridad) entre el riesgo de la API y el ajuste local.
     *    Nunca disminuye el riesgo devuelto por Render.
     */
    private fun calculateHybridRisk(
        apiRisk: RiskLevel,
        realGrade: Double?,
        studentId: String,
        materiaName: String
    ): RiskLevel {
        if (realGrade == null) {
            Log.w(
                TAG,
                "ADVERTENCIA [REGLA HÍBRIDA]: Estudiante '$studentId' | Materia '$materiaName' -> Nota ausente o inválida. Conservando riesgo API original (${apiRisk.name})."
            )
            return apiRisk
        }

        val localRisk = if (realGrade < 3.0) {
            RiskLevel.HIGH
        } else {
            apiRisk
        }

        val finalRisk = if (localRisk.priority > apiRisk.priority) {
            localRisk
        } else {
            apiRisk
        }

        Log.d(
            TAG,
            "[REGLA HÍBRIDA] Estudiante '$studentId' | Materia '$materiaName' | Nota: $realGrade | Riesgo API: ${apiRisk.name} | Riesgo Final: ${finalRisk.name}"
        )

        return finalRisk
    }

    /**
     * Lectura y conversión segura del campo `nota` (con prioridad: 1. `nota`, 2. `Nota`, 3. `grade`) desde Firestore.
     * Muestra el campo encontrado, nombre de la materia, ID del documento, valor original y tipo de dato.
     */
    private fun parseGradeField(doc: DocumentSnapshot, materiaName: String): Double {
        val docId = doc.id

        val fieldNameUsed = when {
            doc.contains("nota") -> "nota"
            doc.contains("Nota") -> "Nota"
            doc.contains("grade") -> "grade"
            else -> null
        }

        val rawValue = fieldNameUsed?.let { doc.get(it) } ?: doc.get("nota") ?: doc.get("Nota") ?: doc.get("grade")
        val dataType = rawValue?.javaClass?.name ?: "null"

        if (fieldNameUsed != null && rawValue != null) {
            Log.d(
                TAG,
                "[LECTURA NOTA] Campo Encontrado: '$fieldNameUsed' | Materia: '$materiaName' | Doc ID: '$docId' | Valor Original: '$rawValue' | Tipo de Dato: '$dataType'"
            )
        } else {
            Log.w(
                TAG,
                "ADVERTENCIA [NOTA AUSENTE]: Ninguno de los campos ('nota', 'Nota', 'grade') existe o contiene valor en Doc ID: '$docId' (Materia: '$materiaName'). Asignando 0.0 de respaldo."
            )
        }

        return when (rawValue) {
            is Number -> rawValue.toDouble()
            is String -> {
                val normalizedStr = rawValue.trim().replace(',', '.')
                val parsed = normalizedStr.toDoubleOrNull()
                if (parsed != null) {
                    parsed
                } else {
                    Log.w(
                        TAG,
                        "ADVERTENCIA [FORMATO NOTA INVÁLIDO]: Campo: '$fieldNameUsed' | Materia: '$materiaName' | Doc ID: '$docId' | Valor recibido: '$rawValue' no es una cadena numérica válida. Asignando 0.0 de respaldo."
                    )
                    0.0
                }
            }
            null -> 0.0
            else -> {
                Log.w(
                    TAG,
                    "ADVERTENCIA [TIPO NOTA INESPERADO]: Campo: '$fieldNameUsed' | Materia: '$materiaName' | Doc ID: '$docId' | Tipo '$dataType' con valor '$rawValue'. Asignando 0.0 de respaldo."
                )
                0.0
            }
        }
    }

    /**
     * Conversión segura de campos numéricos enteros (e.g., `asistencia`, `progreso`, `creditos`).
     */
    private fun parseIntField(
        doc: DocumentSnapshot,
        studentId: String,
        materiaName: String,
        primaryField: String,
        secondaryField: String? = null,
        defaultValue: Int
    ): Int {
        val rawValue = doc.get(primaryField) ?: (secondaryField?.let { doc.get(it) })
        val fieldName = if (doc.contains(primaryField)) primaryField else (secondaryField ?: primaryField)

        val logPrefix = "Estudiante '$studentId' | Materia '$materiaName' | Campo '$fieldName'"
        Log.d(TAG, "$logPrefix -> Valor original: '$rawValue' (Tipo: ${rawValue?.javaClass?.simpleName ?: "null"})")

        return when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> {
                val parsedDouble = rawValue.trim().replace(',', '.').toDoubleOrNull()
                if (parsedDouble != null) {
                    parsedDouble.roundToInt()
                } else {
                    Log.w(TAG, "ADVERTENCIA: $logPrefix -> Cadena no convertible a Int '$rawValue'. Se utiliza valor por defecto $defaultValue.")
                    defaultValue
                }
            }
            null -> {
                Log.w(TAG, "ADVERTENCIA: $logPrefix -> Campo ausente (null). Se utiliza valor por defecto $defaultValue.")
                defaultValue
            }
            else -> {
                Log.w(TAG, "ADVERTENCIA: $logPrefix -> Tipo no numérico inesperado '${rawValue::class.java.name}' con valor '$rawValue'. Se utiliza valor por defecto $defaultValue.")
                defaultValue
            }
        }
    }

    /**
     * Conversión segura de campos de texto (e.g., `Materia`, `riesgo`, `riesgo_predicho`).
     */
    private fun parseStringField(
        doc: DocumentSnapshot,
        primaryField: String,
        secondaryField: String? = null,
        defaultValue: String
    ): String {
        val rawValue = doc.get(primaryField) ?: (secondaryField?.let { doc.get(it) })
        return when (rawValue) {
            is String -> rawValue.trim()
            null -> defaultValue
            else -> rawValue.toString().trim()
        }
    }

    /**
     * Evalúa la política de 24 horas y cambios de riesgo para determinar si se debe
     * persistir el resultado de la predicción en Firestore.
     */
    private fun checkShouldUpdateFirestore(
        newRiskStr: String,
        storedRiskStr: String?,
        storedTimestamp: Timestamp?
    ): Boolean {
        // 1. Si no existe un riesgo predicho anterior válido -> Actualizar
        if (RiskLevel.fromStringOrNull(storedRiskStr) == null) {
            return true
        }

        // 2. Si el nivel de riesgo ha cambiado -> Actualizar
        if (newRiskStr != storedRiskStr) {
            return true
        }

        // 3. Si el nivel no cambió, revisar si pasaron al menos 24 horas desde la última fecha
        if (storedTimestamp == null) {
            return true
        }

        val nowMs = System.currentTimeMillis()
        val lastPredictionMs = storedTimestamp.toDate().time
        val hours24Ms = 24 * 60 * 60 * 1000L

        return (nowMs - lastPredictionMs) >= hours24Ms
    }

    /**
     * Persiste asíncronamente los dos campos autorizados (riesgo_predicho y fecha_prediccion)
     * en Firestore sin modificar ningún otro campo del documento.
     */
    private fun persistPredictionInFirestore(
        docRef: DocumentReference,
        materiaName: String,
        newRiskStr: String
    ) {
        try {
            val updates = mapOf(
                "riesgo_predicho" to newRiskStr,
                "fecha_prediccion" to Timestamp.now()
            )

            docRef.update(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Persistencia exitosa en Firestore para '$materiaName': riesgo_predicho=$newRiskStr")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al persistir riesgo_predicho en Firestore para '$materiaName': ${e.message}", e)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al intentar actualizar Firestore para '$materiaName': ${e.message}", e)
        }
    }

    /**
     * Calcula la cantidad de asistencias e inasistencias para la API
     * basándose en un porcentaje de asistencia (0-100) sobre TOTAL_SEMESTER_CLASSES.
     */
    private fun calculateAttendanceCounts(attendancePercent: Int): Pair<Int, Int> {
        val boundedPercent = attendancePercent.coerceIn(0, 100)
        val asistenciaActual = (boundedPercent * TOTAL_SEMESTER_CLASSES / 100.0).roundToInt()
        val inasistenciaActual = (TOTAL_SEMESTER_CLASSES - asistenciaActual).coerceAtLeast(0)
        return Pair(asistenciaActual, inasistenciaActual)
    }

    /**
     * Normaliza el nombre de la materia eliminando tildes y convirtiendo a mayúsculas
     * para coincidir con el formato esperado por el modelo de ML (ej: ALGEBRA LINEAL).
     */
    private fun normalizeSubjectName(rawName: String): String {
        return rawName.trim()
            .uppercase()
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ñ", "N")
    }

    /**
     * Datos de ejemplo mantenidos únicamente como respaldo secundario de emergencia
     * si Firestore falla por completo o la cuenta de usuario no existe.
     */
    private fun getMockDashboard(email: String): StudentDashboard {
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
        val coursesWithAi = courses.map { it.copy(recommendation = RecommendationEngine.generateRecommendation(it)) }
        return StudentDashboard(email, "Primer Semestre", coursesWithAi)
    }
}
