# Research: Login Page Design, Auth Integration & Logout

**Feature**: 012-login-auth-ui | **Date**: 2026-02-18

## Research Summary

No critical unknowns exist. The backend auth API is already implemented, and the frontend architecture patterns are well-established. This document records key decisions and their rationale.

## Decisions

### 1. Auth State Management Approach

**Decision**: React Context with sessionStorage persistence
**Rationale**: The simplest approach that satisfies FR-009 (persist across page refresh). React Context is the standard Next.js pattern for client-side state. sessionStorage is appropriate because the session should not survive browser close (matching the 30-minute server-side session timeout behavior).
**Alternatives considered**:
- localStorage: Rejected — session should not persist indefinitely across browser sessions
- NextAuth.js: Rejected — over-engineered for session-based auth with existing backend; adds unnecessary dependency
- Cookie-based state: Rejected — Spring Security already manages the session cookie; duplicating user info in a separate cookie adds complexity

### 2. Route Protection Strategy

**Decision**: Client-side AuthGuard component in a worklog layout
**Rationale**: Next.js middleware would be the "proper" way for server-side protection, but since this is a client-side React app with `"use client"` throughout, a client-side redirect is simpler and consistent with the existing architecture. The backend already has `permitAll()` in dev mode, so server-side route protection is not relevant for this phase.
**Alternatives considered**:
- Next.js middleware (`middleware.ts`): Rejected — would require server-side session checking, adds complexity for a dev-focused feature
- Per-page guards: Rejected — duplicates logic across multiple pages; layout-level guard is DRY

### 3. Login Page Design Language

**Decision**: Follow existing Tailwind utility classes from worklog pages
**Rationale**: The worklog dashboard already uses a consistent visual language (gray-50 background, white cards with shadow, blue-600 primary buttons, gray-900 text). Matching this ensures UX consistency (Constitution Principle III).
**Alternatives considered**:
- Custom CSS / styled-jsx: Rejected — inconsistent with the rest of the codebase which uses Tailwind exclusively for styling
- UI component library (shadcn/ui, Radix): Rejected — adds dependency for a single page; over-engineered

### 4. User Seed Data Strategy

**Decision**: Add user INSERT statements to existing `R__dev_seed_data.sql` repeatable migration
**Rationale**: This file already seeds roles, permissions, and member-project assignments. Adding users here keeps all dev data in one place. UUIDs match existing member IDs so the User-Member relationship is implicit via shared ID.
**Alternatives considered**:
- Separate `data-dev-users.sql` file: Rejected — fragments dev seed data across multiple files
- Application-level test user creation: Rejected — adds runtime complexity; SQL seed is simpler and more transparent

### 5. BCrypt Password Hash

**Decision**: Pre-compute BCrypt hash of `Password1` and embed as a literal string in seed SQL
**Rationale**: The hash is deterministic given the same salt, but BCrypt includes random salt per invocation. A pre-computed hash avoids runtime dependency on Spring's BCryptPasswordEncoder during database migration. The dev password is not sensitive.
**Alternatives considered**:
- Runtime hash computation in SQL: Rejected — PostgreSQL has `pgcrypto` extension with `crypt()` function, but it may not be installed in all environments

### 6. Header Placement

**Decision**: Global header in root layout (visible on all authenticated pages, hidden on login)
**Rationale**: Placing the Header in `layout.tsx` ensures it appears on every page. The Header component returns `null` when no user is authenticated, so it naturally hides on the login page. This satisfies FR-005.
**Alternatives considered**:
- Header in worklog layout only: Rejected — would not appear on future non-worklog authenticated pages
- Header in each page individually: Rejected — violates DRY principle
