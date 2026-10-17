@file:Suppress("DEPRECATION", "MissingPermission")
package com.vibex.airwave.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

 data class Nearby(val address: String, val name: String)
 data class LiveReading(val timestamp: String, val intervalMs: Int, val flow: List<Float>, val pressure: List<Float>)

class AirSenseClient(private val context: Context, private val scope: CoroutineScope, private val onSamples: (LiveReading) -> Unit) {
    val status = MutableStateFlow("Not connected")
    val devices = MutableStateFlow<List<Nearby>>(emptyList())
    val needsCode = MutableStateFlow(false)
    val streaming = MutableStateFlow(false)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var session: ByteArray? = null
    private var decoder = FigProtocol.Decoder()
    private var ready = CompletableDeferred<Unit>()
    private var writeAck = Channel<Int>(Channel.UNLIMITED)
    private val replies = mutableMapOf<Int,CompletableDeferred<JSONObject>>()
    private var nextId=0
    private var work: Job? = null
    private var scanJob: Job? = null
    private var pairing: FigProtocol.Pairing? = null
    private var exchange: JSONObject? = null
    private val service = UUID.fromString("0000fd56-0000-1000-8000-00805f9b34fb")
    private val txId = UUID.fromString("a6220002-35f1-4b20-afae-cb089d2044aa")
    private val rxId = UUID.fromString("a6220003-35f1-4b20-afae-cb089d2044aa")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { scope.launch {
            val d=Nearby(result.device.address,result.scanRecord?.deviceName ?: "ResMed")
            devices.value=(devices.value.filterNot { it.address==d.address }+d)
        } }
        override fun onScanFailed(errorCode: Int) { status.value="Scan failed ($errorCode)" }
    }
    fun scan() {
        disconnect()
        try {
            check(adapter?.isEnabled == true) { "Enable Bluetooth on your phone" }
            devices.value=emptyList(); status.value="Scanning for ResMed…"
            (adapter.bluetoothLeScanner ?: error("Bluetooth scanner unavailable")).startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(service)).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
            scanJob=scope.launch { delay(10000); stopScan(); status.value=if(devices.value.isEmpty()) "No ResMed found. Move closer and try again." else "Select your AirSense" }
        } catch(e:Exception) { status.value=e.message ?: "Scan failed" }
    }
    private fun stopScan() { scanJob?.cancel(); scanJob=null; runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } }
    fun connect(device: Nearby) {
        disconnect(); status.value="Connecting to ${device.name}…"
        ready=CompletableDeferred(); decoder=FigProtocol.Decoder();writeAck=Channel(Channel.UNLIMITED)
        work=scope.launch {
            try {
                gatt=adapter.getRemoteDevice(device.address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
                withTimeout(20000) { ready.await() }
                status.value="Requesting pairing code…"
                pairing=FigProtocol.Pairing()
                exchange=rpc("StartKeyExchange",JSONObject().put("clientPk",pairing!!.publicHex),false)
                needsCode.value=true;status.value="On AirSense: Home → More → myAir. Enter its four-digit code."
            } catch(e:Exception) { fail(e) }
        }
    }
    fun pair(code:String) {
        if(!needsCode.value)return
        needsCode.value=false
        work=scope.launch {
            try {
                status.value="Authenticating…"
                val e=exchange!!;val p=pairing!!
                val proof=p.proof(e.getString("serverPk"),e.getString("salt"),code)
                val result=rpc("ConfirmKeyExchange",JSONObject().put("clientConfirmation",proof),false)
                session=p.finish(result.getString("serverConfirmation"),result.getString("nonce"))
                pairing=null;exchange=null
                rpc("GetVersion",null,true)
                val accepted=rpc("StartStream",JSONObject().put("dataIds",JSONArray(listOf("_RFL","_MKP"))).put("sampleIntervalMs",40).put("reportIntervalMs",200),true)
                val ids=accepted.getJSONArray("dataIds")
                check(ids.length()==2 && (0 until ids.length()).all { ids.getJSONObject(it).optBoolean("valid") }) { "Device rejected stream channels" }
                streaming.value=true;status.value="Connected • Waiting for live samples"
            } catch(e:Exception) { fail(e) }
        }
    }
    private fun fail(e:Exception) {
        if(e is CancellationException)return
        val message=e.message ?: e.javaClass.simpleName
        disconnect();status.value="Connection failed: $message"
    }
    fun disconnect() {
        stopScan(); work?.cancel();work=null
        replies.values.forEach { it.cancel() };replies.clear()
        gatt?.disconnect();gatt?.close();gatt=null;tx=null;session=null;pairing=null;exchange=null
        streaming.value=false;needsCode.value=false;status.value="Disconnected"
    }
    private suspend fun rpc(method:String, params:JSONObject?, encrypted:Boolean):JSONObject {
        val id=++nextId;val pending=CompletableDeferred<JSONObject>();replies[id]=pending
        try {
            val version=if(method=="StartStream") "1.0" else "2.0"
            val message=JSONObject().put("jsonrpc",version).put("id",id).put("method",method)
            if(params!=null) message.put("params",params)
            val bytes=message.toString().toByteArray()
            val frame=FigProtocol.frame(if(encrypted)0x397 else 0x393,if(encrypted)FigProtocol.encrypt(bytes,session!!) else bytes)
            val characteristic=tx ?: error("Write characteristic unavailable")
            val noResponse=characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            for(chunk in frame.toList().chunked(20)) {
                characteristic.writeType=if(noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value=chunk.toByteArray()
                check(gatt?.writeCharacteristic(characteristic)==true) { "Bluetooth write rejected" }
                check(withTimeout(5000) { writeAck.receive() }==BluetoothGatt.GATT_SUCCESS) { "Bluetooth write failed" }
            }
            val response=withTimeout(15000) { pending.await() }
            if(response.has("error")) error(response.getJSONObject("error").optString("message","RPC rejected"))
            return response.getJSONObject("result")
        } finally { replies.remove(id) }
    }
    private fun received(bytes:ByteArray) {
        try {
            for((lane,payload) in decoder.feed(bytes)) {
                if(lane!=0x392 && lane!=0x396)continue
                val clear=if(lane==0x396) FigProtocol.decrypt(payload,session ?: error("Encrypted data before authentication")) else payload
                val message=JSONObject(String(clear))
                if(message.has("id")) replies[message.getInt("id")]?.complete(message)
                else if(message.optString("method")=="StreamData") {
                    val p=message.getJSONObject("params");val data=p.getJSONArray("data")
                    var flow=emptyList<Float>();var pressure=emptyList<Float>()
                    for(i in 0 until data.length()) {
                        val item=data.getJSONObject(i)
                        fun values(key:String)=item.getJSONArray(key).let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }
                        if(item.has("PatientFlow"))flow=values("PatientFlow")
                        if(item.has("MaskPressure"))pressure=values("MaskPressure")
                    }
                    check(flow.size==pressure.size && flow.isNotEmpty()) { "Incomplete live sample batch" }
                    onSamples(LiveReading(p.getString("startTime"),p.getInt("intervalMs"),flow,pressure))
                    status.value="Live • ${1000/p.getInt("intervalMs")} Hz"
                }
            }
        } catch(e:Exception) { fail(e) }
    }
    private val callback=object:BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,statusCode:Int,newState:Int) { scope.launch {
            if(g!=gatt)return@launch
            if(statusCode!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED) { fail(IllegalStateException("Bluetooth disconnected ($statusCode)"));return@launch }
            if(newState==BluetoothProfile.STATE_CONNECTED && !g.discoverServices())fail(IllegalStateException("Service discovery failed"))
        } }
        override fun onServicesDiscovered(g:BluetoothGatt,statusCode:Int) { scope.launch {
            if(g!=gatt)return@launch
            try {
                check(statusCode==BluetoothGatt.GATT_SUCCESS)
                val s=g.getService(service) ?: error("AirSense service missing")
                tx=s.getCharacteristic(txId) ?: error("Transmit characteristic missing")
                val rx=s.getCharacteristic(rxId) ?: error("Receive characteristic missing")
                check(g.setCharacteristicNotification(rx,true))
                val descriptor=rx.getDescriptor(cccd) ?: error("Notification descriptor missing")
                descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                check(g.writeDescriptor(descriptor))
            } catch(e:Exception) { ready.completeExceptionally(e) }
        } }
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,statusCode:Int) { scope.launch {
            if(g!=gatt)return@launch
            if(statusCode==BluetoothGatt.GATT_SUCCESS)ready.complete(Unit) else ready.completeExceptionally(IllegalStateException("Notification setup failed ($statusCode)"))
        } }
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,statusCode:Int) { if(g==gatt)writeAck.trySend(statusCode) }
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic) { val bytes=c.value.copyOf();scope.launch { if(g==gatt)received(bytes) } }
    }
}
