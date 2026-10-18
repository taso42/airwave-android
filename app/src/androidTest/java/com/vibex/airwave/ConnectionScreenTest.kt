package com.vibex.airwave

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class ConnectionScreenTest {
    @Test fun packetAcquisitionDoesNotPublishUntilDisplayTick() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var initialNow = 0L
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                model.useSimulation(false)
                initialNow = model.now
                val revision = model.chartRevision
                repeat(20) { index ->
                    model.acceptLiveReading(com.vibex.airwave.ble.LiveReading(
                        "2026-10-16T00:00:%02d.000Z".format(index), 40,
                        listOf(.5f), listOf(5f),
                        mapOf("therapyPressure" to listOf(6f))))
                }
                assertEquals(20, model.samples.size)
                assertEquals(initialNow, model.now)
                assertEquals(revision, model.chartRevision)
                assertEquals(0L, model.lastPacket)
                assertNull(model.liveTherapyPressure)
                model.frozen = model.samples
            }
            Thread.sleep(400)
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertTrue(model.now > initialNow)
                assertTrue(model.lastPacket > 0L)
                assertEquals(6f, model.liveTherapyPressure!!, 0f)
                assertEquals(20, model.frozen!!.size)
                model.clearDisplay()
            }
        }
    }

    @Test fun disconnectAndPreparingReconnectPreserveSamplesAndAxis() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var retained = emptyList<Sample>()
            var end = 0f
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                model.useSimulation(true)
                retained = model.samples
                end = model.chartEnd
                model.disconnectStream()
                assertEquals(retained, model.samples)
                model.prepareBluetooth()
                assertEquals(retained, model.samples)
            }
            Thread.sleep(250)
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertEquals(retained, model.samples)
                assertTrue(model.chartEnd > end + .1f)
            }
        }
    }

    @Test fun frozenAxisStaysFixedWhileLiveAxisAdvancesDuringPause() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var frozenEnd = 0f
            var previous = false
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                previous = model.keepStreamingInBackground
                model.setBackgroundStreaming(false)
                model.useSimulation(true)
                model.frozen = model.samples.toList()
                frozenEnd = model.chartEnd
                model.onBackground()
            }
            Thread.sleep(300)
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertEquals(frozenEnd, model.chartEnd, .001f)
                model.frozen = null
                assertTrue(model.chartEnd > frozenEnd + .15f)
                model.onForeground()
                model.useSimulation(false)
                model.setBackgroundStreaming(previous)
            }
        }
    }

    @Test fun backgroundWithoutStreamingDoesNotReconnectAndSettingSurvivesRotation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous = false
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                previous = model.keepStreamingInBackground
                model.setBackgroundStreaming(true)
            }
            scenario.recreate()
            scenario.onActivity {
                val model = ViewModelProvider(it)[AirwaveViewModel::class.java]
                assertTrue(model.keepStreamingInBackground)
                model.setBackgroundStreaming(false)
                model.onBackground()
                model.onForeground()
                assertFalse(model.client.connecting.value)
                assertFalse(model.client.streaming.value)
                model.setBackgroundStreaming(previous)
            }
        }
    }
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
