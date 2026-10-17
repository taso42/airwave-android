package com.vibex.airwave.ble

// Only the observed pre-proof rejection gets a bounded retry on a fresh link.
internal fun retrySessionRejection(method: String, message: String?, attempts: Int): Boolean =
    method == "RequestSession" && message == "VerificationFailure" && attempts < 3
