package com.popuchoco.puricaremini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterLifeTest {
    @Test fun calculatesPercentAndHoursFromDeviceValues() {
        val life = AirSnapshot(filterRemaining = 1954, filterTotal = 2000).filterLife()
        assertEquals(97, life?.percent)
        assertEquals(46, life?.usedHours)
        assertEquals(2000, life?.totalHours)
    }

    @Test fun usesStandardTotalWhenDeviceOmitsTotal() {
        val life = AirSnapshot(filterRemaining = 1000).filterLife()
        assertEquals(50, life?.percent)
        assertFalse(life?.totalFromDevice ?: true)
    }

    @Test fun identifiesDeviceReportedTotal() {
        assertTrue(AirSnapshot(filterRemaining = 1954, filterTotal = 2000).filterLife()?.totalFromDevice == true)
    }

    @Test fun keepsNonZeroLifeVisibleAtOnePercent() {
        assertEquals(1, AirSnapshot(filterRemaining = 1, filterTotal = 2000).filterLife()?.percent)
        assertEquals(0, AirSnapshot(filterRemaining = 0, filterTotal = 2000).filterLife()?.percent)
    }

    @Test fun reminderFiresOnceAndResetsAfterLifeRises() {
        assertTrue(FilterReminderPolicy.shouldNotify(percent = 10, threshold = 10, notifiedThreshold = null))
        assertFalse(FilterReminderPolicy.shouldNotify(percent = 9, threshold = 10, notifiedThreshold = 10))
        assertTrue(FilterReminderPolicy.shouldReset(percent = 11, threshold = 10, notifiedThreshold = 10))
    }

    @Test fun failedLightWriteDoesNotOverwriteANewerSelection() {
        assertEquals(1, lightLevelAfterFailedWrite(currentLevel = 2, attemptedLevel = 2, previousLevel = 1))
        assertEquals(4, lightLevelAfterFailedWrite(currentLevel = 4, attemptedLevel = 2, previousLevel = 1))
    }

    @Test fun acceptsOnlySupportedReminderThresholds() {
        assertFalse(FilterReminderPolicy.shouldNotify(percent = 7, threshold = 7, notifiedThreshold = null))
        FILTER_REMINDER_THRESHOLDS.forEach { threshold ->
            assertTrue(FilterReminderPolicy.shouldNotify(percent = threshold, threshold = threshold, notifiedThreshold = null))
        }
    }
}
