package com.vibex.airwave.history

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** All fixtures are synthetic; no device captures, identifiers, or credentials. */
class ArchivedSignalTest {
    private fun varint(number: Long): ByteArray {
        var value = number
        val out = mutableListOf<Byte>()
        do {
            val part = (value and 127).toInt(); value = value ushr 7
            out += (part or if (value != 0L) 128 else 0).toByte()
        } while (value != 0L)
        return out.toByteArray()
    }
    private fun scalar(field: Int, value: Long) = varint((field * 8).toLong()) + varint(value)
    private fun bytes(field: Int, value: ByteArray) = varint((field * 8 + 2).toLong()) + varint(value.size.toLong()) + value
    private fun zigzag(value: Long) = (value shl 1) xor (value shr 63)
    private fun fixture(channel: ArchivedChannel, start: Long = 10_000_000, samples: List<Int> = listOf(-10, 0, 12, 9), modulus: Int = 4): ByteArray {
        val parameters = listOf(2L, -2, -32768, 32767, modulus.toLong(), 1)
        val header = "RC03".toByteArray() + parameters.flatMap { varint(zigzag(it)).asIterable() }.toByteArray()
        val seeds = samples.take(2).flatMap { listOf(it.toByte(), (it shr 8).toByte()) }.toByteArray()
        val bits = mutableListOf<Int>()
        samples.indices.drop(2).forEach { index ->
            val code = zigzag((samples[index] - 2 * samples[index - 1] + samples[index - 2]).toLong()).toInt()
            repeat(code / modulus) { bits += 1 }; bits += 0
            for (shift in Integer.numberOfTrailingZeros(modulus) - 1 downTo 0) bits += (code shr shift) and 1
        }
        while (bits.size % 8 != 0) bits += 0
        val body = seeds + bits.chunked(8).map { it.fold(0) { byte, bit -> (byte shl 1) or bit }.toByte() }.toByteArray()
        val block = byteArrayOf(header.size.toByte()) + header + body
        val payload = scalar(1, channel.intervalMs.toLong()) + scalar(2, start) + scalar(3, start + (samples.size - 1L) * channel.intervalMs) + bytes(4, block)
        return bytes(channel.field, scalar(1, 1) + bytes(2, payload))
    }
    private fun round(data: ByteArray = byteArrayOf(), more: Boolean = false, next: Long? = null) = HistoryRound(
        if (data.isEmpty()) emptyList() else listOf(0 to data), MessageDigest.getInstance("SHA-256").digest(data), more, next)
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun decodesEachSupportedCadenceWithOriginalTimestampsAndUnits() {
        ArchivedChannel.entries.forEach { channel ->
            val record = ArchivedSignalDecoder.decode(channel, fixture(channel)).single()
            assertEquals(channel.intervalMs, record.intervalMs)
            assertEquals(10_000_000L + channel.intervalMs * 3, record.timeAt(3))
            assertArrayEquals(floatArrayOf(-.2f, 0f, .24f, .18f).map { it * channel.unitScale }.toFloatArray(), record.values.toFloatArray(), .0001f)
        }
    }
    @Test fun handlesSingleSeedAndMultipleRecordsWithoutJoiningTheirGap() {
        val channel = ArchivedChannel.FLOW
        val records = ArchivedSignalDecoder.decode(channel, fixture(channel, samples = listOf(1)) + fixture(channel, start = 10_100_000))
        assertEquals(2, records.size)
        assertEquals(1, records.first().values.size)
        assertEquals(100_000L, records.last().startMs - records.first().startMs)
    }
    @Test fun rejectsTruncatedWrongChannelAndOversizedRounds() {
        val data = fixture(ArchivedChannel.FLOW)
        rejects { ArchivedSignalDecoder.decode(ArchivedChannel.LEAK, data) }
        for (length in 1 until data.size) rejects { ArchivedSignalDecoder.decode(ArchivedChannel.FLOW, data.copyOf(length)) }
        rejects { ArchivedSignalDecoder.decode(ArchivedChannel.FLOW, ByteArray(4097)) }
        assertTrue(ArchivedSignalDecoder.decode(ArchivedChannel.FLOW, byteArrayOf()).isEmpty())
    }
    @Test fun checksSequenceAndChecksumBeforeDecode() {
        val data = fixture(ArchivedChannel.FLOW)
        val valid = round(data)
        assertArrayEquals(data, valid.verifiedBytes())
        rejects { valid.copy(fragments = listOf(1 to data)).verifiedBytes() }
        rejects { valid.copy(fragments = listOf(0 to data, 0 to data)).verifiedBytes() }
        rejects { valid.copy(sha256 = ByteArray(32)).verifiedBytes() }
    }
    @Test fun trimsToRequestedHourAndStopsAtSnapshotEnd() = runBlocking {
        val end = 10_000_000L
        val records = mutableListOf<ArchivedSignal>()
        val loader = HistoryBackfill { channel, from ->
            round(fixture(channel, from - channel.intervalMs, listOf(1, 2, 3)) + fixture(channel, end - channel.intervalMs, listOf(4, 5, 6)), true, end)
        }
        val results = loader.download(end, records::add)
        assertTrue(results.all { it.outcome == BackfillOutcome.COMPLETE })
        assertEquals(6, records.size)
        records.forEach { record -> record.values.indices.forEach { assertTrue(record.timeAt(it) in end - 3_600_000 until end) } }
        assertEquals(listOf(2, 1, 2, 1, 2, 1), records.map { it.values.size })
    }
    @Test fun retainsVerifiedPartialAndRejectsRepeatedCursor() = runBlocking {
        val records = mutableListOf<ArchivedSignal>()
        var calls = 0
        val loader = HistoryBackfill { channel, from ->
            calls++
            if (calls == 1) round(fixture(channel, from), true, from + 1000)
            else round(more = true, next = from)
        }
        val results = loader.download(10_000_000, records::add)
        assertEquals(1, records.size)
        assertEquals(1, results.first().verifiedRounds)
        assertTrue(results.all { it.outcome == BackfillOutcome.FAILED })
    }
    @Test fun emptyHistoryCompletesAndRoundLimitBoundsMaliciousContinuation() = runBlocking {
        assertTrue(HistoryBackfill { _, _ -> round() }.download(10_000_000) {}.all { it.outcome == BackfillOutcome.COMPLETE })
        var calls = 0
        val results = HistoryBackfill { _, from -> calls++; round(more = true, next = from + 1) }.download(10_000_000) {}
        assertEquals(192, calls)
        assertTrue(results.all { it.outcome == BackfillOutcome.PARTIAL_LIMIT })
    }
    @Test fun cancellationStopsDownloadsAndReleasesSingleDownloadGuard() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var block = true
        var calls = 0
        val loader = HistoryBackfill { _, _ -> calls++; if (block) { entered.complete(Unit); awaitCancellation() }; round() }
        val job = launch { loader.download(10_000_000) { fail("No result expected") } }
        entered.await()
        assertTrue(loader.download(10_000_000) {}.all { it.outcome == BackfillOutcome.ALREADY_RUNNING })
        job.cancelAndJoin()
        assertEquals(1, calls)
        block = false
        assertTrue(loader.download(10_000_000) {}.all { it.outcome == BackfillOutcome.COMPLETE })
    }
}
