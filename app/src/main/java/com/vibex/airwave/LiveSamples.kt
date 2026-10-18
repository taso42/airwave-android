package com.vibex.airwave

import com.vibex.airwave.ble.LiveReading
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone

internal fun decodeSamples(batch: LiveReading, origin: Long?, source: SampleSource = SampleSource.Bluetooth(null)): Pair<Long, List<Sample>> {
    require(batch.intervalMs > 0) { "Invalid sample interval" }
    require(batch.flow.isNotEmpty() && batch.flow.size == batch.pressure.size) { "Incomplete live sample batch" }
    require(batch.flow.all { it.isFinite() && (it * 60f).isFinite() } && batch.pressure.all { it.isFinite() }) { "Non-finite sample" }
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    val position = ParsePosition(0)
    val timestamp = requireNotNull(parser.parse(batch.timestamp, position)) { "Invalid sample timestamp" }.time
    require(position.index == batch.timestamp.length) { "Invalid sample timestamp suffix" }
    val start = origin ?: timestamp
    require(batch.metrics.values.all { it.size == batch.flow.size }) { "Incomplete metric batch" }
    fun metric(name: String, index: Int, scale: Float = 1f): Float? =
        batch.metrics[name]?.get(index)?.let { it * scale }?.takeIf { it.isFinite() }
    val samples = batch.flow.indices.map { i ->
        Sample(((timestamp - start + i.toLong() * batch.intervalMs) / 1000.0).toFloat(), batch.flow[i] * 60f, batch.pressure[i],
            metric("leak", i, 60f), metric("rate", i), metric("volume", i, 1000f), metric("ventilation", i), metric("therapyPressure", i), timestamp + i.toLong() * batch.intervalMs, source)
    }
    return start to samples
}
