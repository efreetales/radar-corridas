package com.taleco.radarcorridas

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Desenha o cartão e o botão flutuante por cima de qualquer app.
 * Usa a camada de acessibilidade, então não precisa da permissão "sobrepor a outros apps".
 */
class OverlayManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private var card: View? = null
    private var bubble: View? = null

    val isCardShowing: Boolean get() = card != null

    fun showCard(eval: Evaluation, prefs: Prefs) {
        hideCard()
        val view = CardView.build(service, eval)
        view.alpha = prefs.opacity
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER_HORIZONTAL or when (prefs.position) {
            CardPosition.TOPO -> Gravity.TOP
            CardPosition.MEIO -> Gravity.CENTER_VERTICAL
            CardPosition.BAIXO -> Gravity.BOTTOM
        }
        lp.y = when (prefs.position) {
            CardPosition.TOPO -> service.dp(120)
            CardPosition.MEIO -> 0
            CardPosition.BAIXO -> service.dp(140)
        }
        try {
            wm.addView(view, lp)
            card = view
        } catch (e: Exception) {
            card = null
        }
    }

    fun hideCard() {
        card?.let { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        card = null
    }

    fun showBubble(prefs: Prefs) {
        if (bubble != null) return
        val size = service.dp(52)
        val view = TextView(service).apply {
            text = "R"
            setTextColor(Color.parseColor("#06231B"))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Colors.ACCENT)
                setStroke(service.dp(3), Color.parseColor("#0D1117"))
            }
            alpha = 0.9f
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = prefs.bubbleX
        lp.y = prefs.bubbleY

        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = ev.rawX; touchY = ev.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - touchX
                    val dy = ev.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else {
                        openApp()
                    }
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(view, lp)
            bubble = view
        } catch (e: Exception) {
            bubble = null
        }
    }

    fun hideBubble() {
        bubble?.let { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        bubble = null
    }

    private fun openApp() {
        val i = Intent(service, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { service.startActivity(i) } catch (_: Exception) {}
    }
}
