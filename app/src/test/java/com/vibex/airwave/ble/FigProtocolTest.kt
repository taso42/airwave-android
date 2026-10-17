package com.vibex.airwave.ble
import org.junit.Test
import org.junit.Assert.*
import java.math.BigInteger
class FigProtocolTest {
    @Test fun pairingMatchesPythonReference() {
        val p=FigProtocol.Pairing(BigInteger("123456789"))
        assertEquals("35BC607719C73273FB3A57454560A458ADF8B003C7F1E5BC20DA91F2EE1B2D1E", p.proof("123456789ABCDEF","00112233445566778899AABBCCDDEEFF","1234"))
        assertEquals("ADF98AB49107309E9F46CA1084AA9BF47A8F122A1AA7B4C886D133A811D22B0F",FigProtocol.hex(p.master))
        assertEquals("FAEDDD6A2CCD08FC977C181851F5046BCF97F83E25D62512C09AC2D134C8B894", FigProtocol.hex(p.finish("4089BF9D2E0A12318610394510B86921CFA52729DAABD176931EB2492A139EEF","00112233")))
    }
    @Test fun framesSurviveFragmentation() {
        val frame=FigProtocol.frame(0x393,"hello".toByteArray())
        assertEquals("BEBAFECA9303050086A61036A8A8B73568656C6C6F",FigProtocol.hex(frame))
        val decoder=FigProtocol.Decoder()
        assertTrue(decoder.feed(frame.copyOfRange(0,10)).isEmpty())
        val decoded=decoder.feed(frame.copyOfRange(10,frame.size))
        assertEquals(0x393,decoded.single().first)
        assertEquals("hello",String(decoded.single().second))
    }
    @Test fun corruptedPayloadIsRejected() {
        val frame=FigProtocol.frame(0x393,"hello".toByteArray())
        frame[frame.lastIndex]=0
        try { FigProtocol.Decoder().feed(frame); fail("Corruption accepted") } catch (_:IllegalArgumentException) {}
    }
    @Test fun encryptionRoundTrip() {
        val data="Sample payload".toByteArray();val key=ByteArray(32){it.toByte()}
        assertArrayEquals(data,FigProtocol.decrypt(FigProtocol.encrypt(data,key),key))
    }
    @Test fun badServerProofIsRejected() {
        val p=FigProtocol.Pairing(BigInteger("123456789"))
        p.proof("123456789ABCDEF","00112233445566778899AABBCCDDEEFF","1234")
        try { p.finish("00","00112233");fail("Invalid proof accepted") } catch (_:IllegalArgumentException) {}
    }
}
