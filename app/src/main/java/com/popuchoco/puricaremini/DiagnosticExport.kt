package com.popuchoco.puricaremini

object DiagnosticExport {
    fun toJson(
        state: BleUiState,
        generatedAt: String,
        appVersion: String,
        androidVersion: String,
        backgroundConnectionActive: Boolean = false,
        proximityAutoPowerActive: Boolean = false,
        filterReminderThreshold: Int? = null,
    ): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 4,")
        appendLine("  \"generatedAt\": ${generatedAt.json()},")
        appendLine("  \"appVersion\": ${appVersion.json()},")
        appendLine("  \"androidVersion\": ${androidVersion.json()},")
        appendLine("  \"connection\": {")
        appendLine("    \"phase\": ${state.phase.json()},")
        appendLine("    \"connected\": ${state.connected},")
        appendLine("    \"scanning\": ${state.scanning},")
        appendLine("    \"deviceName\": ${state.deviceName.jsonOrNull()}")
        appendLine("  },")
        appendLine("  \"capabilities\": {")
        appendLine("    \"backgroundConnectionSupported\": true,")
        appendLine("    \"backgroundConnectionActive\": $backgroundConnectionActive,")
        appendLine("    \"proximityAutoPowerActive\": $proximityAutoPowerActive,")
        appendLine("    \"filterReminderThresholdPercent\": ${filterReminderThreshold.jsonNumber()}")
        appendLine("  },")
        appendLine("  \"deviceDetails\": {")
        appendLine("    \"deviceVersion\": ${state.deviceDetails.version.jsonOrNull()}")
        appendLine("  },")
        appendLine("  \"snapshot\": {")
        appendLine("    \"pm1\": ${state.snapshot.pm1.jsonNumber()},")
        appendLine("    \"pm25\": ${state.snapshot.pm25.jsonNumber()},")
        appendLine("    \"pm10\": ${state.snapshot.pm10.jsonNumber()},")
        appendLine("    \"battery\": ${state.snapshot.batteryPercentForDisplay().jsonNumber()},")
        appendLine("    \"batteryRaw\": ${state.snapshot.battery.jsonNumber()},")
        appendLine("    \"batteryChargeState\": ${state.snapshot.batteryChargeState.jsonNumber()},")
        appendLine("    \"power\": ${state.snapshot.power.jsonBoolean()},")
        appendLine("    \"fan\": ${state.snapshot.fan.jsonNumber()},")
        appendLine("    \"turbo\": ${state.snapshot.turbo.jsonBoolean()},")
        appendLine("    \"auto\": ${state.snapshot.auto.jsonBoolean()},")
        appendLine("    \"sensorAlwaysOn\": ${state.snapshot.sensorAlwaysOn.jsonBoolean()},")
        appendLine("    \"lightLevel\": ${state.snapshot.lightLevel.jsonNumber()},")
        appendLine("    \"filterRemaining\": ${state.snapshot.filterRemaining.jsonNumber()},")
        appendLine("    \"filterTotal\": ${state.snapshot.filterTotal.jsonNumber()},")
        appendLine("    \"filterPercent\": ${state.snapshot.filterLife()?.percent.jsonNumber()},")
        appendLine("    \"updatedAtEpochMs\": ${state.snapshot.updatedAt.jsonNumber()}")
        appendLine("  },")
        appendLine("  \"nearbyDevices\": [")
        state.candidates.forEachIndexed { index, candidate ->
            append("    {\"name\": ${candidate.name.json()}, \"rssi\": ${candidate.rssi}, \"likely\": ${candidate.likely}}")
            appendLine(if (index == state.candidates.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"logsNewestFirst\": [")
        state.logs.forEachIndexed { index, entry ->
            append("    ${entry.json()}")
            appendLine(if (index == state.logs.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun String?.jsonOrNull(): String = this?.json() ?: "null"
    private fun Number?.jsonNumber(): String = this?.toString() ?: "null"
    private fun Boolean?.jsonBoolean(): String = this?.toString() ?: "null"
}
