package app.cabinwright.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.text.format.DateFormat
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * The battery's temperature over time, in the head unit's flat style: a line in the app's
 * gradient, with conditioning shaded behind it and a break wherever the head unit was off.
 */
class TemperatureChart(
    context: Context,
    private val axis: Int,
    private val grid: Int,
    private val shade: Int,
    private val top: Int,
    private val bottom: Int,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINE_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rule = Paint().apply {
        color = grid
        strokeWidth = density
    }
    private val band = Paint().apply { color = shade }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axis
        textSize = LABEL_SP * scaled
    }
    private val message = Paint(label).apply { textAlign = Paint.Align.CENTER }
    private var samples: List<BatteryLog.Sample> = emptyList()

    fun show(latest: List<BatteryLog.Sample>) {
        samples = latest
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (samples.isEmpty()) {
            canvas.drawText("Waiting for readings", width / 2f, height / 2f, message)
            return
        }
        val left = LEFT_DP * density
        val right = width - EDGE_DP * density
        val plotTop = EDGE_DP * density
        val plotBottom = height - BOTTOM_DP * density
        val temps = samples.map { fahrenheit(it.temp) }
        val low = floor((temps.minOrNull()!! - PAD_F) / STEP_F) * STEP_F
        val high = max(ceil((temps.maxOrNull()!! + PAD_F) / STEP_F) * STEP_F, low + MIN_RANGE_F)
        val end = System.currentTimeMillis()
        val start = end - (end - samples[0].time).coerceIn(MIN_SPAN_MS, MAX_SPAN_MS)
        val x = { t: Long -> left + (right - left) * (t - start).toFloat() / (end - start) }
        val y = { f: Double -> (plotBottom - (plotBottom - plotTop) * (f - low) / (high - low)).toFloat() }

        var i = 0
        while (i < samples.size) {
            if (samples[i].conditioning) {
                val from = samples[i].time
                while (i + 1 < samples.size && samples[i + 1].conditioning) i++
                val to = max(samples[i].time, from + PERIOD_MS)
                canvas.drawRect(x(from).coerceAtLeast(left), plotTop, x(to).coerceAtMost(right), plotBottom, band)
            }
            i++
        }
        for (n in 0..LINES) {
            val f = low + (high - low) * n / LINES
            canvas.drawLine(left, y(f), right, y(f), rule)
            canvas.drawText(String.format(Locale.US, "%.0f°", f), 0f, y(f) + label.textSize / 3, label)
        }
        val clock = DateFormat.getTimeFormat(context)
        val below = height - label.descent()
        canvas.drawText(clock.format(java.util.Date(start)), left, below, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("Now", right, below, label)
        label.textAlign = Paint.Align.LEFT

        line.shader = LinearGradient(0f, plotTop, 0f, plotBottom, top, bottom, Shader.TileMode.CLAMP)
        val path = Path()
        var previous: BatteryLog.Sample? = null
        for (s in samples) {
            if (s.time < start) continue
            val joined = previous?.let { s.time - it.time <= GAP_MS } == true
            if (joined) path.lineTo(x(s.time), y(fahrenheit(s.temp))) else path.moveTo(x(s.time), y(fahrenheit(s.temp)))
            if (!joined) path.lineTo(x(s.time) + 1, y(fahrenheit(s.temp)))
            previous = s
        }
        canvas.drawPath(path, line)
    }

    companion object {
        private const val LINE_DP = 3f
        private const val LABEL_SP = 16f
        private const val LEFT_DP = 44f
        private const val EDGE_DP = 8f
        private const val BOTTOM_DP = 28f
        private const val LINES = 2
        private const val PAD_F = 2.0
        private const val STEP_F = 5.0
        private const val MIN_RANGE_F = 10.0
        private const val PERIOD_MS = 60_000L
        private const val GAP_MS = 3 * PERIOD_MS
        private const val MIN_SPAN_MS = 30 * PERIOD_MS
        private const val MAX_SPAN_MS = 360 * PERIOD_MS

        fun fahrenheit(c: Int): Double = c * 9.0 / 5.0 + 32.0
    }
}
