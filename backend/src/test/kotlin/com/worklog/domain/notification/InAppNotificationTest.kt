package com.worklog.domain.notification

import com.worklog.domain.member.MemberId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InAppNotificationTest {

    private val recipientId = MemberId.of(UUID.randomUUID())
    private val referenceId = UUID.randomUUID()

    @Nested
    @DisplayName("NotificationId")
    inner class IdTests {
        @Test
        fun `generate should create unique IDs`() {
            assertNotEquals(NotificationId.generate(), NotificationId.generate())
        }

        @Test
        fun `of UUID should wrap value`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, NotificationId.of(uuid).value())
        }

        @Test
        fun `of String should parse UUID`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid, NotificationId.of(uuid.toString()).value())
        }

        @Test
        fun `constructor should reject null`() {
            assertFailsWith<IllegalArgumentException> { NotificationId(null) }
        }

        @Test
        fun `toString should return UUID string`() {
            val uuid = UUID.randomUUID()
            assertEquals(uuid.toString(), NotificationId.of(uuid).toString())
        }
    }

    @Nested
    @DisplayName("create")
    inner class CreateTests {
        @Test
        fun `should create unread notification`() {
            val notification = InAppNotification.create(
                recipientId,
                NotificationType.DAILY_APPROVED,
                referenceId,
                "Approved",
                "Your entry was approved",
            )
            assertNotNull(notification.id)
            assertEquals(recipientId, notification.recipientMemberId)
            assertEquals(NotificationType.DAILY_APPROVED, notification.type)
            assertEquals(referenceId, notification.referenceId)
            assertEquals("Approved", notification.title)
            assertEquals("Your entry was approved", notification.message)
            assertFalse(notification.isRead)
            assertNotNull(notification.createdAt)
        }
    }

    @Nested
    @DisplayName("reconstitute")
    inner class ReconstituteTests {
        @Test
        fun `should restore all fields including read state`() {
            val id = NotificationId.generate()
            val now = Instant.now()
            val notification = InAppNotification.reconstitute(
                id,
                recipientId,
                NotificationType.SYSTEM_ALERT,
                referenceId,
                "Alert",
                "msg",
                true,
                now,
            )
            assertEquals(id, notification.id)
            assertTrue(notification.isRead)
            assertEquals(now, notification.createdAt)
        }
    }

    @Test
    fun `markRead should set isRead to true`() {
        val notification = InAppNotification.create(
            recipientId,
            NotificationType.DAILY_SUBMITTED,
            referenceId,
            "Submitted",
            "Entry submitted",
        )
        assertFalse(notification.isRead)
        notification.markRead()
        assertTrue(notification.isRead)
    }
}
