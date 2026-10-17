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

internal data class Sample(val seconds: Float, val flow: Float, val pressure: Float)
internal class AirwaveViewModel(application: Application): AndroidViewModel(application) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    var samples by mutableStateOf<List<Sample>>(emptyList())
    var frozen by mutableStateOf<List<Sample>?>(null)
    var window by mutableIntStateOf(30)
    var demo by mutableStateOf(false)
    var sampleRateHz by mutableIntStateOf(25)
    var firstTimestamp:Long?=null
    var lastPacket by mutableLongStateOf(0L)
    var now by mutableLongStateOf(0L)
    val client=AirSenseClient(application,scope) { batch ->
        if (demo) return@AirSenseClient
        sampleRateHz = if (batch.intervalMs > 0) 1000 / batch.intervalMs else 25
        val parser=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC") }
        val timestamp=parser.parse(batch.timestamp)!!.time
        if(firstTimestamp==null)firstTimestamp=timestamp
        val base=(timestamp-firstTimestamp!!)/1000f
        val incoming=batch.flow.indices.map { i -> Sample(base+i*batch.intervalMs/1000f,batch.flow[i]*60f,batch.pressure[i]) }
        // A restarted device clock must not splice new samples into older timestamps.
        if(samples.lastOrNull()?.seconds?.let { base<=it }==true) { samples=emptyList();frozen=null }
        samples=(samples+incoming).takeLast(1501)
        lastPacket=android.os.SystemClock.elapsedRealtime()
    }
    init {
        scope.launch { while(isActive) {
            delay(40)
            now=android.os.SystemClock.elapsedRealtime()
            if(demo)samples=samples.takeLast(1500)+simulated((samples.lastOrNull()?.seconds ?: 0f)+.04f)
        } }
    }
    fun useSimulation(enabled: Boolean) {
        client.disconnect()
        clearDisplay()
        demo = enabled
        if (enabled) samples = (0..1500).map { simulated(it * .04f) }
    }
    fun clearDisplay() { demo=false;samples=emptyList();frozen=null;firstTimestamp=null;lastPacket=0 }
    override fun onCleared() { client.disconnect();scope.cancel() }
    private fun simulated(t:Float):Sample {
        val breath=sin(t*2f*PI.toFloat()/4.8f)
        return Sample(t,26f*breath+2f*sin(t*5f),8f+1.5f*tanh(breath*3f))
    }
}
