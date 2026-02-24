# Notification Click Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the JSON parse error when clicking notifications, and improve the notification click UX with optimistic updates and immediate navigation.

**Architecture:** Backend returns 204 No Content (matching existing patterns), frontend uses optimistic UI updates with rollback, and fire-and-forget API calls when navigating away.

**Tech Stack:** Spring Boot (Java), Next.js (React/TypeScript), Kotlin (tests)

---

### Task 1: Backend — Update tests to expect 204

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/api/AdminNotificationControllerTest.kt`

**Step 1: Update test expectations from 200 to 204**

Change 4 tests that assert `status().isOk` to `status().isNoContent` for the mark-read endpoints:

```kotlin
// Line 91: rename + change assertion
@Test
fun `mark read returns 204`() {
    mockMvc.perform(
        patch("/api/v1/notifications/$notificationId/read")
            .with(user(userEmail)),
    )
        .andExpect(status().isNoContent)
}

// Line 100: rename + change assertion
@Test
fun `mark all read returns 204`() {
    mockMvc.perform(
        patch("/api/v1/notifications/read-all")
            .with(user(userEmail)),
    )
        .andExpect(status().isNoContent)
}

// Line 136: change assertion only
@Test
fun `mark read succeeds for user without member record`() {
    val noMemberEmail = "nomember-${UUID.randomUUID().toString().take(8)}@test.com"
    createUser(noMemberEmail, USER_ROLE_ID, "No Member User")

    mockMvc.perform(
        patch("/api/v1/notifications/${UUID.randomUUID()}/read")
            .with(user(noMemberEmail)),
    )
        .andExpect(status().isNoContent)
}

// Line 148: change assertion only
@Test
fun `mark all read succeeds for user without member record`() {
    val noMemberEmail = "nomember-${UUID.randomUUID().toString().take(8)}@test.com"
    createUser(noMemberEmail, USER_ROLE_ID, "No Member User")

    mockMvc.perform(
        patch("/api/v1/notifications/read-all")
            .with(user(noMemberEmail)),
    )
        .andExpect(status().isNoContent)
}
```

**Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AdminNotificationControllerTest" --no-daemon`
Expected: 4 tests FAIL (expected 204 but got 200)

### Task 2: Backend — Change controller to return 204

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/NotificationController.java`

**Step 1: Update markRead and markAllRead to return ResponseEntity<Void>**

Add `import org.springframework.http.ResponseEntity;` and change both methods:

```java
@PatchMapping("/{id}/read")
public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication authentication) {
    UUID memberId = userContextService.resolveUserMemberIdOrNull(authentication.getName());
    // Users without a member record have no notifications — silently succeed as a no-op
    if (memberId == null) {
        return ResponseEntity.noContent().build();
    }
    notificationService.markRead(id, memberId);
    return ResponseEntity.noContent().build();
}

@PatchMapping("/read-all")
public ResponseEntity<Void> markAllRead(Authentication authentication) {
    UUID memberId = userContextService.resolveUserMemberIdOrNull(authentication.getName());
    // Users without a member record have no notifications — silently succeed as a no-op
    if (memberId == null) {
        return ResponseEntity.noContent().build();
    }
    notificationService.markAllRead(memberId);
    return ResponseEntity.noContent().build();
}
```

**Step 2: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.worklog.api.AdminNotificationControllerTest" --no-daemon`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add backend/src/main/java/com/worklog/api/NotificationController.java \
       backend/src/test/kotlin/com/worklog/api/AdminNotificationControllerTest.kt
git commit -m "fix(backend): return 204 No Content from notification mark-read endpoints

The markRead and markAllRead endpoints returned void (200 OK with empty body),
causing the frontend apiClient to fail when calling response.json() on the
empty response. Change to ResponseEntity<Void> with 204 No Content, matching
the established pattern used by ApprovalController and AbsenceController."
```

### Task 3: Frontend — Optimistic UI in useNotifications hook

**Files:**
- Modify: `frontend/app/hooks/useNotifications.ts`

**Step 1: Update markRead with optimistic update + rollback**

Replace the current `markRead` callback (lines 34-38) with:

```typescript
const markRead = useCallback(async (id: string) => {
  // Optimistic update
  setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
  setUnreadCount((prev) => Math.max(0, prev - 1));
  try {
    await api.notification.markRead(id);
  } catch (e) {
    // Rollback on failure
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: false } : n)));
    setUnreadCount((prev) => prev + 1);
    throw e;
  }
}, []);
```

**Step 2: Update markAllRead with optimistic update + rollback**

Replace the current `markAllRead` callback (lines 40-44) with:

```typescript
const markAllRead = useCallback(async () => {
  const prevNotifications = notifications;
  const prevUnreadCount = unreadCount;
  // Optimistic update
  setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
  setUnreadCount(0);
  try {
    await api.notification.markAllRead();
  } catch (e) {
    // Rollback on failure
    setNotifications(prevNotifications);
    setUnreadCount(prevUnreadCount);
    throw e;
  }
}, [notifications, unreadCount]);
```

**Step 3: Verify frontend compiles**

Run: `cd frontend && npx next build --no-lint 2>&1 | tail -5` or `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 4: Commit**

```bash
git add frontend/app/hooks/useNotifications.ts
git commit -m "fix(frontend): add optimistic UI updates with rollback to notification hooks

markRead and markAllRead now update UI immediately for better perceived
performance, and rollback state on API failure."
```

### Task 4: Frontend — Fire-and-forget markRead on navigation

**Files:**
- Modify: `frontend/app/notifications/page.tsx`

**Step 1: Update handleNotificationClick to not block navigation**

Replace the current `handleNotificationClick` (lines 172-185) with:

```typescript
const handleNotificationClick = async (notification: Notification) => {
  if (!notification.isRead) {
    // Fire-and-forget: don't block navigation for mark-read
    api.notification.markRead(notification.id).catch(() => {
      // Silent — next poll will re-sync
    });
  }
  if (notification.referenceId) {
    router.push(`/worklog/approval?id=${notification.referenceId}`);
  } else {
    await loadNotifications();
  }
};
```

**Step 2: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 3: Commit**

```bash
git add frontend/app/notifications/page.tsx
git commit -m "fix(frontend): don't block navigation on mark-read API call

Navigate immediately when clicking a notification with a referenceId.
The mark-read API call runs in the background — if it fails, the next
poll cycle will re-sync the read state."
```

### Task 5: Frontend — Add navigation to NotificationBell

**Files:**
- Modify: `frontend/app/components/shared/NotificationBell.tsx`

**Step 1: Add useRouter import and navigation on click**

Add `useRouter` import and update the notification click handler:

```typescript
// Add to imports (line 3):
import { useRouter } from "next/navigation";
```

Add router inside the component (after line 9):

```typescript
const router = useRouter();
```

Replace the onClick handler (lines 76-80) with:

```typescript
onClick={() => {
  if (!notification.isRead) {
    markRead(notification.id);
  }
  if (notification.referenceId) {
    router.push(`/worklog/approval?id=${notification.referenceId}`);
    setIsOpen(false);
  }
}}
```

**Step 2: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No type errors

**Step 3: Commit**

```bash
git add frontend/app/components/shared/NotificationBell.tsx
git commit -m "feat(frontend): add navigation from notification bell dropdown

Clicking a notification with a referenceId now navigates to the
approval page, matching user expectations. Previously the dropdown
only marked notifications as read without navigation."
```

### Task 6: Run full test suites

**Step 1: Run backend tests**

Run: `cd backend && ./gradlew test --no-daemon`
Expected: ALL PASS

**Step 2: Run frontend type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

**Step 3: Run frontend lint**

Run: `cd frontend && npx biome check app/hooks/useNotifications.ts app/notifications/page.tsx app/components/shared/NotificationBell.tsx`
Expected: No errors (auto-formatted by hooks)
