package com.example.myapplication.utils

import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

object Animations {

    /**
     * Hace que una vista aparezca desde abajo con un desvanecimiento.
     */
    fun slideUp(view: View, delay: Long = 0) {
        view.visibility = View.INVISIBLE
        view.postDelayed({
            view.visibility = View.VISIBLE
            val animation = AnimationUtils.loadAnimation(view.context, android.R.anim.fade_in)
            animation.duration = 500
            view.startAnimation(animation)
            
            // Efecto de desplazamiento hacia arriba
            view.translationY = 100f
            view.animate()
                .translationY(0f)
                .setDuration(500)
                .start()
        }, delay)
    }

    /**
     * Aplica una animación en cascada a los elementos de un RecyclerView.
     */
    fun animateRecycler(recyclerView: RecyclerView) {
        val context = recyclerView.context
        val controller = AnimationUtils.loadLayoutAnimation(context, android.R.anim.slide_in_left) // O uno personalizado
        recyclerView.layoutAnimation = LayoutAnimationController(controller.animation).apply {
            delay = 0.15f
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }
}
