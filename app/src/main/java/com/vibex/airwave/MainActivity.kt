package com.vibex.airwave

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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

private val Background = Color(0xFF0D1821)
private val Panel = Color(0xFF172630)
private val Mint = Color(0xFF68DCC1)
private val Blue = Color(0xFF83BDF5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val model=androidx.lifecycle.ViewModelProvider(this)[AirwaveViewModel::class.java]
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Panel)) { AirwaveApp(model) } }
    }
}

@Composable
private fun AirwaveApp(model: AirwaveViewModel) {
    var connections by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = connections) { connections = false }
    if (connections) Connections(model, onBack = { connections = false })
    else Dashboard(model, onConnections = { connections = true })
}

@Composable
private fun Connections(model: AirwaveViewModel, onBack: () -> Unit) {
    val client=model.client
    val status by client.status.collectAsState()
    val devices by client.devices.collectAsState()
    val needsCode by client.needsCode.collectAsState()
    val saved by client.savedDevice.collectAsState()
    val connecting by client.connecting.collectAsState()
    val streaming by client.streaming.collectAsState()
    val scanning by client.scanning.collectAsState()
    val active by client.activeDevice.collectAsState()
    var finding by rememberSaveable { mutableStateOf(false) }
    var entered by rememberSaveable { mutableStateOf(false) }
    var reconnectRequested by rememberSaveable { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if(granted.values.all { it }) { if(reconnectRequested)client.reconnectSaved() else client.scan() } else client.status.value="Bluetooth permissions denied. Try again to grant access."
    }
    fun requestBluetooth(reconnect: Boolean) {
        model.useSimulation(false)
        reconnectRequested = reconnect
        finding = !reconnect
        permissions.launch(if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) {
        if (!entered) {
            entered = true
            if (saved == null && !model.demo && !connecting && !streaming && !scanning) requestBluetooth(false)
        }
    }
    Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 2.dp.toPx()
                drawLine(Blue, Offset(size.width * .85f, size.height * .5f), Offset(size.width * .15f, size.height * .5f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Blue, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .45f, size.height * .2f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Blue, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .45f, size.height * .8f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Spacer(Modifier.width(8.dp))
            Text("Back to charts", color = Blue)
        }
        Text("Connections", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium)
        Surface(color = Panel, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (connecting || streaming) active?.name ?: "AirSense" else saved?.name ?: "Find your AirSense",
                    style = MaterialTheme.typography.titleMedium)
                Text(status, color = Mint)
                when {
                    needsCode -> {
                        OutlinedTextField(value=code,onValueChange={ code=it.filter(Char::isDigit).take(4) },label={Text("AirSense code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true)
                        Button(enabled=code.length==4,onClick={ client.pair(code);code="" }) { Text("Pair and connect") }
                        TextButton(onClick={ client.disconnect() }) { Text("Cancel") }
                    }
                    connecting -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick={ client.disconnect() }) { Text("Cancel") }
                    }
                    streaming -> {
                        Button(onClick={ client.disconnect();model.clearDisplay() }) { Text("Disconnect") }
                    }
                    scanning || finding || saved == null -> {
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (devices.isNotEmpty()) Text("Nearby devices · Select to connect", style = MaterialTheme.typography.labelLarge)
                        devices.forEach { device ->
                            Surface(
                                onClick = { finding=false;model.useSimulation(false);client.connect(device) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                color = Color(0xFF263E4C),
                                contentColor = Color.White,
                                shape = MaterialTheme.shapes.medium,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Blue.copy(alpha = .55f))
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("ResMed device", style = MaterialTheme.typography.titleMedium)
                                        Text(device.name, color = Color(0xFFCAD9E3), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("Connect ›", color = Blue, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (scanning) TextButton(onClick={ client.disconnect();finding=false }) { Text("Cancel") }
                        else Button(onClick={ requestBluetooth(false) }) { Text("Search again") }
                        if (saved != null && !scanning) TextButton(onClick={ finding=false }) { Text("Back to saved device") }
                    }
                    else -> Button(onClick={ requestBluetooth(true) }) { Text("Connect") }
                }
                if (saved != null && !connecting && !streaming && !scanning && !finding) {
                    TextButton(onClick={ requestBluetooth(false) }) { Text("Find another device") }
                    TextButton(onClick={ client.forget();model.clearDisplay();requestBluetooth(false) }) { Text("Forget device") }
                }
            }
        }
        Surface(color = Panel, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Simulated data")
                Switch(checked = model.demo, onCheckedChange = { model.useSimulation(it) })
            }
        }
    }
}

@Composable
private fun Dashboard(model: AirwaveViewModel, onConnections: () -> Unit) {
    val streaming by model.client.streaming.collectAsState()
    val live = streaming && model.lastPacket > 0 && model.now - model.lastPacket <= 2000
    val displayed = model.frozen ?: model.samples
    val last = displayed.lastOrNull() ?: Sample(0f,0f,0f)
    Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("AIRWAVE", color = Mint, fontSize = 14.sp, letterSpacing = 3.sp)
        val statusColor = if (model.demo || live) Mint else Color(0xFFFF6B6B)
        Surface(
            onClick = onConnections,
            color = Panel,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = .3f))
        ) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size(8.dp)) { drawCircle(statusColor) }
                Text(when {
                    model.demo -> "Simulated - 25Hz"
                    live -> "Live - ${model.sampleRateHz}Hz"
                    else -> "Disconnected"
                }, color = statusColor, style = MaterialTheme.typography.labelLarge)
                Canvas(Modifier.size(16.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(statusColor, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(statusColor, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
