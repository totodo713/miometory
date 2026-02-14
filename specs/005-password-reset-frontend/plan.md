# Implementation Plan: Password Reset Frontend

**Branch**: `005-password-reset-frontend` | **Date**: 2026-02-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-password-reset-frontend/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement a secure, accessible password reset flow for the Miometry Entry System frontend. Users will be able to request password reset emails and confirm new passwords using time-limited tokens. The implementation follows existing authentication patterns (login/signup pages), enforces client-side rate limiting (3 requests per 5 minutes), extracts tokens from URLs to prevent exposure, and provides real-time password strength feedback. Backend APIs are already available at `/api/v1/auth/password-reset/request` and `/api/v1/auth/password-reset/confirm`.

## Technical Context

**Language/Version**: TypeScript 5.x, React 19.2.3  
**Primary Dependencies**: Next.js 16.1.6, Zod 3.22.4 (validation), date-fns 3.3.1, @tanstack/react-query 5.28.0, zustand 4.5.1  
**Storage**: Browser localStorage/sessionStorage (for client-side rate limiting state), no persistent client-side data  
**Testing**: Vitest 4.0.18 (unit tests), @testing-library/react 16.3.2, Playwright 1.42.0 (E2E)  
**Target Platform**: Modern web browsers (Chrome, Firefox, Safari, Edge) latest 2 versions; mobile responsive (min 320px width)  
**Project Type**: Web application (Next.js App Router, frontend-only feature)  
**Performance Goals**: 
- Password reset request flow: < 30 seconds from page load to success message
- Password reset confirm flow: < 60 seconds from page load to login redirect
- Password strength feedback: < 100ms response time
- Loading state feedback: < 200ms visual indication

**Constraints**: 
- WCAG 2.1 AA accessibility compliance (keyboard navigation, screen readers, ARIA labels)
- Client-side rate limiting: 3 requests per 5-minute window
- Token extraction: Extract from URL to memory and clean URL on confirm page
- No automatic API retry: Manual retry button only
- i18n-ready: Externalize all strings with Japanese as initial language

**Scale/Scope**: 2 new pages, 3-4 components, 2 API client functions, 1 custom hook (optional), estimated 3-5 hours implementation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Code Quality
- [x] **Static analysis**: Biome linting must pass with zero errors before merge ✅
  - *Design compliance*: Project uses Biome 2.2.0; linting enforced in CI
- [x] **Peer review**: All code changes reviewed against quality gates ✅
  - *Design compliance*: PR template includes Constitution checklist
- [x] **Simplicity**: Reuse existing patterns from login/signup pages; no unnecessary abstractions ✅
  - *Design compliance*: Data model mirrors login page structure; no custom state management beyond useState
- [x] **Documentation**: All functions/components documented with JSDoc; password strength logic explained inline ✅
  - *Design compliance*: research.md documents all technical decisions; quickstart.md provides setup guide

### II. Testing Discipline & Standards
- [x] **Test coverage**: Unit tests for both pages (request and confirm) with Vitest ✅
  - *Design compliance*: Test structure defined in plan; targets 80%+ coverage
- [x] **Test pyramid**: Prioritize unit tests for validation logic, password strength, rate limiting; E2E for critical flows ✅
  - *Design compliance*: Unit tests for utilities (password.ts, rate-limit.ts), E2E for flows (quickstart.md Step 6)
- [x] **All tests pass**: No merge until all tests green; failing tests must be justified ✅
  - *Design compliance*: CI enforces test pass before merge
- [x] **Fast & isolated**: Tests run independently without external API dependencies (mock API calls) ✅
  - *Design compliance*: API calls mocked in unit tests; E2E uses test fixtures

### III. Consistent User Experience (UX)
- [x] **Design system**: Follow existing auth page patterns (login/signup) for forms, buttons, error messages ✅
  - *Design compliance*: Reuses existing auth layout from `app/(auth)/login/page.tsx`
- [x] **Consistency**: Use standard error message formats from existing pages; maintain navigation patterns ✅
  - *Design compliance*: ErrorState type matches existing error handling; i18n with next-intl
- [x] **Communication**: Clear loading states, error messages, success messages with appropriate urgency ✅
  - *Design compliance*: isLoading states in forms; success messages with auto-redirect
- [x] **Accessibility**: All form fields have proper labels, keyboard navigation, ARIA attributes; reviewed at design phase ✅
  - *Design compliance*: WCAG 2.1 AA requirements documented; axe-core tests in quickstart.md

### IV. Performance Requirements
- [x] **Latency targets**: ✅
  - Request flow: < 30 seconds (SC-001) - API call + rate limit check < 500ms
  - Confirm flow: < 60 seconds (SC-002) - Password strength + API call < 1s
  - Password strength feedback: < 100ms (SC-006) - zxcvbn-ts benchmarked at < 10ms
  - Loading feedback: < 200ms (SC-008) - Immediate spinner display
- [x] **No regressions**: Automated checks for validation performance; debounce password strength checks ✅
  - *Design compliance*: 300ms debounce for password strength; performance tests in quickstart.md Step 7
- [x] **Documented tradeoffs**: Rate limiting state stored in localStorage (tradeoff: shared across tabs vs. per-tab isolation) ✅
  - *Design compliance*: research.md documents localStorage choice with cross-tab sync via Storage Events

**Status**: ✅ **PASSED** - All constitution principles satisfied in Phase 1 design (2026-02-15)

## Project Structure

### Documentation (this feature)

```text
specs/005-password-reset-frontend/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── api-contracts.yaml  # OpenAPI-style contract for password reset endpoints
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Web application structure (frontend-only feature)
frontend/
├── app/
│   ├── (auth)/                           # Auth route group (existing)
│   │   ├── login/page.tsx                # Existing (reference for patterns)
│   │   ├── signup/page.tsx               # Existing (reference for patterns)
│   │   └── password-reset/               # NEW: Password reset pages
│   │       ├── request/                  # NEW: Request password reset
│   │       │   └── page.tsx              # NEW: Request page component
│   │       └── confirm/                  # NEW: Confirm password reset
│   │           └── page.tsx              # NEW: Confirm page component
│   ├── components/
│   │   └── auth/                         # Existing auth components directory
│   │       └── PasswordStrengthIndicator.tsx  # NEW: Password strength component
│   ├── hooks/                            # Existing hooks directory
│   │   └── usePasswordReset.ts           # NEW: (Optional) Custom hook for password reset logic
│   └── lib/
│       ├── api/                          # API client directory (may need creation)
│       │   └── auth.ts                   # NEW or EXTENDED: API functions for password reset
│       ├── config.ts                     # Existing (contains AUTH_CONFIG, API_CONFIG)
│       ├── validation/                   # NEW: Validation utilities directory
│       │   └── password.ts               # NEW: Password validation logic
│       └── utils/                        # Utilities directory (may need creation)
│           └── rate-limit.ts             # NEW: Client-side rate limiting logic
└── tests/                                # Test directory
    ├── unit/                             # Unit tests
    │   ├── password-reset-request.test.tsx  # NEW: Request page tests
    │   ├── password-reset-confirm.test.tsx  # NEW: Confirm page tests
    │   └── password-strength.test.tsx    # NEW: Password strength component tests
    └── e2e/                              # E2E tests (Playwright)
        └── password-reset.spec.ts        # NEW: End-to-end password reset flow
```

**Structure Decision**: 
- Following existing Next.js App Router structure with route groups `(auth)`
- Reusing existing patterns from login/signup pages for consistency
- New pages placed under `app/(auth)/password-reset/` with nested routes
- Shared components in `app/components/auth/`
- API client functions centralized in `app/lib/api/auth.ts` (to be created or extended)
- Validation and utility functions organized in dedicated `lib/` subdirectories
- Tests mirror source structure with unit tests per component/page

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations anticipated. This feature follows existing patterns and does not introduce architectural complexity beyond standard Next.js pages and components.

---

## Phase 0: Outline & Research

### Research Topics

The following topics require investigation before implementation:

#### 1. Password Strength Calculation Algorithm
- **Question**: What algorithm should be used for real-time password strength calculation?
- **Context**: FR-012 requires real-time password strength indicator (weak/medium/strong)
- **Research needed**:
  - Best practices for client-side password strength evaluation
  - Performance considerations for real-time calculation (< 100ms target)
  - Algorithm options: entropy-based, rule-based, or hybrid approach
  - Color-coding standards (red/yellow/green) and accessibility

#### 2. Client-Side Rate Limiting Implementation
- **Question**: What is the most reliable approach for client-side rate limiting across browser sessions?
- **Context**: FR-016 requires 3 requests per 5-minute window, clarification specifies client-side enforcement
- **Research needed**:
  - localStorage vs. sessionStorage tradeoffs (shared across tabs vs. per-tab)
  - Timestamp-based sliding window implementation
  - Edge cases: cleared storage, time zone changes, manual clock adjustments
  - User feedback patterns when rate limit is hit

#### 3. URL Token Extraction & Cleanup
- **Question**: What is the secure way to extract token from URL and clean it without breaking browser history?
- **Context**: Clarification requires "extract token to memory and clean up URL" for FR-004
- **Research needed**:
  - Next.js 16 router API for URL manipulation (useSearchParams, router.replace)
  - Security implications of token in URL (referrer leaks, browser history)
  - Best practice timing for extraction (useEffect, server component)
  - Handling page refresh after cleanup

#### 4. Internationalization (i18n) Framework Selection
- **Question**: What i18n library best integrates with Next.js 16 App Router?
- **Context**: FR-018 requires externalized strings with i18n framework, Japanese as initial language
- **Research needed**:
  - next-intl vs. react-i18next vs. next-i18next for App Router
  - Performance impact and bundle size
  - Support for client components and server components
  - Namespace organization for auth-related strings

#### 5. API Error Handling & Retry Patterns
- **Question**: How should we implement manual retry button without automatic retry?
- **Context**: FR-010 requires manual retry button; clarification disallows automatic retry
- **Research needed**:
  - Error state management patterns (React Query, zustand, local state)
  - User-friendly error message mapping from backend responses
  - Manual retry button UX patterns
  - Network error detection vs. validation error vs. expired token

#### 6. Accessibility Testing Strategy
- **Question**: What automated tools should validate WCAG 2.1 AA compliance?
- **Context**: FR-015 requires WCAG 2.1 AA, existing project has @axe-core/playwright
- **Research needed**:
  - @axe-core/playwright configuration for password reset pages
  - Manual keyboard navigation testing checklist
  - Screen reader testing approach (NVDA/JAWS/VoiceOver)
  - ARIA patterns for password strength indicator and error messages

**Output**: All findings consolidated in `research.md` with decisions, rationales, and alternatives considered.

---

## Phase 1: Design & Contracts

### Prerequisites
- `research.md` complete with all decisions documented

### Deliverables

#### 1. Data Model (`data-model.md`)
Extract and document entities from feature spec:

**Entities**:
- **PasswordResetRequestForm**: Contains email field with validation state
- **PasswordResetConfirmForm**: Contains token, newPassword, passwordConfirm fields with validation state
- **ValidationError**: Field name, error message, error type (format/required/mismatch)
- **RateLimitState**: Request timestamps, remaining attempts, reset time
- **PasswordStrengthResult**: Strength level (weak/medium/strong), feedback messages, score
- **ApiResponse**: Success/error status, message, error code (from backend)

**State Transitions**:
- Request form: idle → validating → submitting → success/error
- Confirm form: idle → validating → submitting → success (redirect) / error
- Rate limit: available → warning → blocked → available (after reset)

**Validation Rules** (from FR-005):
- Email: RFC 5322 simplified regex from `config.ts` (line 53)
- Password: Min 8 chars, 1 uppercase, 1 lowercase, 1 number

#### 2. API Contracts (`contracts/api-contracts.yaml`)
Generate OpenAPI-style contract from backend endpoints:

**Endpoints** (from backend `AuthController.java`):
- `POST /api/v1/auth/password-reset/request`
  - Request: `{ email: string }`
  - Response: `{ message: string }` (always 200 OK for anti-enumeration)
- `POST /api/v1/auth/password-reset/confirm`
  - Request: `{ token: string, newPassword: string }`
  - Response: `{ message: string }` (200 OK) or error (404 for invalid token)

**Error Responses** (from `GlobalExceptionHandler.java`):
- 400 Bad Request: Validation errors
- 404 Not Found: Invalid/expired token
- 500 Internal Server Error: Unexpected errors

#### 3. Quickstart Guide (`quickstart.md`)
Developer onboarding document:
- Local development setup (already running Next.js dev server)
- How to test password reset flow locally
- How to run unit tests (`npm run test`)
- How to run E2E tests (`npm run test:e2e`)
- How to generate valid test token (reference backend test utilities)

#### 4. Agent Context Update
Run `.specify/scripts/bash/update-agent-context.sh opencode` to add:
- New technologies: Zod for validation, localStorage for rate limiting
- New patterns: Token extraction from URL, password strength calculation
- Preserve existing context between markers

---

## Next Steps

After this plan is complete:
1. Execute Phase 0 research (automatically by `/speckit.plan` command)
2. Execute Phase 1 design & contracts (automatically by `/speckit.plan` command)
3. Re-evaluate Constitution Check after Phase 1
4. Run `/speckit.tasks` to generate implementation task breakdown
5. Begin implementation following task order

**Estimated Timeline**: 
- Phase 0 Research: 30 minutes
- Phase 1 Design: 45 minutes
- Implementation (via `/speckit.tasks`): 3-5 hours
- **Total**: 4.5-6.5 hours

