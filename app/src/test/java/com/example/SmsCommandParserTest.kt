package com.example

import com.example.security.SmsCommandReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCommandParserTest {

    @Test
    fun `parses unique per-device and global secret commands`() {
        assertEquals(
            "LOCK",
            SmsCommandReceiver.parseSmsCommand("#LOCK_SG-101A#", "SG", "2468", "#LOCK_PHONE#", "SG-101A")
        )
        assertEquals(
            "LOCATE",
            SmsCommandReceiver.parseSmsCommand("SG-101A#LOCATE#2468", "SG", "2468", "#LOCK_PHONE#", "SG-101A")
        )
        assertEquals(
            "LOCK",
            SmsCommandReceiver.parseSmsCommand("#LOCK_PHONE#", "SG", "2468", "#LOCK_PHONE#", "SG-101A")
        )
        assertEquals("SIREN", SmsCommandReceiver.parseSmsCommand("SIREN#2468", "SG", "2468"))
        assertEquals("GUARD", SmsCommandReceiver.parseSmsCommand("SG#GUARD#2468", "SG", "2468"))
    }

    @Test
    fun `rejects commands addressed to another device id or wrong pin`() {
        assertNull(
            SmsCommandReceiver.parseSmsCommand("#LOCK_SG-999Z#", "SG", "2468", "#LOCK_PHONE#", "SG-101A")
        )
        assertNull(
            SmsCommandReceiver.parseSmsCommand("SG-999Z#LOCATE#2468", "SG", "2468", "#LOCK_PHONE#", "SG-101A")
        )
        assertNull(SmsCommandReceiver.parseSmsCommand("SG#LOCK#0000", "SG", "2468"))
        assertNull(SmsCommandReceiver.parseSmsCommand("Hey are you home?", "SG", "2468"))
    }
}
