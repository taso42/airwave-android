package com.vibex.airwave

import com.vibex.airwave.ble.LiveReading
import org.junit.Assert.*
import org.junit.Test

class LiveSamplesTest {
    private fun batch(time: String = "2026-10-16T00:00:00.000Z", interval: Int = 40) =
        LiveReading(time, interval, listOf(-.5f, 0f, .5f), listOf(4f, 8f, 12f))
    @Test fun convertsUnitsAndPreservesCadenceAndGaps() {
        val (origin, samples) = decodeSamples(batch(), null)
        assertEquals(listOf(-30f, 0f, 30f), samples.map { it.flow })
        assertEquals(listOf(4f, 8f, 12f), samples.map { it.pressure })
        assertEquals(listOf(0f, .04f, .08f), samples.map { it.seconds })
        val next = decodeSamples(batch("2026-10-16T00:00:01.000Z"), origin).second
        assertEquals(1f, next.first().seconds)
    }
    @Test fun convertsMetricsWithoutTurningUnavailableIntoZero() {
        val input = batch().copy(metrics = mapOf(
            "leak" to listOf(null, 0f, .1f), "rate" to listOf(null, 0f, 12f),
            "volume" to listOf(null, 0f, .5f), "ventilation" to listOf(null, 0f, 6f)))
        val samples = decodeSamples(input, null).second
        assertNull(samples[0].leak)
        assertNull(samples[0].respiratoryRate)
        assertNull(samples[0].tidalVolume)
        assertNull(samples[0].minuteVentilation)
        assertEquals(0f, samples[1].leak!!, 0f)
        assertEquals(6f, samples[2].leak!!, .0001f)
        assertEquals(12f, samples[2].respiratoryRate!!, 0f)
        assertEquals(500f, samples[2].tidalVolume!!, .0001f)
        assertEquals(6f, samples[2].minuteVentilation!!, 0f)
        assertNull(decodeSamples(batch(), null).second.last().leak)
    }
    @Test fun rejectsMalformedBatches() {
        val bad = listOf(batch().copy(metrics=mapOf("leak" to listOf(1f))), batch(interval=0), batch(interval=-1), batch().copy(pressure=emptyList()),
            batch().copy(flow=listOf(Float.NaN, 0f, 1f)), batch("2026-02-30T00:00:00.000Z"),
            batch("2026-10-16T00:00:00.000Zjunk"))
        bad.forEach { input ->
            try { decodeSamples(input, null); fail("Accepted malformed batch") } catch (_: IllegalArgumentException) { }
        }
    }
}
