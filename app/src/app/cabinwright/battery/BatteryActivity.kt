package app.cabinwright.battery

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cabinwright.api.broker.CarVehicle
import app.cabinwright.api.ui.CarPage
import java.util.Locale

/**
 * Battery: the car's battery levels, its temperature over time, and battery conditioning on the
 * screen the app opens to, so it can be started on the way to a charger. The store runs the
 * session, so closing the app doesn't end it, and only the car itself saying so shows it as
 * running. The temperature comes from the package's battery-log service.
 */
class BatteryActivity : CarPage() {
    private val handler = Handler()
    private lateinit var worker: HandlerThread
    private lateinit var background: Handler
    private lateinit var main: TextView
    private lateinit var twelve: TextView
    private lateinit var health: TextView
    private lateinit var temperature: TextView
    private lateinit var chart: TemperatureChart
    private lateinit var phase: TextView
    private lateinit var detail: TextView
    private lateinit var action: TextView

    /** A start or stop on its way to the store: the button waits for its answer. */
    private var sending = false
    private var refusal = ""
    private var shown: CarVehicle.Conditioning? = null

    private val refresher = object : Runnable {
        override fun run() {
            background.post { poll() }
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        worker = HandlerThread("battery").apply { start() }
        background = Handler(worker.looper)
        header("Battery")
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(PADDING), dp(PADDING), dp(PADDING), dp(PADDING))
        page.addView(row, LinearLayout.LayoutParams(FILL, 0, 1f))
        row.addView(conditioningPanel(), LinearLayout.LayoutParams(0, FILL, 1f))
        val side = LinearLayout(this)
        side.orientation = LinearLayout.VERTICAL
        side.setPadding(dp(PADDING), 0, 0, 0)
        row.addView(side, LinearLayout.LayoutParams(0, FILL, READINGS_WEIGHT))
        val readings = LinearLayout(this)
        readings.orientation = LinearLayout.HORIZONTAL
        side.addView(readings, LinearLayout.LayoutParams(FILL, WRAP))
        main = reading(readings, "Main battery")
        temperature = reading(readings, "Temperature")
        twelve = reading(readings, "12V battery")
        health = reading(readings, "Health")
        val below = LinearLayout.LayoutParams(FILL, 0, 1f)
        below.topMargin = dp(PADDING)
        side.addView(chartPanel(), below)
    }

    private fun chartPanel(): View {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = surface(dp(RADIUS))
        panel.setPadding(dp(PADDING), dp(PADDING), dp(PADDING), dp(PADDING))
        panel.addView(text("Lowest battery temperature, °F", TITLE_SP, MUTED, false))
        chart = TemperatureChart(this, MUTED, EDGE, ACCENT_END and SHADE_MASK or SHADE_ALPHA, ICON_START, ACCENT_END)
        val size = LinearLayout.LayoutParams(FILL, 0, 1f)
        size.topMargin = dp(PADDING / 2)
        panel.addView(chart, size)
        return panel
    }

    private fun conditioningPanel(): View {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setGravity(Gravity.CENTER)
        panel.background = surface(dp(RADIUS))
        panel.setPadding(dp(PADDING), dp(PADDING), dp(PADDING), dp(PADDING))
        val title = text("Battery conditioning", TITLE_SP, MUTED, false)
        title.gravity = Gravity.CENTER
        panel.addView(title, LinearLayout.LayoutParams(FILL, WRAP))
        phase = text("", STATE_SP, TEXT, true)
        phase.gravity = Gravity.CENTER
        panel.addView(phase, LinearLayout.LayoutParams(FILL, WRAP))
        detail = text("", DETAIL_SP, MUTED, false)
        detail.gravity = Gravity.CENTER
        panel.addView(detail, LinearLayout.LayoutParams(FILL, WRAP))
        action = button("") { tapped() }
        action.textSize = ACTION_SP.toFloat()
        action.gravity = Gravity.CENTER
        val size = LinearLayout.LayoutParams(FILL, dp(ACTION_HEIGHT))
        size.topMargin = dp(PADDING)
        panel.addView(action, size)
        return panel
    }

    private fun reading(parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(text(label, TITLE_SP, MUTED, false))
        val value = text("–", VALUE_SP, TEXT, false)
        box.addView(value)
        parent.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f))
        return value
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    override fun onDestroy() {
        worker.quit()
        super.onDestroy()
    }

    private fun poll() {
        val state = CarVehicle.state()
        val session = CarVehicle.conditioning()
        handler.post { show(state, session) }
        val samples = BatteryLog.read()
        handler.post { showTemperature(samples) }
    }

    private fun tapped() {
        val going = shown?.phase == CarVehicle.WAITING || shown?.phase == CarVehicle.ACTIVE
        sending = true
        refusal = ""
        action.isEnabled = false
        background.post {
            val r = if (going) CarVehicle.stopConditioning() else CarVehicle.startConditioning()
            val message = if (r.ok) "" else r.message.orEmpty().ifEmpty { "That didn't work." }
            handler.post {
                sending = false
                refusal = message
            }
            poll()
        }
    }

    private fun showTemperature(samples: List<BatteryLog.Sample>) {
        temperature.text = BatteryLog.current(samples)?.let {
            String.format(Locale.US, "%.0f°F", TemperatureChart.fahrenheit(it))
        } ?: "–"
        chart.show(samples)
    }

    private fun show(state: CarVehicle.State?, session: CarVehicle.Conditioning?) {
        main.text = state?.batteryMain?.let { String.format(Locale.US, "%.1f%%", it) } ?: "–"
        twelve.text = state?.battery12v?.let { "$it%" } ?: "–"
        health.text = state?.batteryHealth?.toString() ?: "–"
        shown = session
        val usable = state?.supported == true && session != null
        when {
            state == null || session == null -> show("Not available", "The store's service isn't answering.", "")
            !state.supported -> show("Not available", "This car's software hasn't been tested with this.", "")
            else -> showSession(session)
        }
        val busy = session?.phase == CarVehicle.STOPPING
        action.isEnabled = usable && !sending && !busy
        action.visibility = if (usable) View.VISIBLE else View.INVISIBLE
    }

    private fun showSession(session: CarVehicle.Conditioning) {
        when (session.phase) {
            CarVehicle.WAITING -> show("Waiting for the battery", "This can take a minute or two.", "Cancel")
            CarVehicle.ACTIVE -> show("Conditioning", "The car is conditioning the battery.", "Stop")
            CarVehicle.STOPPING -> show("Stopping", "Waiting for the car to confirm.", "Stop")
            else -> show("Off", refusal.ifEmpty { ending(session.ended) }, "Start")
        }
    }

    private fun show(state: String, why: String, label: String) {
        phase.text = state
        detail.text = why
        action.text = label
    }

    private fun ending(reason: String): String = when (reason) {
        "finished" -> "The car finished conditioning."
        "not_ready" -> "Stopped: the car is no longer ready to drive."
        "timeout" -> "The car didn't start. The battery may not need it."
        "failed" -> "Stopped: something went wrong."
        else -> ""
    }

    private companion object {
        const val REFRESH_MS = 3_000L
        const val FILL = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val PADDING = 16
        const val RADIUS = 8
        const val TITLE_SP = 18
        const val STATE_SP = 32
        const val DETAIL_SP = 18
        const val VALUE_SP = 32
        const val READINGS_WEIGHT = 2f
        const val SHADE_MASK = 0x00FFFFFF
        const val SHADE_ALPHA = 0x38000000
        const val ACTION_SP = 28
        const val ACTION_HEIGHT = 96
    }
}
