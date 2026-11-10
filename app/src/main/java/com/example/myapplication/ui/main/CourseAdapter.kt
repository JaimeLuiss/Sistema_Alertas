package com.example.myapplication.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.academic.CourseStatus
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import java.util.Locale

class CourseAdapter(
    private var courses: List<CourseStatus>,
    private val onGradesClick: (CourseStatus) -> Unit,
    private val onWellnessClick: (CourseStatus) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCourseName: TextView = view.findViewById(R.id.tvCourseName)
        val tvCredits: TextView = view.findViewById(R.id.tvCredits)
        val tvGrade: TextView = view.findViewById(R.id.tvGrade)
        val progressCourse: ProgressBar = view.findViewById(R.id.progressCourse)
        val tvProgress: TextView = view.findViewById(R.id.tvProgress)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvRecommendation: TextView = view.findViewById(R.id.tvRecommendation)
        val layoutAiRecommendation: MaterialCardView? = view.findViewById(R.id.layoutAiRecommendation)
        val tvWellnessLink: TextView? = view.findViewById(R.id.tvWellnessLink)
        val btnViewGrades: MaterialButton = view.findViewById(R.id.btnViewGrades)
        val btnViewSyllabus: MaterialButton = view.findViewById(R.id.btnViewSyllabus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]
        val riskColor = Color.parseColor(course.riskLevel.color)

        holder.tvCourseName.text = course.name
        holder.tvCredits.text = "${course.credits} créditos"
        holder.tvGrade.text = String.format(Locale.US, "%.1f", course.grade)
        holder.tvGrade.setTextColor(riskColor)

        holder.progressCourse.progress = course.attendance
        holder.progressCourse.progressTintList = ColorStateList.valueOf(riskColor)
        holder.tvProgress.text = "Asistencia: ${course.attendance}%"

        holder.tvStatus.text = "Riesgo ${course.riskLevel.label.lowercase()}"
        holder.tvStatus.setTextColor(riskColor)

        if (course.recommendation.isNotBlank()) {
            holder.layoutAiRecommendation?.visibility = View.VISIBLE
            holder.tvRecommendation.text = course.recommendation
        } else {
            holder.layoutAiRecommendation?.visibility = View.GONE
        }

        holder.tvWellnessLink?.setOnClickListener { onWellnessClick(course) }
        holder.btnViewGrades.setOnClickListener { onGradesClick(course) }
    }

    override fun getItemCount() = courses.size
}
