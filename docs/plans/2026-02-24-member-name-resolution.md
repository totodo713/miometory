# Member Name Resolution Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Resolve member/reviewer display names from the repository at 4 TODO locations across 3 controllers (Issue #27).

**Architecture:** Add `findDisplayNamesByIds` batch method to `JdbcMemberRepository` for N+1 avoidance in RejectionController. Use existing `findById` for single lookups in CalendarController and ApprovalController. Inject `JdbcMemberRepository` into all 3 controllers.

**Tech Stack:** Spring Boot 3.5.9, Java 21, JdbcTemplate, JUnit 5, Testcontainers

---

### Task 1: Add `findDisplayNamesByIds` to JdbcMemberRepository

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`

**Step 1: Add imports**

Add to the import block of `JdbcMemberRepository.java`:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
```

Note: `HashMap`, `Map`, `Set` are new. `Collectors` is new. `List`, `Optional`, `UUID` already exist.

**Step 2: Add the batch method**

Insert the following method after the existing `findByOrganization` method (after line 286, before `MemberRowMapper`):

```java
/**
 * Batch lookup of display names by member IDs.
 * Returns a map from MemberId to display name.
 * IDs not found in the database are omitted from the result.
 *
 * @param ids Set of member IDs to look up
 * @return Map from MemberId to display name (missing IDs are omitted)
 */
public Map<MemberId, String> findDisplayNamesByIds(Set<MemberId> ids) {
    if (ids.isEmpty()) {
        return Map.of();
    }

    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
    String sql = "SELECT id, display_name FROM members WHERE id IN (" + placeholders + ")";
    Object[] params = ids.stream().map(id -> id.value()).toArray();

    Map<MemberId, String> result = new HashMap<>();
    jdbcTemplate.query(sql, rs -> {
        result.put(MemberId.of(rs.getObject("id", UUID.class)), rs.getString("display_name"));
    });

    // Note: jdbcTemplate.query(sql, RowCallbackHandler) does not accept varargs params.
    // Use the overload with Object[] params instead:
    // jdbcTemplate.query(sql, rs -> { ... }, params);
    // Actually, let's use the correct API.
    return result;
}
```

**CORRECTION — Use the correct JdbcTemplate API:**

```java
public Map<MemberId, String> findDisplayNamesByIds(Set<MemberId> ids) {
    if (ids.isEmpty()) {
        return Map.of();
    }

    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
    String sql = "SELECT id, display_name FROM members WHERE id IN (" + placeholders + ")";
    Object[] params = ids.stream().map(id -> id.value()).toArray();

    List<Map.Entry<MemberId, String>> rows = jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                    Map.entry(MemberId.of(rs.getObject("id", UUID.class)), rs.getString("display_name")),
            params);

    Map<MemberId, String> result = new HashMap<>();
    for (var entry : rows) {
        result.put(entry.getKey(), entry.getValue());
    }
    return result;
}
```

**Step 3: Run existing tests to ensure no regression**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.**" --info`
Expected: All existing repository tests PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java
git commit -m "feat: add findDisplayNamesByIds batch method to JdbcMemberRepository"
```

---

### Task 2: Write integration test for `findDisplayNamesByIds`

**Files:**
- Create: `backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java`

**Step 1: Write the test class**

```java
package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.member.MemberId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JdbcMemberRepository")
class JdbcMemberRepositoryTest extends IntegrationTestBase {

    @Autowired
    private JdbcMemberRepository memberRepository;

    @Test
    @DisplayName("findDisplayNamesByIds should return empty map for empty input")
    void findDisplayNamesByIds_emptyInput_returnsEmptyMap() {
        Map<MemberId, String> result = memberRepository.findDisplayNamesByIds(Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findDisplayNamesByIds should return display names for existing members")
    void findDisplayNamesByIds_existingMembers_returnsNames() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        createTestMember(id1, "member1-" + id1 + "@example.com");
        createTestMember(id2, "member2-" + id2 + "@example.com");

        // Act
        Map<MemberId, String> result = memberRepository.findDisplayNamesByIds(
                Set.of(MemberId.of(id1), MemberId.of(id2)));

        // Assert
        assertEquals(2, result.size());
        assertEquals("Test User " + id1, result.get(MemberId.of(id1)));
        assertEquals("Test User " + id2, result.get(MemberId.of(id2)));
    }

    @Test
    @DisplayName("findDisplayNamesByIds should omit non-existent member IDs")
    void findDisplayNamesByIds_nonExistentId_omitsFromResult() {
        // Arrange
        UUID existingId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        createTestMember(existingId, "existing-" + existingId + "@example.com");

        // Act
        Map<MemberId, String> result = memberRepository.findDisplayNamesByIds(
                Set.of(MemberId.of(existingId), MemberId.of(nonExistentId)));

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test User " + existingId, result.get(MemberId.of(existingId)));
        assertNull(result.get(MemberId.of(nonExistentId)));
    }

    @Test
    @DisplayName("findDisplayNamesByIds should handle single ID")
    void findDisplayNamesByIds_singleId_returnsName() {
        // Arrange
        UUID id = UUID.randomUUID();
        createTestMember(id, "single-" + id + "@example.com");

        // Act
        Map<MemberId, String> result =
                memberRepository.findDisplayNamesByIds(Set.of(MemberId.of(id)));

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test User " + id, result.get(MemberId.of(id)));
    }
}
```

Note: `createTestMember` is inherited from `IntegrationTestBase` (line 109). It inserts a member with display name `"Test User $memberId"`.

**Step 2: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.repository.JdbcMemberRepositoryTest" --info`
Expected: 4 tests PASS.

**Step 3: Commit**

```bash
git add backend/src/test/java/com/worklog/infrastructure/repository/JdbcMemberRepositoryTest.java
git commit -m "test: add integration tests for findDisplayNamesByIds"
```

---

### Task 3: Resolve member name in CalendarController

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/CalendarController.java`

**Step 1: Add import and inject repository**

Add import:
```java
import com.worklog.domain.member.Member;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
```

Add field and update constructor. Current constructor (lines 40-49):
```java
public CalendarController(
        MonthlyCalendarProjection calendarProjection,
        MonthlySummaryProjection summaryProjection,
        JdbcApprovalRepository approvalRepository,
        JdbcDailyRejectionLogRepository dailyRejectionLogRepository) {
```

Change to:
```java
private final JdbcMemberRepository memberRepository;

public CalendarController(
        MonthlyCalendarProjection calendarProjection,
        MonthlySummaryProjection summaryProjection,
        JdbcApprovalRepository approvalRepository,
        JdbcDailyRejectionLogRepository dailyRejectionLogRepository,
        JdbcMemberRepository memberRepository) {
    ...
    this.memberRepository = memberRepository;
}
```

**Step 2: Fix TODO at line 101 (reviewer name in MonthlyApprovalSummary)**

Replace:
```java
null, // TODO: Fetch reviewer name from member repository
```
With:
```java
a.getReviewedBy() != null
        ? memberRepository
                .findById(a.getReviewedBy())
                .map(Member::getDisplayName)
                .orElse(null)
        : null,
```

**Step 3: Fix TODO at line 145 (member name in MonthlyCalendarResponse)**

Replace:
```java
"Member Name", // TODO: Fetch from member repository
```
With:
```java
memberRepository
        .findById(MemberId.of(memberId))
        .map(Member::getDisplayName)
        .orElse(null),
```

**Step 4: Run CalendarController tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.CalendarControllerTest" --info`
Expected: All existing tests PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/worklog/api/CalendarController.java
git commit -m "feat: resolve member and reviewer names in CalendarController"
```

---

### Task 4: Resolve reviewer name in ApprovalController

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/ApprovalController.java`

**Step 1: Add import and inject repository**

Add import:
```java
import com.worklog.domain.member.Member;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
```

Add field and update constructor. Current constructor (lines 45-52):
```java
public ApprovalController(
        ApprovalService approvalService,
        ApprovalQueueProjection approvalQueueProjection,
        JdbcApprovalRepository approvalRepository) {
```

Change to:
```java
private final JdbcMemberRepository memberRepository;

public ApprovalController(
        ApprovalService approvalService,
        ApprovalQueueProjection approvalQueueProjection,
        JdbcApprovalRepository approvalRepository,
        JdbcMemberRepository memberRepository) {
    ...
    this.memberRepository = memberRepository;
}
```

**Step 2: Fix TODO at line 237 (reviewer name in MemberApprovalResponse)**

Replace:
```java
null, // TODO: Fetch reviewer name from member repository
```
With:
```java
a.getReviewedBy() != null
        ? memberRepository
                .findById(a.getReviewedBy())
                .map(Member::getDisplayName)
                .orElse(null)
        : null,
```

**Step 3: Run ApprovalController tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.ApprovalController*" --info`
Expected: All existing tests PASS. The `ApprovalControllerMemberViewTest` test `shouldReturnApprovalWithRejectedStatusAndReason` should now have a non-null `reviewerName` field (verify this by checking the response body — the test currently does not assert `reviewerName`, but it will validate the field is present).

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/api/ApprovalController.java
git commit -m "feat: resolve reviewer name in ApprovalController"
```

---

### Task 5: Resolve rejected-by names in RejectionController (batch)

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/RejectionController.java`

**Step 1: Add imports and inject repository**

Add imports:
```java
import com.worklog.domain.member.MemberId;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
```

Add field and update constructor. Current constructor (lines 21-23):
```java
public RejectionController(JdbcDailyRejectionLogRepository dailyRejectionLogRepository) {
    this.dailyRejectionLogRepository = dailyRejectionLogRepository;
}
```

Change to:
```java
private final JdbcMemberRepository memberRepository;

public RejectionController(
        JdbcDailyRejectionLogRepository dailyRejectionLogRepository,
        JdbcMemberRepository memberRepository) {
    this.dailyRejectionLogRepository = dailyRejectionLogRepository;
    this.memberRepository = memberRepository;
}
```

**Step 2: Fix TODO at line 43 (batch rejectedByName resolution)**

Replace the `getDailyRejections` method body (lines 35-47). Current code:

```java
List<JdbcDailyRejectionLogRepository.DailyRejectionRecord> records =
        dailyRejectionLogRepository.findByMemberIdAndDateRange(memberId, startDate, endDate);

List<DailyRejectionResponse.DailyRejectionItem> items = records.stream()
        .map(r -> new DailyRejectionResponse.DailyRejectionItem(
                r.workDate(),
                r.rejectionReason(),
                r.rejectedBy(),
                null, // TODO: Fetch rejectedByName from member repository
                r.createdAt()))
        .collect(Collectors.toList());
```

Replace with:

```java
List<JdbcDailyRejectionLogRepository.DailyRejectionRecord> records =
        dailyRejectionLogRepository.findByMemberIdAndDateRange(memberId, startDate, endDate);

// Batch resolve rejector display names
Set<MemberId> rejectorIds = records.stream()
        .map(JdbcDailyRejectionLogRepository.DailyRejectionRecord::rejectedBy)
        .filter(Objects::nonNull)
        .map(MemberId::of)
        .collect(java.util.stream.Collectors.toSet());
Map<MemberId, String> rejectorNames = memberRepository.findDisplayNamesByIds(rejectorIds);

List<DailyRejectionResponse.DailyRejectionItem> items = records.stream()
        .map(r -> new DailyRejectionResponse.DailyRejectionItem(
                r.workDate(),
                r.rejectionReason(),
                r.rejectedBy(),
                r.rejectedBy() != null
                        ? rejectorNames.get(MemberId.of(r.rejectedBy()))
                        : null,
                r.createdAt()))
        .collect(Collectors.toList());
```

Note: `java.util.stream.Collectors` is already imported as `Collectors` (line 8). Use that import. The `java.util.stream.Collectors.toSet()` above should just be `Collectors.toSet()` since the import exists.

**Step 3: Run related tests**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.*Rejection*" --info`
Expected: All existing tests PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/worklog/api/RejectionController.java
git commit -m "feat: batch resolve rejected-by names in RejectionController"
```

---

### Task 6: Run full test suite and verify

**Files:** (none — verification only)

**Step 1: Run all backend tests**

Run: `cd backend && ./gradlew clean test`
Expected: All tests PASS (1,167+ tests).

**Step 2: Verify no remaining TODO references**

Run: `grep -rn "TODO.*Fetch.*member\|TODO.*Fetch.*reviewer\|TODO.*Fetch.*rejected" backend/src/main/java/`
Expected: No matches found.

**Step 3: Squash or group commits if desired, then deliver**

All 4 TODO locations are resolved. Use `/git-deliver` to push and create PR.
