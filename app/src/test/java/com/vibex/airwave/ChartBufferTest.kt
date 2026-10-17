package com.vibex.airwave

import org.junit.Assert.*
import org.junit.Test

class ChartBufferTest {
    @Test fun preservesMetricPeaksAndNullBoundariesInOneBucket() {
        val raw = (0..99).map { i -> Sample(i*.04f, 0f, 5f,
            leak=if(i in 40..50) null else if(i==70) 77f else 0f,
            respiratoryRate=if(i==20) 33f else 12f,
            tidalVolume=if(i==25) 900f else 500f,
            minuteVentilation=if(i==30) 15f else 6f) }
        val points=chartPoints(raw,0f,3600f,1)
        assertEquals(77f,points.mapNotNull { it.sample.leak }.max(),0f)
        assertEquals(33f,points.mapNotNull { it.sample.respiratoryRate }.max(),0f)
        assertEquals(900f,points.mapNotNull { it.sample.tidalVolume }.max(),0f)
        assertEquals(15f,points.mapNotNull { it.sample.minuteVentilation }.max(),0f)
        for(i in listOf(39,40,50,51)) assertTrue(points.any { it.sample==raw[i] })
        assertEquals(1,points.count { it.startsSegment })
    }
    @Test fun retainsOnlyAnHourAndSnapshotsAreIndependent() {
        val buffer = ChartBuffer()
        repeat(100_000) { buffer.append(Sample(it * .04f, 0f, 0f)) }
        assertTrue(buffer.size <= 90_001)
        assertTrue(buffer.last().seconds - buffer.first().seconds <= 3600f)
        val snapshot = buffer.toList()
        buffer.append(Sample(8000f, 1f, 2f))
        assertEquals(1, buffer.size)
        assertEquals(99999 * .04f, snapshot.last().seconds, .001f)
    }
    @Test fun preservesOutageInsideOneBucket() {
        val source = listOf(Sample(0f, 0f, 0f), Sample(.04f, 1f, 2f), Sample(3f, 2f, 3f), Sample(3.04f, 3f, 4f))
        val points = chartPoints(source, 0f, 3600f)
        assertEquals(2, points.count { it.startsSegment })
        assertEquals(source, points.map { it.sample })
    }
    @Test fun reductionPreservesPeaksAndGapsWithoutInventingGaps() {
        val raw = (0..999).map { Sample(it * .04f, if (it == 400) 99f else 0f, if (it == 600) -9f else 5f) } +
            (2000..2999).map { Sample(it * .04f, 0f, 5f) }
        val points = chartPoints(raw, 0f, 120f, 10)
        assertTrue(points.size < 100)
        assertEquals(99f, points.maxOf { it.sample.flow }, 0f)
        assertEquals(-9f, points.minOf { it.sample.pressure }, 0f)
        assertEquals(raw.last(), points.last().sample)
        assertEquals(2, points.count { it.startsSegment })
        assertTrue(points.zipWithNext().all { (a,b) -> a.sample.seconds < b.sample.seconds })
        assertTrue(chartPoints(raw, 200f, 300f).isEmpty())
    }
}
