package com.popuchoco.puricaremini

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRecoveryPolicyTest {
    @Test fun callbackMustBelongToCurrentConnection() {
        val current = Any()
        assertTrue(isCurrentConnection(current, current))
        assertFalse(isCurrentConnection(current, Any()))
        assertFalse(isCurrentConnection(null, current))
    }

    @Test fun notificationReflectsConnectionLifecycle() {
        assertEquals("已連線", backgroundNotificationText(BackgroundConnectionStatus.CONNECTED, "已連線"))
        assertEquals("6 秒後重試", backgroundNotificationText(BackgroundConnectionStatus.RETRYING, "6 秒後重試"))
        assertEquals("重連已暫停，點此開啟 App", backgroundNotificationText(BackgroundConnectionStatus.PAUSED, "ignored"))
    }
}
