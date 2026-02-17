package com.worklog.infrastructure.persistence

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for JSONB and INET reading converters (T008).
 *
 * Verifies conversion from PostgreSQL PGobject to Java String
 * for both JSONB and INET column types. Writing is handled via
 * explicit SQL casting in repository @Query methods.
 */
class JsonbInetConverterTest {

    @Nested
    inner class JsonbToStringReadingConverterTest {
        private val converter = JsonbToStringReadingConverter()

        @Test
        fun `should convert PGobject with jsonb type to String`() {
            val pgObject = PGobject().apply {
                type = "jsonb"
                value = """{"key":"value"}"""
            }
            val result = converter.convert(pgObject)

            assertEquals("""{"key":"value"}""", result)
        }

        @Test
        fun `should convert PGobject with nested JSON`() {
            val json = """{"user":{"name":"test","roles":["ADMIN","USER"]}}"""
            val pgObject = PGobject().apply {
                type = "jsonb"
                value = json
            }
            val result = converter.convert(pgObject)

            assertEquals(json, result)
        }

        @Test
        fun `should convert PGobject with JSON array`() {
            val json = """[1,2,3]"""
            val pgObject = PGobject().apply {
                type = "jsonb"
                value = json
            }
            val result = converter.convert(pgObject)

            assertEquals(json, result)
        }

        @Test
        fun `should convert PGobject with null value`() {
            val pgObject = PGobject().apply {
                type = "jsonb"
                value = null
            }
            val result = converter.convert(pgObject)

            assertNull(result)
        }

        @Test
        fun `should convert empty JSON object`() {
            val pgObject = PGobject().apply {
                type = "jsonb"
                value = "{}"
            }
            val result = converter.convert(pgObject)

            assertEquals("{}", result)
        }
    }

    @Nested
    inner class InetToStringReadingConverterTest {
        private val converter = InetToStringReadingConverter()

        @Test
        fun `should convert PGobject with IPv4 inet to String`() {
            val pgObject = PGobject().apply {
                type = "inet"
                value = "192.168.1.100"
            }
            val result = converter.convert(pgObject)

            assertEquals("192.168.1.100", result)
        }

        @Test
        fun `should convert PGobject with IPv6 inet to String`() {
            val pgObject = PGobject().apply {
                type = "inet"
                value = "::1"
            }
            val result = converter.convert(pgObject)

            assertEquals("::1", result)
        }

        @Test
        fun `should convert PGobject with full IPv6 address`() {
            val pgObject = PGobject().apply {
                type = "inet"
                value = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
            }
            val result = converter.convert(pgObject)

            assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", result)
        }

        @Test
        fun `should convert PGobject with loopback IPv4`() {
            val pgObject = PGobject().apply {
                type = "inet"
                value = "127.0.0.1"
            }
            val result = converter.convert(pgObject)

            assertEquals("127.0.0.1", result)
        }

        @Test
        fun `should convert PGobject with null value`() {
            val pgObject = PGobject().apply {
                type = "inet"
                value = null
            }
            val result = converter.convert(pgObject)

            assertNull(result)
        }
    }
}
