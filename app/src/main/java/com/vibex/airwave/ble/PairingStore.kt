package com.vibex.airwave.ble

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SavedPairing(val device: Nearby, val clientId: String, val master: ByteArray)
internal class PairingStore(context: Context, fileName: String = "airsense-pairing.enc") {
    private val file=AtomicFile(File(context.noBackupFilesDir,fileName))
    private val alias="airwave-pairing-v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun load(): SavedPairing? {
        if(!file.baseFile.exists())return null
        val raw=file.readFully();require(raw.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,raw.copyOfRange(0,12)))
        val data=JSONObject(String(cipher.doFinal(raw.copyOfRange(12,raw.size)),Charsets.UTF_8))
        val master=FigProtocol.bytes(data.getString("master"));require(master.size==32)
        return SavedPairing(Nearby(data.getString("address"),data.getString("name")),data.getString("clientId"),master)
    }
    fun save(pair:SavedPairing) {
        val data=JSONObject().put("address",pair.device.address).put("name",pair.device.name)
            .put("clientId",pair.clientId).put("master",FigProtocol.hex(pair.master)).toString().toByteArray()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val raw=cipher.iv+cipher.doFinal(data)
        val output=file.startWrite()
        try { output.write(raw);file.finishWrite(output) } catch(e:Exception) { file.failWrite(output);throw e }
    }
    fun clear() { file.delete() }
}
