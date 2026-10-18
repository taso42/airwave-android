package com.vibex.airwave.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

/** Adapter contract only. No Android transport is installed until simultaneous stream/spool testing. */
internal fun interface HistoryTransport {
    suspend fun round(channel: ArchivedChannel, fromMs: Long): HistoryRound
}

internal data class HistoryRound(
    val fragments: List<Pair<Int, ByteArray>>,
    val sha256: ByteArray,
    val morePending: Boolean,
    val nextFromMs: Long? = null,
) {
    fun verifiedBytes(): ByteArray {
        require(fragments.size <= 32) { "Too many archive fragments" }
        val sorted = fragments.sortedBy { it.first }
        require(sorted.map { it.first } == sorted.indices.toList()) { "Duplicate or missing archive fragment" }
        require(sorted.sumOf { it.second.size.toLong() } <= ArchivedSignalDecoder.MAX_ROUND_BYTES) { "Archive round exceeds byte budget" }
        val bytes = sorted.flatMap { it.second.asIterable() }.toByteArray()
        require(sha256.size == 32 && MessageDigest.isEqual(sha256, MessageDigest.getInstance("SHA-256").digest(bytes))) { "Archive checksum mismatch" }
        return bytes
    }
}

internal enum class BackfillOutcome { COMPLETE, PARTIAL_LIMIT, FAILED, ALREADY_RUNNING }
internal data class ChannelBackfillResult(val channel: ArchivedChannel, val outcome: BackfillOutcome, val verifiedRounds: Int)

/**
 * Offline coordinator prototype. A snapshot end is supplied from device time, never phone wall time.
 * Results are per-channel original blocks, not fabricated paired live samples. Verified partial rounds
 * survive a later failure. Callback ownership/cancellation must be wired to a connection generation.
 */
internal class HistoryBackfill(private val transport: HistoryTransport) {
    private val active = Mutex()

    suspend fun download(endMs: Long, accept: (ArchivedSignal) -> Unit): List<ChannelBackfillResult> {
        require(endMs >= 3_600_000)
        if (!active.tryLock()) return ArchivedChannel.entries.map { ChannelBackfillResult(it, BackfillOutcome.ALREADY_RUNNING, 0) }
        try {
            val startMs = endMs - 3_600_000
            return ArchivedChannel.entries.map { channel ->
                var verified = 0
                var outcome = BackfillOutcome.PARTIAL_LIMIT
                try {
                    // Limits also apply to repeated/empty continuation rounds.
                    withTimeout(60_000) {
                        var cursor = startMs
                        repeat(64) {
                            currentCoroutineContext().ensureActive()
                            val round = transport.round(channel, cursor)
                            currentCoroutineContext().ensureActive()
                            val records = ArchivedSignalDecoder.decode(channel, round.verifiedBytes())
                            if (round.morePending) require(round.nextFromMs != null && round.nextFromMs > cursor) { "Archive cursor did not advance" }
                            records.forEach { record ->
                                val indices = record.values.indices.filter { record.timeAt(it) in startMs until endMs }
                                if (indices.isNotEmpty()) accept(record.copy(startMs = record.timeAt(indices.first()), values = indices.map { record.values[it] }))
                            }
                            verified++
                            if (!round.morePending || round.nextFromMs!! >= endMs) {
                                outcome = BackfillOutcome.COMPLETE
                                return@withTimeout
                            }
                            cursor = round.nextFromMs
                        }
                    }
                } catch (e: CancellationException) {
                    // Parent cancellation/disconnect must stop the entire download, not try another channel.
                    // TimeoutCancellationException is also propagated; caller keeps verified partial results.
                    throw e
                } catch (_: Exception) {
                    outcome = BackfillOutcome.FAILED
                }
                ChannelBackfillResult(channel, outcome, verified)
            }
        } finally { active.unlock() }
    }
}
