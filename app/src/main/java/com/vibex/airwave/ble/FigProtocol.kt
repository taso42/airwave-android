package com.vibex.airwave.ble

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Protocol implementation informed by airbreak-plus; see THIRD_PARTY_NOTICES.md.
internal object FigProtocol {
    fun hash(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }
    fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }
    fun bytes(s: String): ByteArray { require(s.length % 2 == 0); return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
    fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun crc(b: ByteArray) = CRC32().run { update(b); value.toInt() }
    fun frame(lane: Int, payload: ByteArray): ByteArray {
        require(payload.size <= 65535)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort(lane.toShort()).putShort(payload.size.toShort()).putInt(crc(payload)).array()
        return ByteBuffer.allocate(16 + payload.size).order(ByteOrder.LITTLE_ENDIAN).putInt(0xCAFEBABE.toInt()).put(header).putInt(crc(header)).put(payload).array()
    }
    class Decoder {
        private var pending = byteArrayOf()
        fun feed(bytes: ByteArray): List<Pair<Int, ByteArray>> {
            pending += bytes
            require(pending.size < 131072) { "Incoming frame buffer exceeded limit" }
            val result = mutableListOf<Pair<Int, ByteArray>>()
            while (pending.size >= 16) {
                val h = ByteBuffer.wrap(pending).order(ByteOrder.LITTLE_ENDIAN)
                if (h.int != 0xCAFEBABE.toInt()) { pending = pending.drop(1).toByteArray(); continue }
                val lane = h.short.toInt() and 65535; val count = h.short.toInt() and 65535
                val payloadCrc = h.int; val headerCrc = h.int
                require(headerCrc == crc(pending.copyOfRange(4, 12))) { "Header checksum mismatch" }
                if (pending.size < 16 + count) break
                val payload = pending.copyOfRange(16,16+count)
                require(payloadCrc == crc(payload)) { "Payload checksum mismatch" }
                result.add(lane to payload); pending = pending.copyOfRange(16+count,pending.size)
            }
            return result
        }
    }
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = random(16)
        val raw = ByteBuffer.allocate(((data.size+2+15)/16)*16).order(ByteOrder.LITTLE_ENDIAN).putShort(data.size.toShort()).put(data).array()
        return iv + Cipher.getInstance("AES/CBC/NoPadding").run { init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv)); doFinal(raw) }
    }
    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size >= 32 && (data.size-16)%16 == 0)
        val raw = Cipher.getInstance("AES/CBC/NoPadding").run { init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(data.copyOfRange(0,16))); doFinal(data.copyOfRange(16,data.size)) }
        val size = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
        require(size <= raw.size-2)
        return raw.copyOfRange(2,2+size)
    }
    class Pairing(privateValue: BigInteger? = null) {
        private val n = BigInteger("AC6BDB41324A9A9BF166DE5E1389582FAF72B6651987EE07FC3192943DB56050A37329CBB4A099ED8193E0757767A13DD52312AB4B03310DCD7F48A9DA04FD50E8083969EDB767B0CF6095179A163AB3661A05FBD5FAAAE82918A9962F0B93B855F97993EC975EEAA80D740ADBF4FF747359D041D5C33EA71D281E446B14773BCA97B43A23FB801676BD207A436C6481F1D2B9078717461A5B9D32E688F87748544523B524B0D57D5EA77A2775D2ECFA032CFBDBF52FB3786160279004E57AE6AF874E7303CE53299CCC041C7BC308D82A5698F3A8D0C38271AE35F8E9DBFBB694B5C803D89F7AE435DE236D525F54759B65E372FCD68EF20FA7111F9E4AFF73",16)
        private val g = BigInteger.valueOf(2)
        private val a = privateValue ?: BigInteger(1,random(32))
        private val public = g.modPow(a,n)
        private fun pad(x: BigInteger) = x.toByteArray().takeLast(256).toByteArray().let { ByteArray(256-it.size)+it }
        val publicHex get() = hex(pad(public))
        lateinit var master: ByteArray
        private lateinit var serverProof: ByteArray
        fun proof(server: String, saltHex: String, code: String): String {
            require(code.matches(Regex("[0-9]{4}"))) { "Enter four digits" }
            val b=BigInteger(server,16); require(b.mod(n) != BigInteger.ZERO)
            val salt=bytes(saltHex)
            val k=BigInteger(1,hash(pad(n),pad(g)))
            val x=BigInteger(1,hash(salt,hash(code.toByteArray(Charsets.US_ASCII))))
            val u=BigInteger(1,hash(pad(public),pad(b))); require(u != BigInteger.ZERO)
            val shared=(b-k*g.modPow(x,n)).mod(n).modPow(a+u*x,n)
            master=hash(pad(shared))
            val hn=hash(pad(n)); val hg=hash(pad(g))
            val xor=ByteArray(32) { (hn[it].toInt() xor hg[it].toInt()).toByte() }
            val proof=hash(xor,salt,pad(public),pad(b),master)
            serverProof=hash(pad(public),proof,master)
            return hex(proof)
        }
        fun finish(proof: String, nonce: String): ByteArray {
            require(MessageDigest.isEqual(serverProof,bytes(proof))) { "Pairing server proof mismatch" }
            require(nonce.isNotEmpty())
            return hash(master,bytes(nonce))
        }
    }
}
