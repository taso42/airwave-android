package com.vibex.airwave

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class ConnectionScreenTest {
    @Test fun simulationIsOptInAndSwitchingClearsFrozenSamples() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertFalse(model.demo)
                assertTrue(model.samples.isEmpty())
                model.useSimulation(true)
                assertTrue(model.demo)
                assertFalse(model.client.streaming.value)
                assertTrue(model.samples.isNotEmpty())
                model.frozen = model.samples
            }
            scenario.recreate()
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertTrue(model.demo)
                assertNotNull(model.frozen)
                model.useSimulation(false)
                assertFalse(model.demo)
                assertTrue(model.samples.isEmpty())
                assertNull(model.frozen)
                assertEquals(0L, model.lastPacket)
            }
        }
    }
}
