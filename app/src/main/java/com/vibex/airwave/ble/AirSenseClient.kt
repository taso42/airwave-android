@file:Suppress("DEPRECATION", "MissingPermission")
package com.vibex.airwave.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

 data class Nearby(val address: String, val name: String)
 data class LiveReading(val timestamp: String, val intervalMs: Int, val flow: List<Float>, val pressure: List<Float>, val metrics: Map<String, List<Float?>> = emptyMap())

class AirSenseClient(private val context: Context, private val scope: CoroutineScope, private val onSamples: (LiveReading) -> Unit) {
    val status = MutableStateFlow("Not connected")
    val devices = MutableStateFlow<List<Nearby>>(emptyList())
    val needsCode = MutableStateFlow(false)
    val connecting = MutableStateFlow(false)
    val disconnecting = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    val activeDevice = MutableStateFlow<Nearby?>(null)
    val streaming = MutableStateFlow(false)
    private val store=PairingStore(context)
    val savedDevice=MutableStateFlow<Nearby?>(null)
    private var saved: SavedPairing? = null
    private var selected: Nearby? = null
    private fun trace(event: String) { android.util.Log.i("AirwaveConnection", event) }
    private var waitingForRadio=false
    private var wanted=false
    private var attempts=0
    private var retryJob: Job? = null
    private var watchdog: Job? = null
    private var lastReceived=0L
    init {
        try { saved=store.load();savedDevice.value=saved?.device }
        catch(_:Exception) { status.value="Saved pairing unavailable. Forget pairing and pair again." }
    }
    fun reconnectSaved() { savedDevice.value?.let { connect(it) } }
    fun forget() {
        disconnect();store.clear();saved=null;savedDevice.value=null
        status.value="Pairing forgotten on this phone"
    }
    private class RpcRejected(val method: String, message:String): Exception(message)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private val closing = mutableMapOf<BluetoothGatt, CompletableDeferred<Unit>>()
    private var closeJob: Job? = null
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
    private var exchangeStartedMs: Long? = null
    // Only structural metadata; never values, fingerprints, codes or proofs.
    private fun fieldShape(data: JSONObject?, key: String): String {
        val value = data?.opt(key)
        return when (value) {
            is String -> "$key:string chars=${value.length} hex=${value.isNotEmpty() && value.all { it in "0123456789abcdefABCDEF" }}"
            null, JSONObject.NULL -> "$key:missing"
            else -> "$key:non-string"
        }
    }
    private val service = UUID.fromString("0000fd56-0000-1000-8000-00805f9b34fb")
    private val txId = UUID.fromString("a6220002-35f1-4b20-afae-cb089d2044aa")
    private val rxId = UUID.fromString("a6220003-35f1-4b20-afae-cb089d2044aa")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { scope.launch {
            val d=Nearby(result.device.address,result.scanRecord?.deviceName ?: "ResMed")
            devices.value=(devices.value.filterNot { it.address==d.address }+d)
        } }
        override fun onScanFailed(errorCode: Int) { stopScan();status.value="Search failed ($errorCode)" }
    }
    fun scan() {
        disconnect()
        try {
            check(adapter?.isEnabled == true) { "Enable Bluetooth on your phone" }
            devices.value=emptyList(); scanning.value=true; status.value="Searching for nearby devices…"
            (adapter.bluetoothLeScanner ?: error("Bluetooth scanner unavailable")).startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(service)).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
            scanJob=scope.launch { delay(10000); stopScan(); status.value=if(devices.value.isEmpty()) "No ResMed found. Move closer and try again." else "Select your AirSense" }
        } catch(e:Exception) { stopScan();status.value=e.message ?: "Search failed" }
    }
    private fun stopScan() { scanning.value=false; scanJob?.cancel(); scanJob=null; runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } }
    fun connect(device: Nearby) {
        disconnect(); selected=device;wanted=true;attempts=0
        startConnection(device)
    }
    private fun startConnection(device:Nearby) {
        trace("connection attempt=${attempts + 1}")
        closeLink();connecting.value=true;activeDevice.value=device;status.value="Connecting to ${device.name}…"
        ready=CompletableDeferred(); decoder=FigProtocol.Decoder();writeAck=Channel(Channel.UNLIMITED)
        work=scope.launch {
            try {
                closeJob?.join()
                status.value = "Connecting to ${device.name}…"
                check(adapter?.isEnabled == true) { "Bluetooth is off" }
                gatt=adapter.getRemoteDevice(device.address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE) ?: error("Bluetooth connection unavailable")
                withTimeout(20000) { ready.await() }
                val remembered=saved?.takeIf { it.device.address==device.address }
                if(remembered!=null) {
                    status.value="Restoring paired session…"
                    val challenge=rpc("RequestSession",JSONObject().put("clientId",remembered.clientId),false)
                    val mac=Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(remembered.master,"HmacSHA256")) }
                    val proof=mac.doFinal(FigProtocol.bytes(challenge.getString("challenge")))
                    val confirmation=rpc("CheckSessionIntegrity",JSONObject().put("response",FigProtocol.hex(proof)),false)
                    check(!confirmation.has("confirmation") || confirmation.optBoolean("confirmation")) { "Session proof rejected" }
                    session=FigProtocol.hash(remembered.master,FigProtocol.bytes(challenge.getString("nonce")))
                    startStream()
                    return@launch
                }
                status.value="Requesting pairing code…"
                exchangeStartedMs=null
                pairing=FigProtocol.Pairing()
                exchange=rpc("StartKeyExchange",JSONObject().put("clientPk",pairing!!.publicHex),false)
                exchangeStartedMs=android.os.SystemClock.elapsedRealtime()
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
                trace("pair submit exchangePresent=${exchange != null} keypairPresent=${pairing != null} exchangeAgeMs=${exchangeStartedMs?.let { android.os.SystemClock.elapsedRealtime()-it }} codeFormatValid=${code.matches(Regex("[0-9]{4}"))}")
                val e=exchange!!;val p=pairing!!
                val proof=p.proof(e.getString("serverPk"),e.getString("salt"),code)
                val result=rpc("ConfirmKeyExchange",JSONObject().put("clientConfirmation",proof),false)
                session=p.finish(result.getString("serverConfirmation"),result.getString("nonce"))
                val credential=SavedPairing(selected!!,result.getString("clientId"),p.master.copyOf())
                store.save(credential);saved=credential;savedDevice.value=credential.device
                pairing=null;exchange=null
                startStream()
            } catch(e:Exception) { fail(e) }
        }
    }
    private suspend fun startStream() {
        rpc("GetVersion",null,true)
        val accepted=rpc("StartStream",JSONObject().put("dataIds",JSONArray(listOf("_RFL","_MKP","_LKF","_RR2","_TD2","_MV2","_INP"))).put("sampleIntervalMs",40).put("reportIntervalMs",200),true)
        val ids=accepted.getJSONArray("dataIds")
        check(ids.length()==7 && (0 until ids.length()).all { ids.getJSONObject(it).optBoolean("valid") }) { "Device rejected stream channels" }
        connecting.value=false;streaming.value=true;status.value="Connected • Waiting for live samples"
        lastReceived=android.os.SystemClock.elapsedRealtime()
        watchdog=scope.launch {
            while(isActive) {
                delay(2000)
                if(android.os.SystemClock.elapsedRealtime()-lastReceived>10000) {
                    fail(IllegalStateException("Live stream stalled"));break
                }
            }
        }
    }
    private fun fail(e:Exception) {
        if(e is CancellationException && e !is TimeoutCancellationException)return
        if (wanted && runCatching { adapter?.isEnabled != true }.getOrDefault(false)) {
            waitForRadio()
            return
        }
        closeLink()
        val message=e.message ?: e.javaClass.simpleName
        if(wanted && saved?.device?.address==selected?.address && (e !is RpcRejected || retrySessionRejection(e.method, e.message, attempts)) && attempts<5) {
            val delayMs=1000L shl attempts++
            connecting.value=true;status.value="Reconnecting ($attempts/5) • $message"
            retryJob?.cancel()
            retryJob=scope.launch { delay(delayMs);if(wanted)selected?.let { startConnection(it) } }
        } else {
            wanted=false
            status.value=if(e is RpcRejected) "Connection rejected during ${e.method}: $message. Tap Connect to retry." else "Connection stopped: $message. Try connecting again."
        }
    }
    fun disconnect() {
        trace("disconnect requested")
        waitingForRadio=false
        wanted=false;retryJob?.cancel();retryJob=null;stopScan();closeLink()
        status.value=if (disconnecting.value) "Disconnecting…" else "Disconnected"
    }
    private fun waitForRadio() {
        if (!wanted || waitingForRadio) return
        waitingForRadio = true
        retryJob?.cancel()
        closeLink()
        connecting.value = true
        status.value = "Bluetooth is off • Waiting for Bluetooth"
        retryJob = scope.launch {
            delay(60000)
            if (waitingForRadio && wanted) {
                waitingForRadio = false
                wanted = false
                connecting.value = false
                status.value = "Bluetooth stayed off. Turn it on and tap Connect."
            }
        }
    }
    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) ?: return
            scope.launch {
                if (!wanted) return@launch
                when (state) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> waitForRadio()
                    BluetoothAdapter.STATE_ON -> if (waitingForRadio) {
                        waitingForRadio = false
                        retryJob?.cancel(); retryJob = null
                        selected?.let { startConnection(it) }
                    }
                }
            }
        }
    }
    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(radioReceiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(radioReceiver, filter)
        scope.coroutineContext[Job]?.invokeOnCompletion { runCatching { context.unregisterReceiver(radioReceiver) } }
    }
    private fun closeLink() {
        work?.cancel();work=null;watchdog?.cancel();watchdog=null
        replies.values.forEach { it.cancel() };replies.clear()
        val old=gatt;gatt=null
        if (old != null) {
            val disconnected = CompletableDeferred<Unit>()
            closing[old] = disconnected
            runCatching { old.disconnect() }
            disconnecting.value = true
            closeJob = scope.launch {
                try {
                    try { withTimeoutOrNull(2000) { disconnected.await() } }
                    finally {
                        closing.remove(old)
                        runCatching { old.close() }
                    }
                } finally {
                    disconnecting.value = false
                    if (status.value == "Disconnecting…") status.value = "Disconnected"
                }
            }
        }
        tx=null;session=null;pairing=null;exchange=null
        connecting.value=false;streaming.value=false;needsCode.value=false
    }
    private suspend fun rpc(method:String, params:JSONObject?, encrypted:Boolean):JSONObject {
        val started=android.os.SystemClock.elapsedRealtime()
        trace("RPC start: $method")
        val id=++nextId;val pending=CompletableDeferred<JSONObject>();replies[id]=pending
        try {
            val version=if(method=="StartStream") "1.0" else "2.0"
            val message=JSONObject().put("jsonrpc",version).put("id",id).put("method",method)
            if(params!=null) message.put("params",params)
            val bytes=message.toString().toByteArray()
            val frame=FigProtocol.frame(if(encrypted)0x397 else 0x393,if(encrypted)FigProtocol.encrypt(bytes,session!!) else bytes)
            val characteristic=tx ?: error("Write characteristic unavailable")
            val noResponse=characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val authField = when(method) {
                "StartKeyExchange" -> "clientPk"
                "ConfirmKeyExchange" -> "clientConfirmation"
                "RequestSession" -> "clientId"
                "CheckSessionIntegrity" -> "response"
                else -> null
            }
            trace("RPC tx method=$method id=$id encrypted=$encrypted jsonBytes=${bytes.size} frameBytes=${frame.size} chunks=${(frame.size+19)/20} writeNoResponse=$noResponse")
            if (authField != null) trace("RPC request shape method=$method ${fieldShape(params,authField)}")
            var written=0
            for(chunk in frame.toList().chunked(20)) {
                characteristic.writeType=if(noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value=chunk.toByteArray()
                check(gatt?.writeCharacteristic(characteristic)==true) { "Bluetooth write rejected" }
                check(withTimeout(5000) { writeAck.receive() }==BluetoothGatt.GATT_SUCCESS) { "Bluetooth write failed" }
                written++
            }
            trace("RPC writes complete method=$method id=$id chunks=$written elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}")
            val response=withTimeout(15000) { pending.await() }
            if(response.has("error")) {
                trace("RPC rejected: $method code=${response.getJSONObject("error").optInt("code")}")
                throw RpcRejected(method, response.getJSONObject("error").optString("message","RPC rejected"))
            }
            trace("RPC complete: $method")
            val result=response.get("result")
            check(result != false) { "RPC rejected operation" }
            if (result is JSONObject) {
                val fields = when(method) {
                    "StartKeyExchange" -> listOf("serverPk","salt")
                    "ConfirmKeyExchange" -> listOf("serverConfirmation","nonce","clientId")
                    "RequestSession" -> listOf("challenge","nonce")
                    else -> emptyList()
                }
                fields.forEach { trace("RPC result shape method=$method ${fieldShape(result,it)}") }
            }
            return result as? JSONObject ?: JSONObject().put("value",result)
        } catch (e: Exception) {
            trace("RPC ended method=$method id=$id exception=${e.javaClass.simpleName} elapsedMs=${android.os.SystemClock.elapsedRealtime()-started}")
            throw e
        } finally { replies.remove(id) }
    }
    private fun received(bytes:ByteArray) {
        try {
            for((lane,payload) in decoder.feed(bytes)) {
                if(lane!=0x392 && lane!=0x396)continue
                val clear=if(lane==0x396) FigProtocol.decrypt(payload,session ?: error("Encrypted data before authentication")) else payload
                val message=JSONObject(String(clear))
                if(message.has("id")) {
                    val id=message.getInt("id")
                    val pending=replies[id]
                    trace("RPC rx id=$id lane=$lane pending=${pending != null} alreadyCompleted=${pending?.isCompleted} error=${message.has("error")}")
                    pending?.complete(message)
                }
                else if(message.optString("method")=="StreamData") {
                    val batch = decodeStreamData(message.getJSONObject("params"))
                    lastReceived=android.os.SystemClock.elapsedRealtime()
                    attempts=0
                    onSamples(batch)
                    status.value="Live • ${1000/batch.intervalMs} Hz"
                }
            }
        } catch(e:Exception) { fail(e) }
    }
    private val callback=object:BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,statusCode:Int,newState:Int) { scope.launch {
            trace("GATT status=$statusCode state=$newState current=${g == gatt}")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) closing[g]?.complete(Unit)
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
