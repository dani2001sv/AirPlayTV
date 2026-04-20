package com.airplaytv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * Vista personalizada que muestra el estado del receptor AirPlay.
 * Diseñada para pantallas de TV: alta visibilidad, sin texto pequeño.
 */
class StatusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentStatus: String = AirPlayStatusState.IDLE
    private var clientName: String? = null

    // Animación del pulso
    private var pulseRadius = 0f
    private var pulseAlpha = 0f
    private var pulseAnimator: ValueAnimator? = null

    // Animación del halo externo (cuando conectado)
    private var haloAnimator: ValueAnimator? = null
    private var haloScale = 1f

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colores por estado
    private val colorWaiting = Color.parseColor("#4A9EFF")    // Azul — esperando
    private val colorConnected = Color.parseColor("#00E676")  // Verde — conectado
    private val colorError = Color.parseColor("#FF5252")      // Rojo — error
    private val colorIdle = Color.parseColor("#546E7A")       // Gris azulado — inactivo
    private val colorStarting = Color.parseColor("#FFB300")   // Ámbar — iniciando

    init {
        startIdleAnimation()
    }

    fun setStatus(status: String, clientName: String? = null) {
        if (this.currentStatus == status && this.clientName == clientName) return
        this.currentStatus = status
        this.clientName = clientName

        pulseAnimator?.cancel()
        haloAnimator?.cancel()

        when (status) {
            AirPlayStatusState.WAITING -> startPulseAnimation()
            AirPlayStatusState.CONNECTED -> startConnectedAnimation()
            AirPlayStatusState.STARTING -> startStartingAnimation()
            AirPlayStatusState.ERROR -> startErrorAnimation()
            else -> startIdleAnimation()
        }

        invalidate()
    }

    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                pulseRadius = v
                pulseAlpha = 1f - v
                invalidate()
            }
            start()
        }
    }

    private fun startConnectedAnimation() {
        // Halo que respira suavemente
        haloAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                haloScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startStartingAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseRadius = it.animatedValue as Float
                pulseAlpha = 0.6f * (1f - it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    private fun startErrorAnimation() {
        // Parpadeo rápido de error
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                pulseRadius = 0.3f
                invalidate()
            }
            start()
        }
    }

    private fun startIdleAnimation() {
        pulseRadius = 0f
        pulseAlpha = 0f
        haloScale = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(width, height) / 2f * 0.55f
        val activeColor = getActiveColor()

        // Sombra suave
        shadowPaint.apply {
            color = activeColor
            alpha = 40
            maskFilter = BlurMaskFilter(baseRadius * 0.8f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy + baseRadius * 0.05f, baseRadius * haloScale, shadowPaint)

        // Pulso exterior (ondas)
        if (pulseAlpha > 0f) {
            val pulseR = baseRadius + (baseRadius * 0.7f * pulseRadius)
            pulsePaint.apply {
                color = activeColor
                alpha = (255 * pulseAlpha * 0.5f).toInt()
                style = Paint.Style.STROKE
                strokeWidth = baseRadius * 0.08f
                maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(cx, cy, pulseR, pulsePaint)
        }

        // Halo exterior (cuando conectado)
        if (haloScale > 1f) {
            pulsePaint.apply {
                color = activeColor
                alpha = 60
                style = Paint.Style.STROKE
                strokeWidth = baseRadius * 0.06f
                maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(cx, cy, baseRadius * haloScale * 1.1f, pulsePaint)
        }

        // Círculo principal con gradiente radial
        val gradient = RadialGradient(
            cx, cy * 0.85f,
            baseRadius,
            intArrayOf(lightenColor(activeColor, 0.3f), activeColor, darkenColor(activeColor, 0.2f)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        circlePaint.apply {
            shader = gradient
            style = Paint.Style.FILL
            maskFilter = null
        }
        canvas.drawCircle(cx, cy, baseRadius, circlePaint)

        // Reflejo/brillo en la parte superior del círculo
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 50
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(cx - baseRadius * 0.45f, cy - baseRadius * 0.75f,
                  cx + baseRadius * 0.45f, cy - baseRadius * 0.1f),
            highlightPaint
        )

        // Icono WiFi/AirPlay en el centro
        drawAirPlayIcon(canvas, cx, cy, baseRadius * 0.45f)
    }

    private fun drawAirPlayIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 220
            style = Paint.Style.STROKE
            strokeWidth = size * 0.12f
            strokeCap = Paint.Cap.ROUND
        }

        // Icono AirPlay simplificado: arcos concéntricos + triángulo
        // Arco exterior
        val oval1 = RectF(cx - size, cy - size * 0.7f, cx + size, cy + size * 0.3f)
        canvas.drawArc(oval1, 210f, 120f, false, iconPaint)

        // Arco medio
        val s2 = size * 0.65f
        val oval2 = RectF(cx - s2, cy - s2 * 0.7f, cx + s2, cy + s2 * 0.3f)
        canvas.drawArc(oval2, 210f, 120f, false, iconPaint)

        // Arco interior
        val s3 = size * 0.3f
        val oval3 = RectF(cx - s3, cy - s3 * 0.7f, cx + s3, cy + s3 * 0.3f)
        canvas.drawArc(oval3, 210f, 120f, false, iconPaint)

        // Triángulo (punta hacia abajo)
        val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 220
            style = Paint.Style.FILL
        }
        val triSize = size * 0.22f
        val triY = cy + size * 0.35f
        val path = Path().apply {
            moveTo(cx, triY + triSize * 1.1f)
            lineTo(cx - triSize, triY)
            lineTo(cx + triSize, triY)
            close()
        }
        canvas.drawPath(path, triPaint)
    }

    private fun getActiveColor(): Int = when (currentStatus) {
        AirPlayStatusState.WAITING -> colorWaiting
        AirPlayStatusState.CONNECTED -> colorConnected
        AirPlayStatusState.ERROR -> colorError
        AirPlayStatusState.STARTING -> colorStarting
        else -> colorIdle
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1 - factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1 - factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1 - factor)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        haloAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
