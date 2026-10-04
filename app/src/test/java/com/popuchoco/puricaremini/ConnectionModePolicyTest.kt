package com.popuchoco.puricaremini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConnectionModePolicyTest {
    @Test fun backgroundAndProximityModesAreMutuallyExclusive() {
        val background = ConnectionModePolicy.background(true)
        val proximity = ConnectionModePolicy.proximityAuto(true)

        assertEquals(ConnectionMode.BACKGROUND, background)
        assertEquals(ConnectionMode.PROXIMITY_AUTO, proximity)
        assertNotEquals(background, proximity)
    }

    @Test fun disablingEitherModeReturnsToNone() {
        assertEquals(ConnectionMode.NONE, ConnectionModePolicy.background(false))
        assertEquals(ConnectionMode.NONE, ConnectionModePolicy.proximityAuto(false))
    }
}
