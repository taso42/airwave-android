package com.vibex.airwave

/** A bounded synthetic source on a monotonic 25 Hz timeline. */
internal class SimulationClock(private val originMs: Long) {
    private var lastTick = 1500L
    fun advance(nowMs: Long, sampling: Boolean): List<Float> {
        val tick = 1500L + (nowMs - originMs).coerceAtLeast(0) / 40
        if (tick <= lastTick) return emptyList()
        val start = maxOf(lastTick + 1, tick - 1500)
        lastTick = tick
        return if (sampling) (start..tick).map { it / 25f } else emptyList()
    }
}
