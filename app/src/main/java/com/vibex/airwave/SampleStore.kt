package com.vibex.airwave

import com.vibex.airwave.ble.LiveReading

internal sealed interface SampleSource {
    data class Bluetooth(val deviceId: String?) : SampleSource
    data object Simulation : SampleSource
    data object Unknown : SampleSource
}

/**
 * A normalized observation, independent of dashboard state.
 * seconds is relative chart time; deviceTimestampMs preserves UTC device time separately.
 * flow/leak: L/min; pressure/therapyPressure: cmH2O; respiratoryRate: breaths/min;
 * tidalVolume: mL; minuteVentilation: L/min. Null metrics mean unavailable, not zero.
 */
internal data class Sample(
    val seconds: Float,
    val flow: Float,
    val pressure: Float,
    val leak: Float? = null,
    val respiratoryRate: Float? = null,
    val tidalVolume: Float? = null,
    val minuteVentilation: Float? = null,
    val therapyPressure: Float? = null,
    val deviceTimestampMs: Long? = null,
    val source: SampleSource = SampleSource.Unknown,
)

internal data class SampleUpdate(
    val samples: List<Sample>,
    val timelineReset: Boolean,
    val deviceStartTime: String,
    val intervalMs: Int,
)

/** Owns decoding and bounded retention. Single-thread confined; contains no Android/UI state. */
internal class SampleStore {
    private val buffer = ChartBuffer()
    val samples: List<Sample> get() = buffer
    private var origin: Long? = null

    fun accept(batch: LiveReading, deviceId: String?): SampleUpdate {
        val (start, incoming) = decodeSamples(batch, origin, SampleSource.Bluetooth(deviceId))
        val reset = buffer.lastOrNull()?.let {
            incoming.first().seconds <= it.seconds || incoming.first().source != it.source
        } ?: false
        if (reset) buffer.clear()
        origin = start
        incoming.forEach(buffer::append)
        return SampleUpdate(incoming, reset, batch.timestamp, batch.intervalMs)
    }

    fun appendSimulation(incoming: List<Sample>) { incoming.forEach(buffer::append) }
    fun replace(samples: List<Sample>) {
        clear()
        samples.forEach(buffer::append)
    }
    fun clear() { buffer.clear(); origin = null }
}
