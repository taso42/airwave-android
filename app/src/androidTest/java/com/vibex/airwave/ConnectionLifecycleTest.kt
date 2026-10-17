package com.vibex.airwave

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.vibex.airwave.ble.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ConnectionLifecycleTest {
    @Test fun recreationRetainsClientBufferAndFrozenView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var before: AirwaveViewModel?=null
            scenario.onActivity {
                before=ViewModelProvider(it)[AirwaveViewModel::class.java]
                before!!.demo=false
                before!!.samples=listOf(Sample(1f,2f,3f))
                before!!.frozen=before!!.samples
                before!!.window=60
            }
            scenario.recreate()
            scenario.onActivity {
                val after=ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertSame(before,after)
                assertSame(before!!.client,after.client)
                assertEquals(60,after.window)
                assertEquals(listOf(Sample(1f,2f,3f)),after.samples)
                assertEquals(after.samples,after.frozen)
            }
        }
    }
    @Test fun encryptedPairingSurvivesStoreRecreationAndCanBeForgotten() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-pairing.enc"
        val store=PairingStore(context,name)
        try {
            val expected=SavedPairing(Nearby("01:02:03:04:05:06","test-device"),"test-client-id",ByteArray(32){it.toByte()})
            store.save(expected)
            val disk=File(context.noBackupFilesDir,name).readBytes()
            assertFalse(String(disk).contains("test-client-id"))
            val actual=PairingStore(context,name).load()!!
            assertEquals(expected.device,actual.device)
            assertEquals(expected.clientId,actual.clientId)
            assertArrayEquals(expected.master,actual.master)
            store.clear();assertNull(store.load())
        } finally { store.clear() }
    }
}
