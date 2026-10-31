/*
 * The battery-log service, run by package/tweaks/battery-log/run.sh under app_process. Takes a
 * sample a minute through the store (`kt`), keeps six hours of them on disk, and gives them to
 * the app over a local socket, so the history is there whenever the app opens.
 */
@file:JvmName("Main")

package app.cabinwright.battery.log

import android.net.LocalServerSocket
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

private const val SOCKET = "app.cabinwright.battery.log"
private const val PERIOD_MS = 60_000L
private const val KEEP = 360
private const val CLUSTER = "com.hkmc.micom.cluster"
private const val CLUSTER_ADD = "37"
private const val CLUSTER_REMOVE = "38"
private const val BATTERY_INFO = "109"
private const val BATTERY_LISTENER = "com.hkmc.cluster.INavigationClusterListener"
private const val BATTERY_INFO_REPLY = "16"
private const val BCM = "com.hkmc.micom.bcm"
private const val INDICATOR = "109"

/** The reply's words: the array's length, then its 8 fields. The lowest cell temperature is field 1. */
private const val WORDS = 9
private const val LENGTH = 8
private const val MIN_TEMP = 2
private const val COLDEST = -40
private const val HOTTEST = 60

private val ON = Regex("Parcel\\(00000000 00000001")

private class Sample(val time: Long, val temp: Int, val conditioning: Boolean) {
    val line get() = "$time $temp ${if (conditioning) 1 else 0}\n"
}

private val samples = ArrayDeque<Sample>()
private var appended = 0

private fun kt(vararg args: String): String? {
    val p = ProcessBuilder(listOf("kt") + args).redirectErrorStream(true).start()
    p.outputStream.close()
    val out = p.inputStream.bufferedReader().readText()
    return if (p.waitFor() == 0) out else null
}

private fun temperature(): Int? {
    val reply = kt("listen", CLUSTER, CLUSTER_ADD, CLUSTER_REMOVE, BATTERY_INFO, BATTERY_LISTENER, BATTERY_INFO_REPLY)
    val words = reply?.trim()?.split(' ')?.map { it.toIntOrNull() } ?: return null
    if (words.size != WORDS || words[0] != LENGTH) return null
    return words[MIN_TEMP]?.takeIf { it in COLDEST..HOTTEST }
}

private fun parse(line: String): Sample? {
    val f = line.split(' ')
    val time = f[0].toLongOrNull()
    val temp = f.getOrNull(1)?.toIntOrNull()
    return if (f.size == 3 && time != null && temp != null) Sample(time, temp, f[2] == "1") else null
}

private fun load(file: File) {
    if (!file.isFile) return
    val oldest = System.currentTimeMillis() - KEEP * PERIOD_MS
    for (line in file.readLines()) {
        val sample = parse(line)
        if (sample != null && sample.time >= oldest) samples.addLast(sample)
    }
    while (samples.size > KEEP) samples.removeFirst()
}

/** Appends, and rewrites the file once it's about twice the size it needs to be. */
private fun record(file: File, sample: Sample) {
    synchronized(samples) {
        samples.addLast(sample)
        while (samples.size > KEEP) samples.removeFirst()
        if (++appended < KEEP) {
            file.appendText(sample.line)
        } else {
            appended = 0
            file.writeText(samples.joinToString("") { it.line })
        }
    }
}

private fun sampling(file: File) {
    while (true) {
        try {
            val temp = temperature()
            val conditioning = kt("binder", BCM, INDICATOR)?.let { ON.containsMatchIn(it) } ?: false
            if (temp != null) record(file, Sample(System.currentTimeMillis(), temp, conditioning))
        } catch (e: IOException) {
            println("battery log: $e")
        }
        Thread.sleep(PERIOD_MS)
    }
}

private fun serving() {
    val server = LocalServerSocket(SOCKET)
    while (true) {
        val client = server.accept()
        try {
            val all = synchronized(samples) { samples.joinToString("") { it.line } }
            client.outputStream.write(all.toByteArray())
        } catch (e: IOException) {
            println("battery log: $e")
        } finally {
            client.close()
        }
    }
}

fun main(args: Array<String>) {
    val file = File(args[0])
    load(file)
    Thread { sampling(file) }.apply { isDaemon = true }.start()
    serving()
}
