package com.vibex.airwave

import com.vibex.airwave.ble.decodeStreamData
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StreamDataTest {
    private val fixture = """{"startTime":"2026-10-16T00:00:00.000Z","intervalMs":40,"data":[{"MaskPressure":[4,8,12]},{"PatientFlow":[-0.5,0,0.5]}]}"""
    @Test fun decodesChannelsByNameAndConvertsSamples() {
        val batch = decodeStreamData(JSONObject(fixture))
        val samples = decodeSamples(batch, null).second
        assertEquals(3, samples.size)
        assertEquals(listOf(-30f,0f,30f), samples.map { it.flow })
        assertEquals(listOf(4f,8f,12f), samples.map { it.pressure })
        assertEquals(.08f, samples.last().seconds)
    }
    @Test fun decodesNullableMetricsAndWireAlias() {
        val params = JSONObject(fixture)
        params.getJSONArray("data")
            .put(JSONObject("""{"Leak":[null,0,0.1]}"""))
            .put(JSONObject("""{"_RR2":[null,0,12]}"""))
            .put(JSONObject("""{"_TD2":[null,0,0.5]}"""))
            .put(JSONObject("""{"_MV2":[null,0,6]}"""))
            .put(JSONObject("""{"InspiratoryPressure":[null,0,5]}"""))
        val samples = decodeSamples(decodeStreamData(params), null).second
        assertNull(samples.first().therapyPressure)
        assertEquals(0f, samples[1].therapyPressure!!, 0f)
        assertEquals(5f, samples.last().therapyPressure!!, 0f)
        assertNull(samples.first().leak)
        assertEquals(6f, samples.last().leak!!, .0001f)
        assertEquals(500f, samples.last().tidalVolume!!, .0001f)
        assertEquals(12f, samples.last().respiratoryRate!!, 0f)
        assertEquals(6f, samples.last().minuteVentilation!!, 0f)
    }
    @Test fun rejectsMissingAndDuplicateChannels() {
        for (key in listOf("UnknownChannel", "PatientFlow")) {
            try {
                decodeStreamData(JSONObject(fixture.replace("MaskPressure", key)))
                fail("Invalid channel set accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
