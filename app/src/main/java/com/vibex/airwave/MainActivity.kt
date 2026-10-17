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

private val Background = Color(0xFF0D1821)
private val Panel = Color(0xFF172630)
private val Mint = Color(0xFF68DCC1)
private val Blue = Color(0xFF83BDF5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val model=androidx.lifecycle.ViewModelProvider(this)[AirwaveViewModel::class.java]
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Panel)) { Dashboard(model) } }
    }
}

@Composable
private fun Dashboard(model: AirwaveViewModel) {
    val client=model.client
    val status by client.status.collectAsState()
    val devices by client.devices.collectAsState()
    val needsCode by client.needsCode.collectAsState()
    val saved by client.savedDevice.collectAsState()
    var reconnectRequested by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if(granted.values.all { it }) { if(reconnectRequested)client.reconnectSaved() else client.scan() } else client.status.value="Bluetooth permissions denied. Tap Scan to retry."
    }
    val displayed = model.frozen ?: model.samples
    val last = displayed.lastOrNull() ?: Sample(0f,0f,0f)
    Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("AIRWAVE", color = Mint, fontSize = 14.sp, letterSpacing = 3.sp)
        Surface(color = Panel, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if(model.demo) "DEMO • Simulated data" else status, color = Mint)
                Text(if(model.demo) "No CPAP connected. Illustrative waveforms at 25 samples/sec." else "Live only • Pairing remembered securely", style = MaterialTheme.typography.bodySmall)
                if(!model.demo && model.lastPacket > 0 && model.now-model.lastPacket > 2000) Text("No recent samples • Display is stale", color=Color(0xFFFFC980))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={
                        model.demo=false; model.samples=emptyList(); model.frozen=null; model.firstTimestamp=null;model.lastPacket=0
                        reconnectRequested=false
                        permissions.launch(if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text("Scan") }
                    OutlinedButton(onClick={ client.disconnect();model.demo=false }) { Text("Disconnect") }
                }
                if(!model.demo) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled=saved!=null,onClick={
                        model.demo=false;reconnectRequested=true
                        permissions.launch(if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text("Reconnect") }
                    TextButton(onClick={ client.forget();model.clearDisplay() }) { Text("Forget pairing") }
                }
                if(!model.demo && !needsCode) devices.forEach { device ->
                    TextButton(onClick={ model.samples=emptyList();model.frozen=null;model.firstTimestamp=null;model.lastPacket=0;client.connect(device) }) { Text(device.name) }
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
            FilterChip(selected = model.window == 30, onClick = { model.window = 30 }, label = { Text("30 sec") })
            FilterChip(selected = model.window == 60, onClick = { model.window = 60 }, label = { Text("60 sec") })
            OutlinedButton(onClick = { model.frozen = if (model.frozen == null) model.samples.toList() else null }) {
                Text(if (model.frozen == null) "Freeze view" else "Resume")
            }
        }
        if (model.frozen != null) Text("View frozen • Incoming sampling continues", color = Mint, style = MaterialTheme.typography.bodySmall)
        WaveChart("Flow", "L/min", displayed, model.window, -40f, 40f, Mint) { it.flow }
        WaveChart("Mask pressure", "cmH₂O", displayed, model.window, 4f, 12f, Blue) { it.pressure }
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
