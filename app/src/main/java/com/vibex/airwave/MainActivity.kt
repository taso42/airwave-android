package com.vibex.airwave

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.vibex.airwave.ble.AirSenseClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

private data class Sample(val seconds: Float, val flow: Float, val pressure: Float)
private val Background = Color(0xFF0D1821)
private val Panel = Color(0xFF172630)
private val Mint = Color(0xFF68DCC1)
private val Blue = Color(0xFF83BDF5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Panel)) { Dashboard() } }
    }
}

private fun simulatedSample(t: Float): Sample {
    val breath = sin(t * 2f * PI.toFloat() / 4.8f)
    return Sample(t, 26f * breath + 2f * sin(t * 5f), 8f + 1.5f * tanh(breath * 3f))
}

@Composable
private fun Dashboard() {
    var samples by remember { mutableStateOf((0..1500).map { simulatedSample(it * .04f) }) }
    var frozen by remember { mutableStateOf<List<Sample>?>(null) }
    var window by remember { mutableIntStateOf(30) }
    var demo by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var firstTimestamp by remember { mutableStateOf<Long?>(null) }
    var lastPacket by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val client = remember {
        AirSenseClient(context.applicationContext, scope) { batch ->
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC") }
            val timestamp = parser.parse(batch.timestamp)!!.time
            if(firstTimestamp == null) firstTimestamp = timestamp
            val base = (timestamp-firstTimestamp!!)/1000f
            val incoming = batch.flow.indices.map { i -> Sample(base+i*batch.intervalMs/1000f, batch.flow[i]*60f, batch.pressure[i]) }
            samples = (samples + incoming).takeLast(1501)
            lastPacket = android.os.SystemClock.elapsedRealtime()
        }
    }
    val status by client.status.collectAsState()
    val devices by client.devices.collectAsState()
    val needsCode by client.needsCode.collectAsState()
    var code by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if(granted.values.all { it }) client.scan() else client.status.value="Bluetooth permissions denied. Tap Scan to retry."
    }
    DisposableEffect(client) { onDispose { client.disconnect() } }
    LaunchedEffect(Unit) { while(true) { delay(500); now=android.os.SystemClock.elapsedRealtime() } }
    LaunchedEffect(demo) {
        while (demo) {
            delay(40)
            samples = (samples.takeLast(1500) + simulatedSample(samples.last().seconds + .04f))
        }
    }
    val displayed = frozen ?: samples
    val last = displayed.lastOrNull() ?: Sample(0f,0f,0f)
    Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("AIRWAVE", color = Mint, fontSize = 14.sp, letterSpacing = 3.sp)
        Surface(color = Panel, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if(demo) "DEMO • Simulated data" else status, color = Mint)
                Text(if(demo) "No CPAP connected. Illustrative waveforms at 25 samples/sec." else "Live only • Pairing lasts until disconnect", style = MaterialTheme.typography.bodySmall)
                if(!demo && lastPacket > 0 && now-lastPacket > 2000) Text("No recent samples • Display is stale", color=Color(0xFFFFC980))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={
                        demo=false; samples=emptyList(); frozen=null; firstTimestamp=null;lastPacket=0
                        permissions.launch(if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text("Scan") }
                    OutlinedButton(onClick={ client.disconnect();demo=false }) { Text("Disconnect") }
                }
                if(!demo && !needsCode) devices.forEach { device ->
                    TextButton(onClick={ samples=emptyList();frozen=null;firstTimestamp=null;lastPacket=0;client.connect(device) }) { Text(device.name) }
                }
                if(needsCode) {
                    OutlinedTextField(value=code,onValueChange={ code=it.filter(Char::isDigit).take(4) },label={Text("AirSense code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true)
                    Button(enabled=code.length==4,onClick={ client.pair(code);code="" }) { Text("Pair and stream") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Reading("Flow", if(displayed.isEmpty()) "—" else "%.1f".format(last.flow), "L/min", Mint, Modifier.weight(1f))
            Reading("Mask pressure", if(displayed.isEmpty()) "—" else "%.1f".format(last.pressure), "cmH₂O", Blue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = window == 30, onClick = { window = 30 }, label = { Text("30 sec") })
            FilterChip(selected = window == 60, onClick = { window = 60 }, label = { Text("60 sec") })
            OutlinedButton(onClick = { frozen = if (frozen == null) samples.toList() else null }) {
                Text(if (frozen == null) "Freeze view" else "Resume")
            }
        }
        if (frozen != null) Text("View frozen • Incoming sampling continues", color = Mint, style = MaterialTheme.typography.bodySmall)
        WaveChart("Flow", "L/min", displayed, window, -40f, 40f, Mint) { it.flow }
        WaveChart("Mask pressure", "cmH₂O", displayed, window, 4f, 12f, Blue) { it.pressure }
        Text("Both charts share a time window. Flow above zero represents inhalation; below zero represents exhalation.", color = Color(0xFFA5B7C4), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Reading(label: String, value: String, unit: String, tint: Color, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, color = tint, fontSize = 32.sp)
            Text(unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WaveChart(title: String, unit: String, samples: List<Sample>, window: Int,
                      minimum: Float, maximum: Float, tint: Color, value: (Sample) -> Float) {
    Surface(color = Panel, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$title · $unit", style = MaterialTheme.typography.titleMedium)
            Text("${maximum.toInt()}", color = Color.LightGray, fontSize = 11.sp)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                fun y(v: Float) = size.height * (1f - (v - minimum) / (maximum - minimum))
                for (i in 0..4) {
                    val level = size.height * i / 4
                    drawLine(Color.White.copy(alpha = .08f), Offset(0f, level), Offset(size.width, level))
                }
                if (minimum < 0) drawLine(Color.White.copy(alpha = .3f), Offset(0f, y(0f)), Offset(size.width, y(0f)))
                val end = samples.lastOrNull()?.seconds ?: 0f
                val path = Path()
                var first = true
                var previous: Float? = null
                samples.filter { it.seconds >= end - window }.forEach {
                    val x = size.width * (it.seconds - (end - window)) / window
                    if (first || previous?.let { p -> it.seconds-p > .12f } == true) { path.moveTo(x, y(value(it))); first = false } else path.lineTo(x, y(value(it)))
                    previous=it.seconds
                }
                drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
            Text("${minimum.toInt()}", color = Color.LightGray, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−${window}s", fontSize = 11.sp)
                Text("Latest shown", fontSize = 11.sp)
            }
        }
    }
}
