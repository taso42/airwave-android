package com.vibex.airwave

internal class ChartClock {
    private var anchorMs: Long? = null
    private var anchorSeconds = 0f
    fun reset(seconds: Float, nowMs: Long) { anchorSeconds=seconds;anchorMs=nowMs }
    fun end(nowMs: Long): Float = anchorSeconds + (anchorMs?.let { (nowMs-it).coerceAtLeast(0)/1000f } ?: 0f)
    fun observe(seconds: Float, nowMs: Long) { reset(maxOf(end(nowMs), seconds), nowMs) }
    fun clear() { anchorMs=null;anchorSeconds=0f }
}
