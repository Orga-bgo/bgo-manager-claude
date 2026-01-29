package com.mgomanager.app.domain.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for IdGenerator.
 */
class IdGeneratorTest {

    @Test
    fun `generateDeviceName returns correct format`() {
        val accountName = "TestAccount"
        val result = IdGenerator.generateDeviceName(accountName)

        assertEquals("TestAccount's Device", result)
    }

    @Test
    fun `generateDeviceName handles special characters`() {
        val accountName = "Test_Account-123"
        val result = IdGenerator.generateDeviceName(accountName)

        assertEquals("Test_Account-123's Device", result)
    }

    @Test
    fun `generateAndroidId returns 16 hex characters`() {
        val result = IdGenerator.generateAndroidId()

        assertEquals(16, result.length)
        assertTrue(result.matches(Regex("^[a-f0-9]{16}$")))
    }

    @Test
    fun `generateAndroidId returns lowercase hex`() {
        val result = IdGenerator.generateAndroidId()

        assertEquals(result, result.lowercase())
    }

    @Test
    fun `generateAndroidId generates unique values`() {
        val results = (1..100).map { IdGenerator.generateAndroidId() }.toSet()

        // All 100 generated IDs should be unique
        assertEquals(100, results.size)
    }

    @Test
    fun `generateAppSetId returns valid UUID format`() {
        val result = IdGenerator.generateAppSetId()

        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(result.matches(Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")))
    }

    @Test
    fun `generateAppSetId generates unique values`() {
        val results = (1..100).map { IdGenerator.generateAppSetId() }.toSet()

        assertEquals(100, results.size)
    }

    @Test
    fun `generateGsfId returns 16 hex characters`() {
        val result = IdGenerator.generateGsfId()

        assertEquals(16, result.length)
        assertTrue(result.matches(Regex("^[a-f0-9]{16}$")))
    }

    @Test
    fun `generateGsfId generates unique values`() {
        val results = (1..100).map { IdGenerator.generateGsfId() }.toSet()

        // Most should be unique (allowing for rare collisions)
        assertTrue(results.size >= 95)
    }

    @Test
    fun `generateGaid returns valid UUID format`() {
        val result = IdGenerator.generateGaid()

        assertTrue(result.matches(Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")))
    }

    @Test
    fun `isValidAndroidId accepts valid 16 hex chars`() {
        assertTrue(IdGenerator.isValidAndroidId("abcd1234567890ef"))
        assertTrue(IdGenerator.isValidAndroidId("0000000000000000"))
        assertTrue(IdGenerator.isValidAndroidId("ffffffffffffffff"))
        assertTrue(IdGenerator.isValidAndroidId("ABCD1234567890EF"))
    }

    @Test
    fun `isValidAndroidId rejects invalid inputs`() {
        assertFalse(IdGenerator.isValidAndroidId(""))
        assertFalse(IdGenerator.isValidAndroidId("abc")) // too short
        assertFalse(IdGenerator.isValidAndroidId("abcd1234567890efg")) // too long
        assertFalse(IdGenerator.isValidAndroidId("ghij1234567890ef")) // invalid hex chars
        assertFalse(IdGenerator.isValidAndroidId("abcd-1234-5678")) // contains dashes
    }

    @Test
    fun `isValidUuid accepts valid UUIDs`() {
        assertTrue(IdGenerator.isValidUuid("550e8400-e29b-41d4-a716-446655440000"))
        assertTrue(IdGenerator.isValidUuid("00000000-0000-0000-0000-000000000000"))
        assertTrue(IdGenerator.isValidUuid(IdGenerator.generateAppSetId()))
    }

    @Test
    fun `isValidUuid rejects invalid inputs`() {
        assertFalse(IdGenerator.isValidUuid(""))
        assertFalse(IdGenerator.isValidUuid("not-a-uuid"))
        assertFalse(IdGenerator.isValidUuid("550e8400e29b41d4a716446655440000")) // no dashes
        assertFalse(IdGenerator.isValidUuid("550e8400-e29b-41d4-a716-44665544000")) // too short
    }

    @Test
    fun `isValidGsfId accepts valid 16 hex chars`() {
        assertTrue(IdGenerator.isValidGsfId("abcd1234567890ef"))
        assertTrue(IdGenerator.isValidGsfId("ABCD1234567890EF"))
    }

    @Test
    fun `isValidGsfId rejects invalid inputs`() {
        assertFalse(IdGenerator.isValidGsfId(""))
        assertFalse(IdGenerator.isValidGsfId("abc"))
        assertFalse(IdGenerator.isValidGsfId("abcd-1234-5678-90ef"))
    }

    @Test
    fun `generateAllIds returns all required fields`() {
        val accountName = "TestAccount"
        val result = IdGenerator.generateAllIds(accountName)

        assertEquals("TestAccount's Device", result.deviceName)
        assertTrue(IdGenerator.isValidAndroidId(result.androidId))
        assertTrue(IdGenerator.isValidUuid(result.appSetIdApp))
        assertTrue(IdGenerator.isValidUuid(result.appSetIdDev))
        assertTrue(IdGenerator.isValidGsfId(result.gsfId))
        assertTrue(IdGenerator.isValidUuid(result.gaid))
    }

    @Test
    fun `generateAllIds generates different app and dev set IDs`() {
        val result = IdGenerator.generateAllIds("TestAccount")

        assertNotEquals(result.appSetIdApp, result.appSetIdDev)
    }

    @Test
    fun `generated Android ID is valid according to validation`() {
        repeat(100) {
            val androidId = IdGenerator.generateAndroidId()
            assertTrue("Android ID '$androidId' should be valid", IdGenerator.isValidAndroidId(androidId))
        }
    }

    @Test
    fun `generated GSF ID is valid according to validation`() {
        repeat(100) {
            val gsfId = IdGenerator.generateGsfId()
            assertTrue("GSF ID '$gsfId' should be valid", IdGenerator.isValidGsfId(gsfId))
        }
    }

    @Test
    fun `generated App Set ID is valid according to validation`() {
        repeat(100) {
            val appSetId = IdGenerator.generateAppSetId()
            assertTrue("App Set ID '$appSetId' should be valid", IdGenerator.isValidUuid(appSetId))
        }
    }

    @Test
    fun `generated GAID is valid according to validation`() {
        repeat(100) {
            val gaid = IdGenerator.generateGaid()
            assertTrue("GAID '$gaid' should be valid", IdGenerator.isValidUuid(gaid))
        }
    }
}
