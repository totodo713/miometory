package com.worklog.infrastructure.persistence

import com.worklog.domain.role.RoleId
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class IdConverterTest {

    @Nested
    @DisplayName("UserIdToUuidConverter")
    inner class UserIdToUuidTests {
        private val converter = UserIdToUuidConverter()

        @Test
        fun `should convert UserId to UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, converter.convert(UserId.of(uuid)))
        }
    }

    @Nested
    @DisplayName("UuidToUserIdConverter")
    inner class UuidToUserIdTests {
        private val converter = UuidToUserIdConverter()

        @Test
        fun `should convert UUID to UserId`() {
            val uuid = UUID.randomUUID()
            assertEquals(UserId.of(uuid), converter.convert(uuid))
        }
    }

    @Nested
    @DisplayName("RoleIdToUuidConverter")
    inner class RoleIdToUuidTests {
        private val converter = RoleIdToUuidConverter()

        @Test
        fun `should convert RoleId to UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, converter.convert(RoleId.of(uuid)))
        }
    }

    @Nested
    @DisplayName("UuidToRoleIdConverter")
    inner class UuidToRoleIdTests {
        private val converter = UuidToRoleIdConverter()

        @Test
        fun `should convert UUID to RoleId`() {
            val uuid = UUID.randomUUID()
            assertEquals(RoleId.of(uuid), converter.convert(uuid))
        }
    }
}
