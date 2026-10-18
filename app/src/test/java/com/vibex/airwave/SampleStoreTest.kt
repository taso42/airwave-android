package com.vibex.airwave

import com.vibex.airwave.ble.LiveReading
import org.junit.Assert.*
import org.junit.Test

class SampleStoreTest {
    private fun batch(time: String = "2026-10-16T00:00:00.000Z") =
        LiveReading(time, 40, listOf(.5f, 1f), listOf(5f, 6f))

    @Test fun retainsDeviceTimeAndIdentityAcrossGaps() {
        val store = SampleStore()
        val first = store.accept(batch(), "device-a").samples.first()
        val next = store.accept(batch("2026-10-16T00:00:01.000Z"), "device-a")
        assertFalse(next.timelineReset)
        assertEquals("2026-10-16T00:00:01.000Z", next.deviceStartTime)
        assertEquals(40, next.intervalMs)
        assertEquals(4, store.samples.size)
        assertEquals(1f, next.samples.first().seconds)
        assertEquals(first.deviceTimestampMs!! + 1000, next.samples.first().deviceTimestampMs)
        assertEquals(first.deviceTimestampMs + 1040, next.samples.last().deviceTimestampMs)
        assertEquals(SampleSource.Bluetooth("device-a"), next.samples.last().source)
        assertEquals(30f, first.flow)
    }

    @Test fun restartedClockClearsHistoryWithoutMutatingFrozenCopy() {
        val store = SampleStore()
        store.accept(batch(), "device-a")
        val frozen = store.samples.toList()
        store.accept(batch("2026-10-16T00:00:01.000Z"), "device-a")
        assertTrue(store.accept(batch(), "device-a").timelineReset)
        assertEquals(2, store.samples.size)
        assertEquals(2, frozen.size)
        assertEquals(0f, frozen.first().seconds)
    }

    @Test fun rejectsMalformedPacketWithoutChangingStoredSamples() {
        val store = SampleStore()
        store.accept(batch(), "device-a")
        val before = store.samples.toList()
        try {
            store.accept(batch().copy(pressure = emptyList()), "device-a")
            fail("Accepted invalid packet")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, store.samples)
    }

    @Test fun deviceSwitchAndClearSeparateSourcesAndOrigins() {
        val store = SampleStore()
        store.accept(batch(), "device-a")
        assertTrue(store.accept(batch("2026-10-16T00:00:01.000Z"), "device-b").timelineReset)
        assertEquals(2, store.samples.size)
        store.clear()
        val update = store.accept(batch("2026-10-16T01:00:00.000Z"), "device-b")
        assertFalse(update.timelineReset)
        assertEquals(0f, update.samples.first().seconds)
    }
}
