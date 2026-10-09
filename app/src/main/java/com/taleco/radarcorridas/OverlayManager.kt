package com.taleco.radarcorridas

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
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

    // Aviso de radar
    private var speedView: LinearLayout? = null
    private var speedSign: TextView? = null
    private var speedValue: TextView? = null
    private var speedLine: TextView? = null
    private var speedDist: TextView? = null
    private var speedDistUnit: TextView? = null
    private var speedBase: GradientDrawable? = null
    private var speedFillShape: GradientDrawable? = null
    private var speedFill: ClipDrawable? = null

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

    /** Mostra (ou atualiza) o aviso de radar no topo da tela. */
    fun showSpeed(b: SpeedBanner) {
        if (speedView == null) createSpeedView()
        if (speedView == null) return
        val dark = b.color == Colors.YELLOW
        val fg = if (dark) Color.parseColor("#111111") else Color.WHITE
        speedBase?.setColor(b.color)
        speedFillShape?.setColor(darker(b.color))
        speedFill?.level = (b.progress.coerceIn(0f, 1f) * 10_000).toInt()
        speedSign?.text = b.sign ?: b.limit?.toString() ?: "?"
        speedSign?.textSize = if (b.sign != null) 26f else 20f
        (speedSign?.background as? GradientDrawable)?.setStroke(
            service.dp(5), if (b.sign != null) Color.parseColor("#E8590C") else Color.parseColor("#D62828")
        )
        speedValue?.text = b.title ?: "${b.speedKmh} km/h"
        speedValue?.setTextColor(fg)
        speedLine?.text = b.line
        speedLine?.setTextColor(fg)
        speedDist?.setTextColor(fg)
        speedDistUnit?.setTextColor(fg)
        if (b.distText != null) {
            speedDist?.text = b.distText
            speedDistUnit?.text = b.distUnit
        } else if (b.distanceM != null) {
            speedDist?.text = b.distanceM.toString()
            speedDistUnit?.text = b.distUnit
        } else {
            speedDist?.text = ""
            speedDistUnit?.text = ""
        }
    }

    fun hideSpeed() {
        speedView?.let { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        speedView = null
        speedSign = null
        speedValue = null
        speedLine = null
        speedDist = null
        speedDistUnit = null
        speedBase = null
        speedFillShape = null
        speedFill = null
    }

    /** Mesma cor, uns 35% mais escura: é o "preenchimento" que avança até o radar. */
    private fun darker(c: Int): Int {
        val f = 0.65f
        return Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    }

    private fun createSpeedView() {
        val radius = service.dp(18).toFloat()
        val base = GradientDrawable().apply { cornerRadius = radius; setColor(Colors.SURFACE_2) }
        val fillShape = GradientDrawable().apply { cornerRadius = radius; setColor(Colors.SURFACE) }
        val fill = ClipDrawable(fillShape, Gravity.START, ClipDrawable.HORIZONTAL).apply { level = 0 }

        val root = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(service.dp(10), service.dp(8), service.dp(16), service.dp(8))
            background = LayerDrawable(arrayOf(base, fill))
            elevation = service.dp(6).toFloat()
        }
        // Placa de limite de velocidade: círculo branco com borda vermelha
        val size = service.dp(54)
        val sign = TextView(service).apply {
            gravity = Gravity.CENTER
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111111"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(service.dp(5), Color.parseColor("#D62828"))
            }
        }
        root.addView(sign, LinearLayout.LayoutParams(size, size).apply { rightMargin = service.dp(10) })

        // Meio: velocidade e nome da rua
        val col = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val value = TextView(service).apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        val line = TextView(service).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 2
        }
        col.addView(value)
        col.addView(line)
        root.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // Direita: distância até o radar, bem grande
        val distCol = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val dist = TextView(service).apply {
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
        }
        val distUnit = TextView(service).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        distCol.addView(dist)
        distCol.addView(distUnit)
        root.addView(distCol, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = service.dp(8) })


        // Fechar fácil: um toque em qualquer lugar do aviso, ou arrastar para o lado.
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        val swipeDistance = service.dp(80)
        var downX = 0f
        var moved = false
        // Segurar o aviso de radar por 1 s: "radar errado" (some e não aparece mais)
        var longDone = false
        val longPress = Runnable {
            if (SpeedWatch.reportWrong()) longDone = true
        }
        root.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    moved = false
                    longDone = false
                    v.postDelayed(longPress, 1000L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    if (abs(dx) > slop) {
                        moved = true
                        v.removeCallbacks(longPress)
                    }
                    v.translationX = dx
                    v.alpha = (1f - abs(dx) / (v.width.coerceAtLeast(1) * 0.8f)).coerceIn(0.2f, 1f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    val dx = ev.rawX - downX
                    if (longDone) {
                        v.translationX = 0f
                        v.alpha = 1f
                    } else if (!moved || abs(dx) > swipeDistance) {
                        // toque simples ou arrasto longo: fecha
                        v.animate().translationX(if (dx < 0) -v.width.toFloat() else v.width.toFloat())
                            .alpha(0f).setDuration(150).withEndAction { HazardWatch.onBannerTap() }.start()
                    } else {
                        // arrasto curto: volta para o lugar
                        v.animate().translationX(0f).alpha(1f).setDuration(150).start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPress)
                    v.animate().translationX(0f).alpha(1f).setDuration(150).start()
                    true
                }
                else -> false
            }
        }

        val width = service.resources.displayMetrics.widthPixels - service.dp(24)
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Só o próprio aviso recebe toque (para o ✕); o resto da tela continua normal.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = service.dp(36)
        try {
            wm.addView(root, lp)
            speedView = root
            speedSign = sign
            speedValue = value
            speedLine = line
            speedDist = dist
            speedDistUnit = distUnit
            speedBase = base
            speedFillShape = fillShape
            speedFill = fill
        } catch (e: Exception) {
            speedView = null
        }
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
        // Segurar o "R" por 0,6 s marca uma valeta.
        var longFired = false
        val longPress = Runnable {
            longFired = true
            HazardWatch.markManual(service)
        }
        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = ev.rawX; touchY = ev.rawY
                    moved = false
                    longFired = false
                    v.postDelayed(longPress, 600L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - touchX
                    val dy = ev.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        v.removeCallbacks(longPress)
                    }
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (moved) {
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else if (!longFired) {
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
