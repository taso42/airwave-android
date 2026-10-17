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

/** Preserve both channels' extrema and every outage boundary in each time bucket. */
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
        var flowMin = i; var flowMax = i; var pressureMin = i; var pressureMax = i
        i++
        while (i < source.size && source[i].seconds <= end &&
            source[i].seconds - source[i - 1].seconds <= .12f &&
            ((source[i].seconds - start) / (end - start) * buckets).toInt() == bucket) {
            if (source[i].flow < source[flowMin].flow) flowMin = i
            if (source[i].flow > source[flowMax].flow) flowMax = i
            if (source[i].pressure < source[pressureMin].pressure) pressureMin = i
            if (source[i].pressure > source[pressureMax].pressure) pressureMax = i
            i++
        }
        listOf(first, flowMin, flowMax, pressureMin, pressureMax, i - 1).distinct().sorted().forEachIndexed { n, index ->
            result.add(ChartPoint(source[index], newSegment && n == 0))
        }
        newSegment = i < source.size && source[i].seconds - source[i - 1].seconds > .12f
    }
    return result
}
