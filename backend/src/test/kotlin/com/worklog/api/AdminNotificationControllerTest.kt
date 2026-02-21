package com.worklog.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration tests for NotificationController (T047b).
 *
 * Tests list with pagination/isRead filter, unreadCount in response,
 * markRead, markAllRead, and user-scoped notification access.
 */
class AdminNotificationControllerTest : AdminIntegrationTestBase() {

    private lateinit var userEmail: String
    private lateinit var otherEmail: String
    private lateinit var userMemberId: UUID
    private lateinit var otherMemberId: UUID
    private lateinit var notificationId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID().toString().take(8)
        userEmail = "notifuser-$suffix@test.com"
        otherEmail = "otheruser-$suffix@test.com"

        createUser(userEmail, USER_ROLE_ID, "Notification User")
        createUser(otherEmail, USER_ROLE_ID, "Other User")

        userMemberId = createMemberForUser(userEmail)
        otherMemberId = createMemberForUser(otherEmail)

        // Insert test notifications
        notificationId = UUID.randomUUID()
        baseJdbcTemplate.update(
            """INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
               VALUES (?, ?, 'DAILY_APPROVED', ?, 'Test Title', 'Test message', false, NOW())""",
            notificationId,
            userMemberId,
            UUID.randomUUID(),
        )

        // Second notification for pagination
        baseJdbcTemplate.update(
            """INSERT INTO in_app_notifications (id, recipient_member_id, type, reference_id, title, message, is_read, created_at)
               VALUES (?, ?, 'DAILY_REJECTED', ?, 'Second Title', 'Second message', true, NOW())""",
            UUID.randomUUID(),
            userMemberId,
            UUID.randomUUID(),
        )
    }

    @Test
    fun `list notifications returns 200 with content`() {
        mockMvc.perform(
            get("/api/v1/notifications")
                .with(user(userEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.unreadCount").isNumber)
    }

    @Test
    fun `list notifications with isRead filter returns 200`() {
        mockMvc.perform(
            get("/api/v1/notifications")
                .with(user(userEmail))
                .param("isRead", "false"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `list notifications with pagination returns 200`() {
        mockMvc.perform(
            get("/api/v1/notifications")
                .with(user(userEmail))
                .param("page", "0")
                .param("size", "1"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `mark read returns 200`() {
        mockMvc.perform(
            patch("/api/v1/notifications/$notificationId/read")
                .with(user(userEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `mark all read returns 200`() {
        mockMvc.perform(
            patch("/api/v1/notifications/read-all")
                .with(user(userEmail)),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `notifications are scoped to authenticated user`() {
        // Other user should not see the first user's notifications
        mockMvc.perform(
            get("/api/v1/notifications")
                .with(user(otherEmail)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unreadCount").value(0))
    }
}
