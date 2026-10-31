package app.cabinwright.battery

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException

/** What the battery-log service has kept: nothing if it's off or hasn't taken a sample yet. */
object BatteryLog {
    class Sample(val time: Long, val temp: Int, val conditioning: Boolean)

    private const val SOCKET = "app.cabinwright.battery.log"
    private const val STALE_MS = 3 * 60_000L

    fun read(): List<Sample> {
        val socket = LocalSocket()
        return try {
            socket.connect(LocalSocketAddress(SOCKET))
            socket.inputStream.bufferedReader().readLines().mapNotNull { parse(it) }
        } catch (ignored: IOException) {
            emptyList()
        } finally {
            socket.close()
        }
    }

    private fun parse(line: String): Sample? {
        val f = line.split(' ')
        val time = f[0].toLongOrNull()
        val temp = f.getOrNull(1)?.toIntOrNull()
        return if (f.size == 3 && time != null && temp != null) Sample(time, temp, f[2] == "1") else null
    }

    /** Null without a recent sample: an old one isn't the temperature now. */
    fun current(samples: List<Sample>): Int? =
        samples.lastOrNull()?.takeIf { System.currentTimeMillis() - it.time < STALE_MS }?.temp
}
