package com.vibex.airwave
import org.junit.Test
import org.junit.Assert.*
class ChartClockTest {
    @Test fun outageScrollsSamplesOutOfWindow() {
        val clock=ChartClock();clock.reset(60f,1000)
        assertEquals(75f,clock.end(16000),.001f)
        assertTrue(60f < clock.end(62000)-60f)
    }
    @Test fun resumptionDoesNotRewindAxis() {
        val clock=ChartClock();clock.reset(60f,1000)
        clock.observe(74.8f,16000)
        assertEquals(75f,clock.end(16000),.001f)
        assertEquals(76f,clock.end(17000),.001f)
    }
    @Test fun resetStartsIndependentSource() {
        val clock=ChartClock();clock.reset(60f,1000);clock.clear()
        assertEquals(0f,clock.end(5000),.001f)
        clock.reset(0f,5000)
        assertEquals(1f,clock.end(6000),.001f)
    }
}
