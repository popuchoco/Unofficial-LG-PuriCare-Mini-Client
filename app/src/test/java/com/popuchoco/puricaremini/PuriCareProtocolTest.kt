package com.popuchoco.puricaremini

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PuriCareProtocolTest {
    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    @Test fun getAllUsesOriginalToadEnvelopeAndId501() {
        val packet = PuriCareProtocol.getAll()
        assertArrayEquals(byteArrayOf(4, 84, 79, 65, 68, 2, 2, 0, 2, 0x7d, 0x42), packet.copyOfRange(0, 11))
        val crc = PuriCareProtocol.crc16(packet.copyOfRange(0, packet.size - 2))
        assertEquals(crc, ((packet[packet.size - 2].toInt() and 0xff) shl 8) or (packet.last().toInt() and 0xff))
    }

    @Test fun powerOnUsesId503AndSetMessage() {
        val packet = PuriCareProtocol.setBoolean(PuriCareProtocol.ID_POWER, true)
        assertArrayEquals(byteArrayOf(4, 84, 79, 65, 68, 2, 1, 0, 2, 0x7d, 0xc1.toByte()), packet.copyOfRange(0, 11))
    }

    @Test fun fanAutoUsesFanId506AndValue8() {
        val packet = PuriCareProtocol.setByte(PuriCareProtocol.ID_FAN, 8)
        assertArrayEquals(byteArrayOf(4, 84, 79, 65, 68, 2, 1, 0, 2, 0x7e, 0x88.toByte()), packet.copyOfRange(0, 11))
    }

    @Test fun turboDoesNotReplaceReportedFanLevel() {
        val snapshot = AirSnapshot().with(
            listOf(
                PuriCareProtocol.Reading(PuriCareProtocol.ID_FAN, 3),
                PuriCareProtocol.Reading(PuriCareProtocol.ID_TURBO, 1),
            ),
        )
        assertEquals(3, snapshot.fan)
        assertEquals(true, snapshot.turbo)
    }

    @Test fun crcMatchesProtocolTestVector() {
        assertEquals(0x31c3, PuriCareProtocol.crc16("123456789".toByteArray()))
    }

    @Test fun decodesRealDeviceReportWithThreePmValues() {
        val packet = hex("04 54 4F 41 50 02 04 01 07 CD 50 13 CD 08 CC C8 35 D2")

        assertEquals(
            listOf(
                PuriCareProtocol.Reading(PuriCareProtocol.ID_PM10, 0x13),
                PuriCareProtocol.Reading(PuriCareProtocol.ID_PM25, 8),
                PuriCareProtocol.Reading(PuriCareProtocol.ID_PM1, 8),
            ),
            PuriCareProtocol.decodeReport(packet),
        )
    }

    @Test fun decodesRealDeviceReportWithSingleInlinePmValue() {
        val packet = hex("04 54 4F 41 50 02 04 0B 02 CD 4A 7D 58")

        assertEquals(
            listOf(PuriCareProtocol.Reading(PuriCareProtocol.ID_PM10, 10)),
            PuriCareProtocol.decodeReport(packet),
        )
    }

    @Test fun decodesBatteryLevelAndFilterRemainingHours() {
        val packet = hex("04 54 4F 41 50 02 10 01 08 9B E0 64 00 D5 60 03 E8 00 00")

        assertEquals(
            listOf(
                PuriCareProtocol.Reading(PuriCareProtocol.ID_BATTERY, 100),
                PuriCareProtocol.Reading(PuriCareProtocol.ID_FILTER_REMAIN, 1000),
            ),
            PuriCareProtocol.decodeReport(packet),
        )
    }
}
