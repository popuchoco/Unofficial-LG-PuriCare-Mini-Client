package com.popuchoco.puricaremini

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {
    @Test fun exportContainsConnectionSnapshotAndLogs() {
        val state = BleUiState(
            phase = "已連線",
            connected = true,
            deviceName = "PuriCare \"Mini\"",
            snapshot = AirSnapshot(pm25 = 12, battery = 88, power = true, fan = 8, turbo = false),
            deviceDetails = DeviceDetails(manufacturer = "LG", firmware = "1.2.3", hardware = "A1"),
            logs = listOf("12:00:01  RX 04 54", "12:00:00  TX GET ALL"),
        )

        val json = DiagnosticExport.toJson(state, "2026-10-04T12:00:02+08:00", "0.1.0", "Android 15 (API 35)")

        assertTrue(json.contains("\"connected\": true"))
        assertTrue(json.contains("\"pm25\": 12"))
        assertTrue(json.contains("\"fan\": 8"))
        assertTrue(json.contains("\"turbo\": false"))
        assertTrue(json.contains("\"firmware\": \"1.2.3\""))
        assertTrue(json.contains("\"backgroundConnectionSupported\": false"))
        assertTrue(json.contains("PuriCare \\\"Mini\\\""))
        assertTrue(json.contains("RX 04 54"))
        assertFalse(json.contains("kotlin."))
    }

    @Test fun exportRepresentsUnavailableValuesAsNull() {
        val json = DiagnosticExport.toJson(BleUiState(), "now", "test", "test")
        assertTrue(json.contains("\"pm25\": null"))
        assertTrue(json.contains("\"deviceName\": null"))
    }
}
