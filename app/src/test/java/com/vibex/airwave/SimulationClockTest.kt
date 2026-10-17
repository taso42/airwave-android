package com.vibex.airwave
import org.junit.Test
import org.junit.Assert.*
class SimulationClockTest {
    @Test fun pausedTimeCreatesGapWithoutBackfill() {
        val clock = SimulationClock(1000)
        assertEquals(listOf(60.04f), clock.advance(1040, true))
        assertTrue(clock.advance(11000, false).isEmpty())
        assertEquals(listOf(70.04f), clock.advance(11040, true))
    }
    @Test fun enabledBackgroundRemainsContinuousAndBounded() {
        val clock = SimulationClock(0)
        val samples = clock.advance(10000, true)
        assertEquals(250, samples.size)
        assertEquals(60.04f, samples.first())
        assertEquals(70f, samples.last())
        assertEquals(1501, clock.advance(3600000, true).size)
    }
}
