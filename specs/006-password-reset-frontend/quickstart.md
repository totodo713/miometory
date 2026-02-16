# Quickstart: Password Reset Frontend

**Date**: 2026-02-16
**Feature**: 006-password-reset-frontend

## Prerequisites

- Node.js (LTS)
- npm
- Frontend dev server runnable (`cd frontend && npm install && npm run dev`)

## Development Setup

```bash
# 1. Switch to feature branch
git checkout 006-password-reset-frontend

# 2. Install dependencies (from frontend/)
cd frontend && npm install

# 3. Start dev server
npm run dev
# Frontend available at http://localhost:3000
```

## Pages to Test Manually

| Page | URL | Notes |
|------|-----|-------|
| Password Reset Request | http://localhost:3000/password-reset/request | Enter email, submit, verify success message |
| Password Reset Confirm | http://localhost:3000/password-reset/confirm?token=TEST | Enter new password, test validation |
| Login (with new link) | http://localhost:3000/login | Verify "forgot password" link appears |

## Running Tests

```bash
# Run all unit tests
npm test -- --run

# Run password reset tests only
npm test -- --run tests/unit/\(auth\)/password-reset/
npm test -- --run tests/unit/components/auth/PasswordStrengthIndicator.test.tsx
npm test -- --run tests/unit/lib/validation/password.test.ts
npm test -- --run tests/unit/lib/utils/rate-limit.test.ts

# Run with watch mode for development
npm run test:watch

# Run E2E accessibility tests (requires dev server running)
npm run test:e2e -- password-reset-accessibility
```

## Linting and Formatting

```bash
# Check all modified files
npx biome check app/(auth)/login/page.tsx
npx biome check tests/unit/

# Auto-fix
npm run lint:fix
npm run format

# CI check (lint + format)
npm run check:ci
```

## Key Files Reference

| Purpose | Path |
|---------|------|
| Request page | `app/(auth)/password-reset/request/page.tsx` |
| Confirm page | `app/(auth)/password-reset/confirm/page.tsx` |
| Strength indicator | `app/components/auth/PasswordStrengthIndicator.tsx` |
| Validation utils | `app/lib/validation/password.ts` |
| Rate limiting | `app/lib/utils/rate-limit.ts` |
| Type definitions | `app/lib/types/password-reset.ts` |
| API client | `app/services/api.ts` (auth section) |
| E2E tests | `tests/e2e/password-reset-accessibility.spec.ts` |

## Notes

- All user-facing text is in Japanese
- Password strength uses `@zxcvbn-ts/core` with 300ms debounce
- Rate limiting: 3 requests per 5 minutes (client-side, localStorage)
- Backend API must be running for E2E tests (or use Playwright route mocking)
