package com.vibex.airwave

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class StreamingServiceTest {
    @Test fun rapidStartStopAndRestartDoesNotCrashOrStopNewSession() {
        val unexpected = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(10) {
                scenario.onActivity { activity ->
                    StreamingService.start(activity, true) { unexpected.incrementAndGet() }
                    StreamingService.stop()
                }
                Thread.sleep(100)
            }
            scenario.onActivity { activity ->
                StreamingService.start(activity, true) { unexpected.incrementAndGet() }
                StreamingService.stop()
                StreamingService.start(activity, true) { unexpected.incrementAndGet() }
            }
            Thread.sleep(12000)
            assertEquals(0, unexpected.get())
            scenario.onActivity { StreamingService.stop() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }
}
