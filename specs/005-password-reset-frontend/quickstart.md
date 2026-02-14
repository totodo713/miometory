# Quickstart Guide: Password Reset Frontend

**Feature Branch**: `005-password-reset-frontend`  
**Date**: 2026-02-15  
**Status**: Development Setup Guide  
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/api-contracts.yaml](./contracts/api-contracts.yaml)

## Overview

This guide walks you through setting up the password reset frontend for local development. Follow these steps to:
1. Install new frontend dependencies (zxcvbn-ts, next-intl)
2. Run the backend service with password reset API endpoints
3. Run the frontend with password reset UI
4. Test the complete password reset flow
5. Generate test tokens for development

**Estimated Setup Time**: 10-15 minutes

---

## Prerequisites

### Required Software

| Software | Version | Installation Check |
|----------|---------|-------------------|
| Java | 21+ | `java -version` |
| Node.js | 20+ | `node --version` |
| npm | 10+ | `npm --version` |
| PostgreSQL | 15+ | `psql --version` |
| Docker | 24+ (optional) | `docker --version` |
| Git | 2.40+ | `git --version` |

### System Requirements
- **OS**: Linux, macOS, or Windows (with WSL2)
- **RAM**: 4GB minimum, 8GB recommended
- **Disk**: 2GB free space

### Prerequisites Features
The password reset feature depends on the following existing features:
- **004-user-login-auth**: User authentication system with `members` table
- **Backend API**: Password reset endpoints already implemented in PR #3

---

## Step 1: Verify Backend API Availability

The backend already includes password reset endpoints (implemented in PR #3). Verify they are available:

```bash
# Navigate to backend directory
cd backend

# Check AuthController.java for password reset endpoints
grep -A 5 "password-reset" src/main/java/com/worklog/api/AuthController.java

# Expected output:
# @PostMapping("/password-reset/request")
# @PostMapping("/password-reset/confirm")
```

### Backend Endpoints
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/auth/password-reset/request` | POST | Request password reset (sends email) |
| `/api/v1/auth/password-reset/confirm` | POST | Confirm password reset (set new password) |

See [contracts/api-contracts.yaml](./contracts/api-contracts.yaml) for full API specification.

---

## Step 2: Install Frontend Dependencies

Install new dependencies required for password reset:

```bash
# Navigate to frontend directory
cd frontend

# Install new dependencies
npm install @zxcvbn-ts/core@^3.0.4 \
            @zxcvbn-ts/language-common@^3.0.4 \
            @zxcvbn-ts/language-en@^3.0.2 \
            next-intl@^3.0.0

# Verify installation
npm list @zxcvbn-ts/core next-intl

# Expected output:
# ├── @zxcvbn-ts/core@3.0.4
# └── next-intl@3.0.x
```

### New Dependencies Explained
| Package | Purpose | Size | Performance |
|---------|---------|------|-------------|
| `@zxcvbn-ts/core` | Password strength calculation | ~50KB | < 10ms per check |
| `@zxcvbn-ts/language-common` | Common password dictionary | ~20KB | - |
| `@zxcvbn-ts/language-en` | English language support | ~15KB | - |
| `next-intl` | Internationalization framework | ~14KB | Server + client |

**Total Bundle Impact**: ~99KB (gzip: ~30KB)

---

## Step 3: Start Development Servers

### Option A: Using Docker Compose (Recommended)

```bash
# Navigate to docker directory
cd infra/docker

# Start all services (PostgreSQL + Redis)
docker compose up -d

# Wait for PostgreSQL to be ready (10-15 seconds)
docker compose logs -f postgres
# Press Ctrl+C when you see "database system is ready to accept connections"

# Verify connection
docker exec -it miometry-postgres psql -U worklog -d worklog -c "SELECT version();"
```

### Option B: Using Local PostgreSQL

```bash
# Start local PostgreSQL (if not already running)
sudo systemctl start postgresql

# Verify connection
psql -h localhost -U worklog -d worklog -c "\dt"
```

### Start Backend Server

```bash
# Navigate to backend directory
cd backend

# Run Spring Boot application
./gradlew bootRun

# Wait for startup (20-30 seconds)
# Expected output: "Started WorkLogApplication in X seconds"
```

**Backend Health Check**:
```bash
# Verify backend is running
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

### Start Frontend Server

```bash
# Navigate to frontend directory
cd frontend

# Run Next.js development server
npm run dev

# Expected output:
# ▲ Next.js 16.1.6
# - Local:        http://localhost:3000
# - ready in 2s
```

**Frontend Health Check**:
Open browser to http://localhost:3000 and verify login page loads.

---

## Step 4: Test Password Reset Flow

### Manual Testing Workflow

#### 4.1 Request Password Reset

1. **Navigate to request page**:
   ```
   http://localhost:3000/password-reset/request
   ```

2. **Enter test email**:
   ```
   test@example.com
   ```
   (Use an email from your `members` table - see Step 5 for test users)

3. **Click "Send Reset Link" button**

4. **Expected behavior**:
   - Success message: "If the email exists, a password reset link has been sent."
   - Message appears regardless of email existence (anti-enumeration)

#### 4.2 Retrieve Reset Token

**Development Mode**: Since no email server is configured, retrieve the token from backend logs:

```bash
# View backend logs for reset token
cd backend
tail -f build/logs/application.log | grep "password-reset"

# Expected log entry:
# [INFO] PasswordResetService - Generated reset token: eyJhbGci...
# [INFO] PasswordResetService - Reset link: http://localhost:3000/password-reset/confirm?token=eyJhbGci...
```

**Alternative**: Query database directly:
```bash
docker exec -it miometry-postgres psql -U worklog -d worklog -c \
  "SELECT token, expires_at FROM password_reset_tokens WHERE used = false ORDER BY created_at DESC LIMIT 1;"
```

#### 4.3 Confirm Password Reset

1. **Navigate to confirm page with token**:
   ```
   http://localhost:3000/password-reset/confirm?token=<TOKEN_FROM_LOGS>
   ```

2. **Enter new password**:
   - New Password: `NewSecure123`
   - Confirm Password: `NewSecure123`

3. **Observe password strength indicator**:
   - Weak (red): `password123`
   - Medium (yellow): `Password1`
   - Strong (green): `MySecure@Pass123`

4. **Click "Reset Password" button**

5. **Expected behavior**:
   - Success message: "Password reset successfully. You may now log in with your new password."
   - Automatic redirect to `/login` after 3 seconds

#### 4.4 Verify Password Reset

1. **Navigate to login page**:
   ```
   http://localhost:3000/login
   ```

2. **Login with new credentials**:
   - Email: `test@example.com`
   - Password: `NewSecure123`

3. **Expected behavior**:
   - Successful login
   - Redirect to dashboard (`/`)

---

## Step 5: Create Test Users

If you need additional test users for password reset testing:

```bash
# Connect to PostgreSQL
docker exec -it miometry-postgres psql -U worklog -d worklog

# Insert test user (with bcrypt-hashed password "OldPassword123")
INSERT INTO members (id, employee_id, name, email, role, hashed_password, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'TEST001',
  'Test User',
  'test@example.com',
  'REGULAR_MEMBER',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldjqZmq6bJFO3VqZvou',
  NOW(),
  NOW()
);

# Verify insertion
SELECT id, email, name FROM members WHERE email = 'test@example.com';

# Exit psql
\q
```

**Test User Credentials**:
| Email | Initial Password | Employee ID |
|-------|-----------------|-------------|
| test@example.com | OldPassword123 | TEST001 |

---

## Step 6: Run Automated Tests

### Unit Tests (Vitest)

```bash
# Navigate to frontend directory
cd frontend

# Run all unit tests
npm run test

# Run tests in watch mode (for development)
npm run test:watch

# Run tests with coverage
npm run test -- --coverage
```

**Expected Test Coverage**:
| Component | Coverage Target | Files |
|-----------|----------------|-------|
| Pages | 80%+ | `app/(auth)/password-reset/**/*.tsx` |
| Components | 80%+ | `app/components/auth/PasswordStrengthIndicator.tsx` |
| Utilities | 90%+ | `app/lib/validation/password.ts`, `app/lib/utils/rate-limit.ts` |

### E2E Tests (Playwright)

```bash
# Navigate to frontend directory
cd frontend

# Run E2E tests (headless mode)
npm run test:e2e

# Run E2E tests with UI (for debugging)
npm run test:e2e:ui

# Run specific test file
npx playwright test tests/e2e/password-reset.spec.ts
```

**Expected E2E Test Scenarios**:
1. Request password reset with valid email
2. Request password reset with invalid email format
3. Rate limiting (3 requests per 5 minutes)
4. Confirm password reset with valid token
5. Confirm password reset with expired token
6. Password strength indicator behavior
7. Password mismatch error

### Accessibility Tests

```bash
# Run accessibility scan with axe-core
npm run test:e2e -- --grep "@accessibility"

# Manual testing with screen reader:
# - macOS: VoiceOver (Cmd+F5)
# - Windows: NVDA (free), JAWS
# - Linux: Orca
```

**WCAG 2.1 AA Compliance Checklist**:
- [ ] All form fields have `<label>` or `aria-label`
- [ ] Error messages have `role="alert"` and `aria-live="assertive"`
- [ ] Password strength indicator has `role="status"` and `aria-live="polite"`
- [ ] Keyboard navigation works (Tab, Enter, Escape)
- [ ] Color contrast meets 4.5:1 ratio (text) and 3:1 (UI components)

---

## Step 7: Development Utilities

### Generate Test Token (Backend Utility)

If backend provides a test utility to generate tokens:

```bash
cd backend

# Option 1: Using Gradle task (if implemented)
./gradlew generatePasswordResetToken --args="test@example.com"

# Option 2: Direct database insertion (for testing)
docker exec -it miometry-postgres psql -U worklog -d worklog -c "
  INSERT INTO password_reset_tokens (token, member_id, expires_at, used, created_at)
  VALUES (
    'TEST_TOKEN_123',
    (SELECT id FROM members WHERE email = 'test@example.com'),
    NOW() + INTERVAL '24 hours',
    false,
    NOW()
  );
"

# Test with generated token
curl -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "token": "TEST_TOKEN_123",
    "newPassword": "NewSecure123"
  }'
```

### Clear Rate Limit State (Frontend)

```javascript
// Open browser console (F12) on password reset request page
// Execute:
localStorage.removeItem('password_reset_rate_limit');
console.log('Rate limit state cleared');
```

### Check Token Expiration

```bash
# View all password reset tokens
docker exec -it miometry-postgres psql -U worklog -d worklog -c "
  SELECT
    token,
    m.email,
    created_at,
    expires_at,
    used,
    (expires_at > NOW()) AS is_valid
  FROM password_reset_tokens prt
  JOIN members m ON prt.member_id = m.id
  ORDER BY created_at DESC
  LIMIT 5;
"
```

---

## Troubleshooting

### Issue: "Rate limit exceeded" error on first request

**Cause**: Previous rate limit state in localStorage  
**Solution**:
```javascript
// Browser console
localStorage.removeItem('password_reset_rate_limit');
location.reload();
```

### Issue: "Invalid or expired token" error

**Cause**: Token expired (24-hour limit) or already used  
**Solution**:
```bash
# Generate new token by requesting password reset again
# Or create test token in database (see Step 7)
```

### Issue: Backend 404 error on password reset endpoints

**Cause**: Backend not running or API routes not registered  
**Solution**:
```bash
# Verify backend is running
curl http://localhost:8080/actuator/health

# Check backend logs for startup errors
cd backend
tail -f build/logs/application.log

# Restart backend
./gradlew bootRun
```

### Issue: Frontend build error "Cannot find module '@zxcvbn-ts/core'"

**Cause**: Dependencies not installed  
**Solution**:
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

### Issue: Password strength indicator not updating

**Cause**: zxcvbn options not initialized  
**Solution**:
1. Check browser console for errors
2. Verify `@zxcvbn-ts/*` packages are installed
3. Ensure `zxcvbnOptions` is configured before use

### Issue: CSRF token error on POST requests

**Cause**: CSRF cookie not set or expired  
**Solution**:
```bash
# Frontend automatically handles CSRF tokens
# If error persists, verify session cookie is set:

# Browser console
document.cookie

# Should include: XSRF-TOKEN=...
# If missing, backend session management may have issues
```

---

## Performance Benchmarks

### Expected Performance Targets

| Metric | Target | Validation Command |
|--------|--------|-------------------|
| Password strength calculation | < 100ms | Browser DevTools Performance tab |
| Request page load | < 500ms | Lighthouse (Chrome) |
| Confirm page load | < 500ms | Lighthouse (Chrome) |
| API request latency | < 200ms | Browser Network tab |
| Rate limit check | < 10ms | Console.time() in rate-limit.ts |

### Run Performance Tests

```bash
# Frontend bundle size analysis
cd frontend
npm run build
npm run analyze  # (if configured)

# Lighthouse CI (requires @lhci/cli)
npx lhci autorun --collect.url=http://localhost:3000/password-reset/request

# Manual Lighthouse audit:
# 1. Open Chrome DevTools (F12)
# 2. Navigate to "Lighthouse" tab
# 3. Select "Performance" and "Accessibility"
# 4. Click "Analyze page load"
```

**Expected Lighthouse Scores**:
| Category | Target | Importance |
|----------|--------|------------|
| Performance | 90+ | High |
| Accessibility | 95+ | Critical |
| Best Practices | 90+ | Medium |
| SEO | 90+ | Low (auth pages) |

---

## Next Steps

After completing local development setup:

1. **Implement Frontend Components** (Phase 2):
   - Create request page: `app/(auth)/password-reset/request/page.tsx`
   - Create confirm page: `app/(auth)/password-reset/confirm/page.tsx`
   - Create password strength indicator: `app/components/auth/PasswordStrengthIndicator.tsx`

2. **Configure i18n** (Phase 2):
   - Set up next-intl configuration
   - Create Japanese translations: `messages/ja.json`
   - Add English translations: `messages/en.json`

3. **Write Tests** (Phase 2):
   - Unit tests for pages (Vitest)
   - Unit tests for utilities (Vitest)
   - E2E tests for flows (Playwright)
   - Accessibility tests (axe-core)

4. **Integration Testing** (Phase 3):
   - Test with real email provider (Mailpit for dev, SendGrid for prod)
   - Test token expiration (24-hour window)
   - Test concurrent requests (rate limiting)

5. **Production Deployment** (Phase 3):
   - Configure environment variables
   - Set up email provider (SendGrid/AWS SES)
   - Enable HTTPS/TLS
   - Configure logging/monitoring

---

## References

- [OpenAPI Spec](./contracts/api-contracts.yaml): API contract details
- [Data Model](./data-model.md): Frontend TypeScript types
- [Research](./research.md): Technical decisions and alternatives
- [Spec](./spec.md): Feature requirements and user stories
- [Next.js Documentation](https://nextjs.org/docs): Framework reference
- [zxcvbn-ts](https://zxcvbn-ts.github.io/zxcvbn/): Password strength library
- [next-intl](https://next-intl-docs.vercel.app/): i18n framework

---

**Last Updated**: 2026-02-15  
**Maintainer**: Development Team  
**Questions**: Open GitHub issue or contact via team Slack
