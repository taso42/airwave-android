package com.vibex.airwave

internal const val DISPLAY_REFRESH_MS = 250L

/** Published on the display clock, never by incoming sample packets. */
internal data class DisplayFrame(
    val nowMs: Long = 0,
    val lastPacketMs: Long = 0,
    val sampleRateHz: Int = 25,
    val chartEnd: Float = 0f,
    val therapyPressure: Float? = null,
)
