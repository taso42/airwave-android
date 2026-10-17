package com.vibex.airwave.ble

import org.json.JSONObject

internal fun decodeStreamData(params: JSONObject): LiveReading {
    val data = params.getJSONArray("data")
    val metrics = mutableMapOf<String, List<Float?>>()
    var flow: List<Float>? = null
    var pressure: List<Float>? = null
    for (i in 0 until data.length()) {
        val item = data.getJSONObject(i)
        fun values(key: String) = item.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getDouble(it).toFloat() }
        }
        for ((wire, name) in mapOf("Leak" to "leak", "_LKF" to "leak", "_RR2" to "rate", "_TD2" to "volume", "_MV2" to "ventilation", "InspiratoryPressure" to "therapyPressure", "_INP" to "therapyPressure")) {
            if (item.has(wire)) {
                require(name !in metrics) { "Duplicate metric channel" }
                val array = item.getJSONArray(wire)
                metrics[name] = (0 until array.length()).map { index ->
                    if (array.isNull(index)) null else array.getDouble(index).toFloat().takeIf { it.isFinite() }
                }
            }
        }
        if (item.has("PatientFlow")) {
            require(flow == null) { "Duplicate flow channel" }
            flow = values("PatientFlow")
        }
        if (item.has("MaskPressure")) {
            require(pressure == null) { "Duplicate pressure channel" }
            pressure = values("MaskPressure")
        }
    }
    val batch = LiveReading(params.getString("startTime"), params.getInt("intervalMs"),
        requireNotNull(flow) { "Missing flow" }, requireNotNull(pressure) { "Missing pressure" }, metrics)
    com.vibex.airwave.decodeSamples(batch, null)
    return batch
}
