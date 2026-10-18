package com.vibex.airwave.history

import kotlin.math.pow

/** Offline foundation for #23. Not yet connected to the live Bluetooth client. */
internal enum class ArchivedChannel(val spool: String, val field: Int, val intervalMs: Int, val unitScale: Float) {
    FLOW("RespiratoryFlow6p25Hz", 18, 160, 60f),
    MASK_PRESSURE("MaskPressure6p25Hz", 19, 160, 1f),
    LEAK("Leak0p5Hz", 20, 2000, 60f),
}

/** One original archive block. Boundaries and original device timestamps are retained. */
internal data class ArchivedSignal(
    val channel: ArchivedChannel,
    val startMs: Long,
    val intervalMs: Int,
    val values: List<Float>,
) {
    fun timeAt(index: Int): Long = Math.addExact(startMs, index.toLong() * intervalMs)
}

/** Strict, bounded subset of DataDelivery protobuf and RC03, informed by airbreak-plus. */
internal object ArchivedSignalDecoder {
    const val MAX_ROUND_BYTES = 4096
    private const val MAX_SAMPLES = 25_000

    fun decode(channel: ArchivedChannel, bytes: ByteArray): List<ArchivedSignal> {
        require(bytes.size <= MAX_ROUND_BYTES) { "Archive round exceeds byte budget" }
        var total = 0
        return fields(bytes).map { outer ->
            require(outer.number == channel.field && outer.wire == 2) { "Unexpected archive channel" }
            val wrapper = fields(outer.bytes!!)
            val payload = fields(wrapper.single { it.number == 2 && it.wire == 2 }.bytes!!)
            fun integer(number: Int) = payload.single { it.number == number && it.wire == 0 }.value!!
            val interval = integer(1)
            val start = integer(2)
            val end = integer(3)
            require(interval == channel.intervalMs.toLong()) { "Unexpected archive cadence" }
            require(start >= 0 && end >= start) { "Invalid archive timestamps" }
            // Firmware endpoint may be rounded; upstream derives count using integer division.
            val count = (end - start) / interval + 1
            require(count in 1..MAX_SAMPLES.toLong() && total + count <= MAX_SAMPLES) { "Archive sample budget exceeded" }
            total += count.toInt()
            val block = payload.single { it.number == 4 && it.wire == 2 }.bytes!!
            ArchivedSignal(channel, start, interval.toInt(), rc03(block, count.toInt()).map {
                (it * channel.unitScale).also { value -> require(value.isFinite()) { "Non-finite archive value" } }
            })
        }
    }

    private data class Field(val number: Int, val wire: Int, val value: Long? = null, val bytes: ByteArray? = null)
    private class Reader(val data: ByteArray) {
        var offset = 0
        fun byte(): Int { require(offset < data.size) { "Truncated archive" }; return data[offset++].toInt() and 255 }
        fun take(count: Int): ByteArray {
            require(count >= 0 && count <= data.size - offset) { "Truncated archive field" }
            return data.copyOfRange(offset, offset + count).also { offset += count }
        }
        fun varint(): Long {
            var value = 0L
            // Only nonnegative signed Long values are supported: timestamps, lengths, and RC03 parameters.
            for (shift in 0..56 step 7) {
                val byte = byte()
                value = value or ((byte and 127).toLong() shl shift)
                if (byte and 128 == 0) return value
            }
            error("Archive varint overflow")
        }
    }
    private fun fields(bytes: ByteArray): List<Field> {
        val reader = Reader(bytes)
        val result = mutableListOf<Field>()
        while (reader.offset < bytes.size) {
            val tag = reader.varint()
            require(tag ushr 3 in 1..536870911) { "Invalid protobuf field" }
            val number = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            result += when (wire) {
                0 -> Field(number, wire, value = reader.varint())
                2 -> {
                    val length = reader.varint()
                    require(length <= bytes.size) { "Archive field too large" }
                    Field(number, wire, bytes = reader.take(length.toInt()))
                }
                1 -> Field(number, wire, bytes = reader.take(8))
                5 -> Field(number, wire, bytes = reader.take(4))
                else -> error("Unsupported protobuf wire type")
            }
        }
        return result
    }
    private fun zigzag(value: Long): Long = (value ushr 1) xor -(value and 1)
    private fun rc03(bytes: ByteArray, count: Int): List<Float> {
        val reader = Reader(bytes)
        val header = Reader(reader.take(reader.byte()))
        require(header.take(4).contentEquals("RC03".toByteArray(Charsets.US_ASCII))) { "Invalid RC03 header" }
        val parameters = mutableListOf<Long>()
        while (header.offset < header.data.size) parameters += zigzag(header.varint())
        require(parameters.size == 6) { "Unexpected RC03 format" }
        val modulus = parameters[4]
        require(modulus in 1..65536 && modulus and (modulus - 1) == 0L) { "Unsupported Rice modulus" }
        val scale = parameters[0] * 10.0.pow(parameters[1].toDouble())
        require(scale.isFinite() && scale > 0) { "Invalid RC03 scale" }
        val values = ArrayList<Long>(count)
        repeat(minOf(count, 2)) { values += (reader.byte() or (reader.byte() shl 8)).toShort().toLong() }
        var bitsRemaining = 0
        var currentByte = 0
        fun bit(): Int {
            if (bitsRemaining == 0) { currentByte = reader.byte(); bitsRemaining = 8 }
            return (currentByte shr --bitsRemaining) and 1
        }
        val remainderBits = java.lang.Long.numberOfTrailingZeros(modulus)
        while (values.size < count) {
            var quotient = 0L
            while (bit() == 1) quotient++
            var remainder = 0L
            repeat(remainderBits) { remainder = (remainder shl 1) or bit().toLong() }
            val delta = zigzag(quotient * modulus + remainder)
            val value = Math.addExact(Math.subtractExact(Math.multiplyExact(2, values.last()), values[values.lastIndex - 1]), delta)
            require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "RC03 sample outside int16 range" }
            values += value
        }
        return values.map { (it * scale).toFloat().also { value -> require(value.isFinite()) } }
    }
}
