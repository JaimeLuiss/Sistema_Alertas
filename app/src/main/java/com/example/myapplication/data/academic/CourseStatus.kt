package com.example.myapplication.data.academic

data class CourseStatus(
    val name: String,
    val grade: Double,
    val attendance: Int,
    val progress: Int,
    val riskLevel: RiskLevel,
    val recommendation: String,
    val credits: Int,
    val trend: String = "Estable"
)
