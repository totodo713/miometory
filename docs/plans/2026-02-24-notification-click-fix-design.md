# Notification Click JSON Parse Error Fix

## Problem

Clicking any unread notification triggers:
`Failed to execute 'json' on 'Response': Unexpected end of JSON input`

### Root Cause

`NotificationController.markRead()` and `markAllRead()` return `void`, which Spring MVC translates to **200 OK with empty body**. The frontend `apiClient.request()` only handles empty bodies for `204 No Content`, so it calls `response.json()` on the empty 200 response and fails.

## Approach: Backend 204 + Frontend UX Improvements

### Change 1: Backend — Return 204 No Content (Bug Fix)

**Files**: `NotificationController.java`, `AdminNotificationControllerTest.kt`

Change `markRead` and `markAllRead` from `void` return to `ResponseEntity<Void>` with `ResponseEntity.noContent().build()`. This matches the established pattern in `ApprovalController` and `AbsenceController`.

Update test expectations from `status().isOk` to `status().isNoContent`.

### Change 2: Optimistic UI Update with Rollback

**File**: `useNotifications.ts`

Current `markRead` updates UI only after API success and has no error handling. Change to:
- Update UI immediately (optimistic)
- Call API in background
- Rollback UI state on failure and re-throw for caller handling

### Change 3: Fire-and-Forget markRead on Navigation

**File**: `notifications/page.tsx`

Current `handleNotificationClick` awaits `markRead` before navigating, blocking the user. Change to:
- Fire `markRead` without awaiting when navigating to a reference page
- Navigate immediately — the user's goal is to see the referenced content, not to mark as read
- Silent catch (next poll will re-sync)

### Change 4: NotificationBell Click Navigation

**File**: `NotificationBell.tsx`

Currently clicking a notification in the bell dropdown only marks it as read. Add navigation to the reference page when `referenceId` exists, matching user expectations.

## Out of Scope (Separate Tickets)

- Keyboard navigation for notification dropdown (Escape, arrow keys)
- Screen reader announcement of unread state

## Files Changed

| File | Change |
|------|--------|
| `NotificationController.java` | `void` -> `ResponseEntity<Void>` + 204 |
| `AdminNotificationControllerTest.kt` | `isOk` -> `isNoContent` |
| `useNotifications.ts` | Optimistic update + rollback + error handling |
| `notifications/page.tsx` | Fire-and-forget markRead + immediate navigation |
| `NotificationBell.tsx` | Add navigation on click |
