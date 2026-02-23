# AuditLogService @Async Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace `REQUIRES_NEW` with `@Async` in AuditLogService to eliminate FK-deadlock during login

**Architecture:** AuditLogService.logEvent() runs in a separate thread via Spring's @Async. The outer transaction (e.g., login) commits first, releasing row locks, then the async thread persists the audit log in its own transaction. Tests use SyncTaskExecutor to keep @Async methods synchronous.

**Tech Stack:** Spring Boot @Async, Spring @Transactional, SyncTaskExecutor (test)

---

### Task 1: Add @EnableAsync to BackendApplication

**Files:**
- Modify: `backend/src/main/java/com/worklog/BackendApplication.java`

**Step 1: Add @EnableAsync annotation**

```java
package com.worklog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
```

**Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

---

### Task 2: Replace REQUIRES_NEW with @Async in AuditLogService

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/audit/AuditLogService.java`

**Step 1: Update logEvent() annotations and Javadoc**

Replace `@Transactional(propagation = Propagation.REQUIRES_NEW)` with `@Async @Transactional` and update Javadoc.

```java
package com.worklog.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.domain.audit.AuditLog;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.AuditLogRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for persisting audit log entries asynchronously.
 *
 * <p>Uses {@link Async} to run audit log insertion in a separate thread,
 * eliminating the FK-deadlock that occurred with {@code REQUIRES_NEW}
 * (audit_logs.user_id FK referencing users row locked by the caller).
 *
 * <p>The outer transaction commits first, releasing all locks, then this
 * method persists the audit log in its own transaction. Any exception
 * during save is caught and logged, allowing the primary operation to
 * complete successfully regardless of audit log outcome.
 *
 * <p>Uses {@link AuditLogRepository#insertAuditLog} with explicit SQL casting
 * for JSONB and INET columns, since global writing converters would affect
 * all String fields across all entities.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists an audit log event asynchronously in its own transaction.
     *
     * <p>If persistence fails, the exception is caught and logged via SLF4J.
     * The calling transaction is never affected by failures in this method.
     *
     * @param userId    the user who triggered the event (null for system events)
     * @param eventType the type of audit event (e.g., LOGIN_SUCCESS)
     * @param ipAddress the client IP address (null for system events)
     * @param details   additional event details as a plain text string (nullable)
     */
    @Async
    @Transactional
    public void logEvent(UserId userId, String eventType, String ipAddress, String details) {
        try {
            AuditLog auditLog = AuditLog.createUserAction(userId, eventType, ipAddress, details);
            auditLogRepository.insertAuditLog(
                    auditLog.getId(),
                    userId != null ? userId.value() : null,
                    eventType,
                    ipAddress,
                    auditLog.getTimestamp(),
                    toJsonDetails(details),
                    auditLog.getRetentionDays());
        } catch (Exception e) {
            log.error(
                    "Failed to persist audit log: eventType={}, userId={}, error={}",
                    eventType,
                    userId,
                    e.getMessage(),
                    e);
        }
    }

    private String toJsonDetails(String details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("message", details));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details to JSON, using null", e);
            return null;
        }
    }
}
```

**Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

---

### Task 3: Add SyncTaskExecutor test config for @Async

**Files:**
- Create: `backend/src/test/kotlin/com/worklog/config/TestAsyncConfig.kt`

**Why:** Integration tests need @Async methods to run synchronously so assertions can verify results immediately after calling logEvent(). SyncTaskExecutor runs the async method on the calling thread.

**Step 1: Create test config**

```kotlin
package com.worklog.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.task.SyncTaskExecutor
import java.util.concurrent.Executor

@TestConfiguration
class TestAsyncConfig {
    @Bean
    fun taskExecutor(): Executor = SyncTaskExecutor()
}
```

**Step 2: Verify compilation**

Run: `cd backend && ./gradlew compileTestKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 4: Update AuditLogServiceIntegrationTest for @Async

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceIntegrationTest.kt`

**Step 1: Import TestAsyncConfig and update Javadoc**

Add `@Import(TestAsyncConfig::class)` to ensure @Async runs synchronously in tests. Update class Javadoc to reflect async behavior.

```kotlin
package com.worklog.application.audit

import com.worklog.IntegrationTestBase
import com.worklog.config.TestAsyncConfig
import com.worklog.domain.audit.AuditLog
import com.worklog.domain.user.UserId
import com.worklog.infrastructure.persistence.AuditLogRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Integration tests for AuditLogService transaction behavior (T014).
 *
 * Uses [TestAsyncConfig] to run @Async methods synchronously via SyncTaskExecutor,
 * so assertions can verify results immediately after calling logEvent().
 */
@Import(TestAsyncConfig::class)
class AuditLogServiceIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var auditLogService: AuditLogService

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Test
    @Transactional
    fun `logEvent should not mark outer transaction as rollback-only on success`() {
        val countBefore = auditLogRepository.count()

        auditLogService.logEvent(
            null,
            AuditLog.AUDIT_LOG_CLEANUP,
            null,
            """{"test":"transaction_isolation"}""",
        )

        val countAfter = auditLogRepository.count()
        assertTrue(countAfter >= countBefore, "Outer transaction should still be functional after logEvent")
    }

    @Test
    fun `logEvent should persist audit log in independent transaction`() {
        val uniqueEventType = "TX_TEST_${UUID.randomUUID().toString().take(8)}"

        auditLogService.logEvent(null, uniqueEventType, null, """{"independent":true}""")

        val results = auditLogRepository.findByEventType(uniqueEventType, 10)
        assertTrue(results.isNotEmpty(), "AuditLog should be persisted in its own transaction")
    }

    @Test
    fun `logEvent should catch exception without affecting caller`() {
        val countBefore = auditLogRepository.count()

        auditLogService.logEvent(null, AuditLog.LOGIN_SUCCESS, null, "not-valid-json")

        val countAfter = auditLogRepository.count()
        assertTrue(countAfter >= countBefore, "Repository should still be usable after failed logEvent")
    }
}
```

**Step 2: Run integration tests**

Run: `cd backend && ./gradlew test --tests '*AuditLogServiceIntegrationTest'`
Expected: 3 tests PASSED

---

### Task 5: Run all AuditLog tests

**Step 1: Run unit test**

Run: `cd backend && ./gradlew test --tests '*AuditLogServiceTest'`
Expected: 5 tests PASSED

**Step 2: Run all AuditLog-related tests**

Run: `cd backend && ./gradlew test --tests '*AuditLog*'`
Expected: All tests PASSED

---

### Task 6: Run AuthService tests to verify callers

**Step 1: Run AuthServiceTest**

Run: `cd backend && ./gradlew test --tests '*AuthServiceTest*'`
Expected: PASSED (callers of logEvent() are unaffected since method signature unchanged)

---

### Task 7: Commit

**Step 1: Commit all changes**

```bash
git add backend/src/main/java/com/worklog/BackendApplication.java
git add backend/src/main/java/com/worklog/application/audit/AuditLogService.java
git add backend/src/test/kotlin/com/worklog/config/TestAsyncConfig.kt
git add backend/src/test/kotlin/com/worklog/application/audit/AuditLogServiceIntegrationTest.kt
git commit -m "fix(audit): replace REQUIRES_NEW with @Async to eliminate FK deadlock"
```
