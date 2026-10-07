package com.popuchoco.puricaremini

import java.util.UUID

object PuriCareProtocol {
    val UART_SERVICE: UUID = UUID.fromString("6a400001-b5a3-f393-e0a9-e50e24dcca9e")
    val UART_TX: UUID = UUID.fromString("6a400002-b5a3-f393-e0a9-e50e24dcca9e")
    val UART_RX: UUID = UUID.fromString("6a400003-b5a3-f393-e0a9-e50e24dcca9e")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val DEVICE_INFORMATION_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val SOFTWARE_REVISION: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val ID_GET_ALL = 501
    const val ID_POWER = 503
    const val ID_FAN = 506
    const val ID_AUTO = 531
    const val ID_LIGHT = 590
    const val ID_BATTERY = 623
    const val ID_PM1 = 819
    const val ID_PM25 = 820
    const val ID_PM10 = 821
    const val ID_MONITORING = 823
    const val ID_FILTER_REMAIN = 853
    const val ID_FILTER_TOTAL = 854
    const val ID_TURBO = 863

    fun getAll(): ByteArray = frame(messageType = 2, payload = field(ID_GET_ALL, 2))
    fun setBoolean(id: Int, enabled: Boolean): ByteArray = frame(1, field(id, if (enabled) 1 else 0))
    fun setByte(id: Int, value: Int): ByteArray = frame(1, field(id, value.coerceIn(0, 15)))

    private fun field(id: Int, smallValue: Int): ByteArray {
        require(id in 0..1023)
        val packed = (id shl 6) or (smallValue and 0x0f)
        return byteArrayOf((packed shr 8).toByte(), packed.toByte())
    }

    private fun frame(messageType: Int, payload: ByteArray): ByteArray {
        val body = byteArrayOf(
            4, 'T'.code.toByte(), 'O'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(),
            2, messageType.toByte(), 0, payload.size.toByte(),
        ) + payload
        val crc = crc16(body)
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    fun crc16(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            var value = ((((crc shl 8) or (crc ushr 8)) and 0xffff) xor (byte.toInt() and 0xff))
            value = value xor ((value and 0xff) ushr 4)
            value = value xor ((value shl 12) and 0xffff)
            crc = value xor (((value and 0xff) shl 5) and 0xffff)
        }
        return crc and 0xffff
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }

    data class Reading(val id: Int, val value: Int)

    /** Decodes device reports. Known scalar values use a 10-bit ID + 2-bit length + 4-bit inline value. */
    fun decodeReport(packet: ByteArray): List<Reading> {
        if (packet.size < 11) return emptyList()
        val expectedCrc = ((packet[packet.size - 2].toInt() and 0xff) shl 8) or (packet.last().toInt() and 0xff)
        if (crc16(packet.copyOfRange(0, packet.size - 2)) != expectedCrc) return emptyList()
        val addressLength = packet[0].toInt() and 0xff
        if (addressLength <= 0 || packet.size < addressLength + 7) return emptyList()
        val address = packet.copyOfRange(1, 1 + addressLength).toString(Charsets.US_ASCII)
        val headerStart = 1 + addressLength
        if (address != "TOAP" && address != "TOAD") return emptyList()
        // protocol/version, message type, sequence/reserved, length, payload.
        val messageTypeIndex = headerStart + 1
        val payloadLengthIndex = headerStart + 3
        val payloadStart = headerStart + 4
        if (payloadStart > packet.size - 2 || payloadLengthIndex >= packet.size) return emptyList()
        val messageType = packet[messageTypeIndex].toInt() and 0xff
        if (messageType != 4 && messageType != 16) return emptyList()
        val payloadLength = packet[payloadLengthIndex].toInt() and 0xff
        val end = minOf(payloadStart + payloadLength, packet.size - 2)
        val result = mutableListOf<Reading>()
        var index = payloadStart
        while (index + 1 < end) {
            val header = ((packet[index].toInt() and 0xff) shl 8) or (packet[index + 1].toInt() and 0xff)
            val id = header ushr 6
            val format = (header ushr 4) and 0x03
            val inline = header and 0x0f
            index += 2
            val bytes = format
            var value = when {
                bytes == 0 -> inline
                index + bytes <= end -> {
                    var v = 0
                    repeat(bytes.coerceAtMost(4)) { offset -> v = (v shl 8) or (packet[index + offset].toInt() and 0xff) }
                    index += bytes
                    v
                }
                else -> break
            }
            // Battery status is [levelPercent, chargeState]; the UI value is the first byte.
            if (id == ID_BATTERY && bytes >= 1) value = packet[index - bytes].toInt() and 0xff
            result += Reading(id, value)
        }
        return result
    }
}

data class AirSnapshot(
    val pm1: Int? = null,
    val pm25: Int? = null,
    val pm10: Int? = null,
    val battery: Int? = null,
    val power: Boolean? = null,
    val fan: Int? = null,
    val turbo: Boolean? = null,
    val auto: Boolean? = null,
    val sensorAlwaysOn: Boolean? = null,
    val lightLevel: Int? = null,
    val filterRemaining: Int? = null,
    val filterTotal: Int? = null,
    val updatedAt: Long? = null,
)

fun AirSnapshot.with(readings: List<PuriCareProtocol.Reading>): AirSnapshot {
    var next = this
    readings.forEach { reading ->
        next = when (reading.id) {
            PuriCareProtocol.ID_PM1 -> next.copy(pm1 = reading.value)
            PuriCareProtocol.ID_PM25 -> next.copy(pm25 = reading.value)
            PuriCareProtocol.ID_PM10 -> next.copy(pm10 = reading.value)
            PuriCareProtocol.ID_BATTERY -> next.copy(battery = reading.value.coerceIn(0, 100))
            PuriCareProtocol.ID_POWER -> next.copy(power = reading.value != 0)
            PuriCareProtocol.ID_FAN -> next.copy(fan = reading.value)
            PuriCareProtocol.ID_TURBO -> next.copy(turbo = reading.value != 0)
            PuriCareProtocol.ID_AUTO -> next.copy(auto = reading.value != 0)
            PuriCareProtocol.ID_MONITORING -> next.copy(sensorAlwaysOn = reading.value != 0)
            PuriCareProtocol.ID_LIGHT -> next.copy(lightLevel = reading.value.coerceIn(0, 4))
            PuriCareProtocol.ID_FILTER_REMAIN -> next.copy(filterRemaining = reading.value)
            PuriCareProtocol.ID_FILTER_TOTAL -> next.copy(filterTotal = reading.value)
            else -> next
        }
    }
    return if (readings.isEmpty()) next else next.copy(updatedAt = System.currentTimeMillis())
}

fun AirSnapshot.withLocalLightLevel(level: Int): AirSnapshot =
    copy(lightLevel = level.coerceIn(0, 4), updatedAt = System.currentTimeMillis())
