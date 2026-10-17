package com.vibex.airwave

/** Bounded, in-memory hour of live data. All access is on the main thread. */
internal class ChartBuffer : AbstractList<Sample>() {
    private val entries = arrayOfNulls<Sample>(90_001)
    private var head = 0
    override var size = 0
        private set
    override fun get(index: Int): Sample {
        require(index in 0 until size)
        return entries[(head + index) % entries.size]!!
    }
    fun append(sample: Sample) {
        while (size > 0 && (size == entries.size || sample.seconds - get(0).seconds > 3600f)) {
            entries[head] = null
            head = (head + 1) % entries.size
            size--
        }
        entries[(head + size) % entries.size] = sample
        size++
    }
    fun clear() { entries.fill(null); head = 0; size = 0 }
}

internal data class ChartPoint(val sample: Sample, val startsSegment: Boolean)

private val chartChannels: List<(Sample) -> Float?> = listOf(
    { it.flow }, { it.pressure }, { it.leak }, { it.respiratoryRate },
    { it.tidalVolume }, { it.minuteVentilation })

/** Preserve channel extrema, null transitions, and outage boundaries. */
internal fun chartPoints(source: List<Sample>, start: Float, end: Float, buckets: Int = 600): List<ChartPoint> {
    val result = ArrayList<ChartPoint>()
    var low = 0
    var high = source.size
    while (low < high) {
        val middle = (low + high) / 2
        if (source[middle].seconds < start) low = middle + 1 else high = middle
    }
    var i = low
    var newSegment = true
    while (i < source.size && source[i].seconds <= end) {
        val first = i
        val bucket = ((source[i].seconds - start) / (end - start) * buckets).toInt()
        val minima = IntArray(chartChannels.size) { i }; val maxima = minima.copyOf()
        i++
        while (i < source.size && source[i].seconds <= end &&
            source[i].seconds - source[i - 1].seconds <= .12f &&
            chartChannels.all { (it(source[i]) == null) == (it(source[i-1]) == null) } &&
            ((source[i].seconds - start) / (end - start) * buckets).toInt() == bucket) {
            chartChannels.forEachIndexed { channel, value ->
                val current = value(source[i])
                if (current != null) {
                    if (current < (value(source[minima[channel]]) ?: current)) minima[channel] = i
                    if (current > (value(source[maxima[channel]]) ?: current)) maxima[channel] = i
                }
            }
            i++
        }
        (listOf(first, i - 1) + minima.toList() + maxima.toList()).distinct().sorted().forEachIndexed { n, index ->
            result.add(ChartPoint(source[index], newSegment && n == 0))
        }
        newSegment = i < source.size && source[i].seconds - source[i - 1].seconds > .12f
    }
    return result
}
