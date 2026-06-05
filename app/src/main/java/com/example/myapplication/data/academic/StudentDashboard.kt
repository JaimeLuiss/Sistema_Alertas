package com.example.myapplication.data.academic

data class StudentDashboard(
    val studentEmail: String,
    val period: String,
    val courses: List<CourseStatus>
) {
    fun averageGrade(): Double {
        return courses.map { it.grade }.average()
    }

    fun overallRisk(): RiskLevel {
        val average = averageGrade()
        val highRiskCount = courses.count { it.riskLevel == RiskLevel.HIGH }
        val mediumRiskCount = courses.count { it.riskLevel == RiskLevel.MEDIUM }

        return when {
            highRiskCount > 0 || average < 3.0 -> RiskLevel.HIGH
            mediumRiskCount > 0 || average < 3.7 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
}
