package com.vibex.airwave.ble
import org.junit.Test
import org.junit.Assert.*
class SessionRetryTest {
    @Test fun onlyObservedPreProofFailureGetsThreeRetries() {
        for (attempt in 0..2) assertTrue(retrySessionRejection("RequestSession", "VerificationFailure", attempt))
        assertFalse(retrySessionRejection("RequestSession", "VerificationFailure", 3))
        assertFalse(retrySessionRejection("CheckSessionIntegrity", "VerificationFailure", 0))
        assertFalse(retrySessionRejection("RequestSession", "UnknownClient", 0))
        assertFalse(retrySessionRejection("StartStream", "VerificationFailure", 0))
    }
}
