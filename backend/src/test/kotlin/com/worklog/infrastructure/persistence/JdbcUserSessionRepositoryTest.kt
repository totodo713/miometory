package com.worklog.infrastructure.persistence

import com.worklog.IntegrationTestBase
import com.worklog.domain.session.UserSession
import com.worklog.domain.tenant.TenantId
import com.worklog.domain.user.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcUserSessionRepositoryTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: JdbcUserSessionRepository

    private lateinit var userId: UserId
    private val roleId = "00000000-0000-0000-0000-000000000002" // USER role from seed

    @BeforeEach
    fun setUp() {
        val userUuid = UUID.randomUUID()
        userId = UserId.of(userUuid)
        // Create a user for FK constraint
        baseJdbcTemplate.update(
            """INSERT INTO users (id, email, hashed_password, name, role_id, account_status, failed_login_attempts)
               VALUES (?, ?, 'hashed', ?, ?::UUID, 'active', 0)
               ON CONFLICT (id) DO NOTHING""",
            userUuid,
            "session-test-$userUuid@example.com",
            "Session Test User",
            roleId,
        )
    }

    @Test
    fun `save and findBySessionId should round-trip a session`() {
        val session = UserSession.create(userId, "192.168.1.1", "TestAgent/1.0", 30)
        repository.save(session)

        val found = repository.findBySessionId(session.sessionId.toString())

        assertTrue(found.isPresent)
        val loaded = found.get()
        assertEquals(session.sessionId, loaded.sessionId)
        assertEquals(userId, loaded.userId)
        assertEquals("192.168.1.1", loaded.ipAddress)
        assertEquals("TestAgent/1.0", loaded.userAgent)
        assertNotNull(loaded.createdAt)
        assertNotNull(loaded.expiresAt)
    }

    @Test
    fun `findBySessionId should return empty for non-existent session`() {
        val result = repository.findBySessionId(UUID.randomUUID().toString())
        assertFalse(result.isPresent)
    }

    @Test
    fun `findByUserId should return sessions for user`() {
        val session1 = UserSession.create(userId, "10.0.0.1", "Agent1", 30)
        val session2 = UserSession.create(userId, "10.0.0.2", "Agent2", 60)
        repository.save(session1)
        repository.save(session2)

        val sessions = repository.findByUserId(userId)

        assertTrue(sessions.size >= 2)
    }

    @Test
    fun `deleteBySessionId should remove session`() {
        val session = UserSession.create(userId, "10.0.0.1", "AgentDel", 30)
        repository.save(session)
        assertTrue(repository.findBySessionId(session.sessionId.toString()).isPresent)

        repository.deleteBySessionId(session.sessionId.toString())

        assertFalse(repository.findBySessionId(session.sessionId.toString()).isPresent)
    }

    @Test
    fun `deleteExpiredSessions should remove expired sessions`() {
        // Create an already-expired session
        val past = Instant.now().minusSeconds(3600)
        val expiredSession = UserSession(
            UUID.randomUUID(),
            userId,
            "10.0.0.1",
            "ExpiredAgent",
            past.minusSeconds(7200),
            past,
            past.minusSeconds(3600),
        )
        repository.save(expiredSession)

        val deleted = repository.deleteExpiredSessions(Instant.now())

        assertTrue(deleted >= 1)
    }

    @Test
    fun `findExpiredSessions should return expired sessions`() {
        val past = Instant.now().minusSeconds(3600)
        val expiredSession = UserSession(
            UUID.randomUUID(),
            userId,
            "10.0.0.1",
            "ExpiredFindAgent",
            past.minusSeconds(7200),
            past,
            past.minusSeconds(3600),
        )
        repository.save(expiredSession)

        val expired = repository.findExpiredSessions(Instant.now())

        assertTrue(expired.isNotEmpty())
    }

    @Test
    fun `deleteByUserId should remove all sessions for user`() {
        val session = UserSession.create(userId, "10.0.0.1", "AgentDelUser", 30)
        repository.save(session)

        repository.deleteByUserId(userId)

        val sessions = repository.findByUserId(userId)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `count should return total sessions`() {
        val count = repository.count()
        assertTrue(count >= 0)
    }

    @Test
    fun `save should upsert on conflict`() {
        val session = UserSession.create(userId, "10.0.0.1", "UpsertAgent", 30)
        repository.save(session)

        // Touch and save again - should update via ON CONFLICT
        session.touch(60)
        repository.save(session)

        val found = repository.findBySessionId(session.sessionId.toString())
        assertTrue(found.isPresent)
    }

    @Test
    fun `deleteAll should remove all sessions`() {
        val session = UserSession.create(userId, "10.0.0.1", "AgentDelAll", 30)
        repository.save(session)
        assertTrue(repository.count() >= 1)

        repository.deleteAll()

        assertEquals(0, repository.count())
    }

    @Test
    fun `updateSelectedTenant should set tenant and return updated row count`() {
        val session = UserSession.create(userId, "10.0.0.1", "TenantAgent", 30)
        repository.save(session)

        val tenantId = TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        val updated = repository.updateSelectedTenant(session.sessionId, tenantId, userId)

        assertEquals(1, updated)
        val found = repository.findBySessionId(session.sessionId.toString())
        assertTrue(found.isPresent)
        assertNotNull(found.get().selectedTenantId)
        assertEquals(tenantId, found.get().selectedTenantId)
    }

    @Test
    fun `updateSelectedTenant should clear tenant when null`() {
        val session = UserSession.create(userId, "10.0.0.1", "ClearTenantAgent", 30)
        repository.save(session)

        // First set a tenant
        val tenantId = TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        repository.updateSelectedTenant(session.sessionId, tenantId, userId)

        // Then clear it
        val updated = repository.updateSelectedTenant(session.sessionId, null, userId)
        assertEquals(1, updated)

        val found = repository.findBySessionId(session.sessionId.toString())
        assertTrue(found.isPresent)
        assertNull(found.get().selectedTenantId)
    }

    @Test
    fun `updateSelectedTenant should return 0 for wrong user`() {
        val session = UserSession.create(userId, "10.0.0.1", "WrongUserAgent", 30)
        repository.save(session)

        val otherUserId = UserId.of(UUID.randomUUID())
        val tenantId = TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        val updated = repository.updateSelectedTenant(session.sessionId, tenantId, otherUserId)

        assertEquals(0, updated)
    }

    @Test
    fun `save and findBySessionId should round-trip session with selectedTenantId`() {
        val tenantId = TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        val now = Instant.now()
        val session = UserSession(
            UUID.randomUUID(),
            userId,
            "10.0.0.1",
            "TenantRoundTrip",
            now,
            now.plusSeconds(1800),
            now,
            tenantId,
        )
        repository.save(session)

        val found = repository.findBySessionId(session.sessionId.toString())

        assertTrue(found.isPresent)
        assertEquals(tenantId, found.get().selectedTenantId)
    }
}
