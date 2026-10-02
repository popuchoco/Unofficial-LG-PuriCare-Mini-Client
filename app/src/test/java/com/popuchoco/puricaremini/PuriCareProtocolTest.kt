package com.popuchoco.puricaremini

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PuriCareProtocolTest {
    @Test fun getAllUsesOriginalToadEnvelopeAndId501() {
        val packet = PuriCareProtocol.getAll()
        assertArrayEquals(byteArrayOf(4, 84, 79, 65, 68, 2, 0, 2, 0x7d, 0x42), packet.copyOfRange(0, 10))
        val crc = PuriCareProtocol.crc16(packet.copyOfRange(0, packet.size - 2))
        assertEquals(crc, ((packet[packet.size - 2].toInt() and 0xff) shl 8) or (packet.last().toInt() and 0xff))
    }

    @Test fun powerOnUsesId503AndSetMessage() {
        val packet = PuriCareProtocol.setBoolean(PuriCareProtocol.ID_POWER, true)
        assertArrayEquals(byteArrayOf(4, 84, 79, 65, 68, 1, 0, 2, 0x7d, 0xc1.toByte()), packet.copyOfRange(0, 10))
    }

    @Test fun crcMatchesProtocolTestVector() {
        assertEquals(0x31c3, PuriCareProtocol.crc16("123456789".toByteArray()))
    }
}
