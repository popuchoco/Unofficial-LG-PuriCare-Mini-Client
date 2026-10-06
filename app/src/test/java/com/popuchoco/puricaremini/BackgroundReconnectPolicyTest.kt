package com.popuchoco.puricaremini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundReconnectPolicyTest {
    @Test fun reconnectUsesBoundedBackoff() {
        assertEquals(3_000L, BackgroundReconnectPolicy.delayForAttempt(0))
        assertEquals(6_000L, BackgroundReconnectPolicy.delayForAttempt(1))
        assertEquals(15_000L, BackgroundReconnectPolicy.delayForAttempt(2))
        assertEquals(30_000L, BackgroundReconnectPolicy.delayForAttempt(3))
        assertEquals(60_000L, BackgroundReconnectPolicy.delayForAttempt(4))
        assertNull(BackgroundReconnectPolicy.delayForAttempt(5))
    }
}
