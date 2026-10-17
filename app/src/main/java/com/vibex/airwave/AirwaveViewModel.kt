package com.vibex.airwave

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import com.vibex.airwave.ble.AirSenseClient
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

internal data class Sample(val seconds: Float, val flow: Float, val pressure: Float, val leak: Float? = null, val respiratoryRate: Float? = null, val tidalVolume: Float? = null, val minuteVentilation: Float? = null, val therapyPressure: Float? = null)
internal class AirwaveViewModel(application: Application): AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("settings", 0)
    var keepStreamingInBackground by mutableStateOf(preferences.getBoolean("backgroundStreaming", false))
        private set
    private var foreground = true
    private var resumeDevice: com.vibex.airwave.ble.Nearby? = null
    private var serviceRunning = false
    private var simulationClock: SimulationClock? = null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val buffer = ChartBuffer()
    var chartRevision by mutableIntStateOf(0)
        private set
    var samples: List<Sample>
        get() = buffer.toList()
        set(value) { buffer.clear(); value.forEach(buffer::append); chartRevision++ }
    val liveTherapyPressure: Float? get() { chartRevision; return buffer.lastOrNull()?.therapyPressure }
    fun chartSnapshot(): List<ChartPoint> {
        chartRevision // Observe throttled refresh, including outages.
        return chartPoints(frozen ?: buffer, chartEnd - window, chartEnd)
    }
    private val chartClock = ChartClock()
    private var frozenSamples by mutableStateOf<List<Sample>?>(null)
    private var frozenEnd by mutableFloatStateOf(0f)
    var frozen: List<Sample>?
        get() = frozenSamples
        set(value) {
            if (value != null && frozenSamples == null) frozenEnd = chartClock.end(android.os.SystemClock.elapsedRealtime())
            frozenSamples = value
        }
    val chartEnd: Float get() = if (frozen != null) frozenEnd else chartClock.end(now)
    var window by mutableIntStateOf(30)
    var demo by mutableStateOf(false)
    var sampleRateHz by mutableIntStateOf(25)
    var firstTimestamp:Long?=null
    var lastPacket by mutableLongStateOf(0L)
    var now by mutableLongStateOf(0L)
    val client=AirSenseClient(application,scope) { batch ->
        if (demo) return@AirSenseClient
        sampleRateHz = if (batch.intervalMs > 0) 1000 / batch.intervalMs else 25
        val (origin, incoming) = decodeSamples(batch, firstTimestamp)
        firstTimestamp = origin
        val base = incoming.first().seconds
        // A restarted device clock must not splice new samples into older timestamps.
        if(buffer.lastOrNull()?.seconds?.let { base<=it }==true) { samples=emptyList();frozen=null;chartClock.clear() }
        incoming.forEach(buffer::append)
        lastPacket=android.os.SystemClock.elapsedRealtime()
        now=lastPacket
        chartClock.observe(incoming.last().seconds, now)
    }
    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(client.connecting, client.streaming) { connecting, streaming -> connecting || streaming }
                .collect { active -> updateService(active) }
        }
        scope.launch { while(isActive) {
            delay(40)
            now=android.os.SystemClock.elapsedRealtime()
            if (demo) {
                val incoming = simulationClock?.advance(now, foreground || (keepStreamingInBackground && serviceRunning)).orEmpty().map(::simulated)
                incoming.forEach(buffer::append)
            }
        } }
        scope.launch { while (isActive) { delay(250); chartRevision++ } }
    }
    fun setBackgroundStreaming(enabled: Boolean) {
        keepStreamingInBackground = enabled
        preferences.edit().putBoolean("backgroundStreaming", enabled).apply()
        updateService(client.connecting.value || client.streaming.value)
    }
    private fun updateService(bluetoothActive: Boolean) {
        val active = bluetoothActive || demo
        val application = getApplication<Application>()
        if (keepStreamingInBackground && active && !serviceRunning && foreground) {
            try {
                StreamingService.start(application, demo) { serviceRunning=false;disconnectStream() }
                serviceRunning = true
            } catch (e: Exception) {
                client.status.value = "Background streaming unavailable: ${e.message}"
            }
        } else if ((!keepStreamingInBackground || !active) && serviceRunning) stopService()
    }
    private fun stopService() {
        StreamingService.stop()
        serviceRunning = false
    }
    fun onBackground() {
        simulationClock?.advance(android.os.SystemClock.elapsedRealtime(), false)
        foreground = false
        if (!keepStreamingInBackground || !serviceRunning) {
            resumeDevice = if (client.streaming.value || client.connecting.value) client.activeDevice.value else null
            client.disconnect()
            lastPacket = 0
        } else if (client.scanning.value) client.disconnect()
    }
    fun onForeground() {
        if (!foreground && !(keepStreamingInBackground && serviceRunning)) {
            simulationClock?.advance(android.os.SystemClock.elapsedRealtime(), false)
        }
        foreground = true
        val device = resumeDevice
        resumeDevice = null
        if (device != null && !demo) client.connect(device)
        updateService(client.connecting.value || client.streaming.value)
    }
    fun disconnectStream() {
        resumeDevice = null
        client.disconnect()
        demo = false
        simulationClock = null
        stopService()
    }
    fun prepareBluetooth(device: com.vibex.airwave.ble.Nearby? = null) {
        val switchingDevice = device != null && client.activeDevice.value?.let { it.address != device.address } == true
        if (demo || switchingDevice) useSimulation(false)
    }
    fun useSimulation(enabled: Boolean) {
        stopService()
        resumeDevice = null
        client.disconnect()
        clearDisplay()
        demo = enabled
        if (enabled) {
            now = android.os.SystemClock.elapsedRealtime()
            simulationClock = SimulationClock(now)
            chartClock.reset(60f, now)
            samples = (0..1500).map { simulated(it * .04f) }
        }
        updateService(false)
    }
    fun clearDisplay() { chartClock.clear();demo=false;simulationClock=null;stopService();samples=emptyList();frozen=null;firstTimestamp=null;lastPacket=0 }
    override fun onCleared() { stopService();client.disconnect();scope.cancel() }
    private fun simulated(t:Float):Sample {
        val breath=sin(t*2f*PI.toFloat()/4.8f)
        return Sample(t,26f*breath+2f*sin(t*5f),8f+1.5f*tanh(breath*3f), 2f+sin(t/9f), 12.5f, 500f+30f*sin(t/8f), 6.25f, 9.5f)
    }
}
