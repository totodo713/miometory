# Quickstart Guide: ユーザーログイン認証・認可システム

**Feature Branch**: `004-user-login-auth`  
**Date**: 2026-02-03  
**Status**: Development Setup Guide  
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md)

## Overview

This guide walks you through setting up the authentication/authorization system for local development. Follow these steps to:
1. Set up the database with user authentication tables
2. Run the backend service with authentication enabled
3. Run the frontend with authentication UI
4. Create an initial admin user for testing
5. Test the complete authentication flow

**Estimated Setup Time**: 15-20 minutes

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

---

## Step 1: Database Setup

### Option A: Using Docker Compose (Recommended)

```bash
# Navigate to docker directory
cd infra/docker

# Start PostgreSQL container
docker compose up -d postgres

# Wait for PostgreSQL to be ready (10-15 seconds)
docker compose logs -f postgres
# Press Ctrl+C when you see "database system is ready to accept connections"

# Verify connection
docker exec -it miometry-postgres psql -U worklog -d worklog -c "SELECT version();"
```

### Option B: Using Local PostgreSQL

```bash
# Create database and user
psql -U postgres <<EOF
CREATE DATABASE worklog;
CREATE USER worklog WITH ENCRYPTED PASSWORD 'worklog_dev_password';
GRANT ALL PRIVILEGES ON DATABASE worklog TO worklog;
\c worklog
GRANT ALL ON SCHEMA public TO worklog;
EOF

# Set environment variable
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/worklog
export SPRING_DATASOURCE_USERNAME=worklog
export SPRING_DATASOURCE_PASSWORD=worklog_dev_password
```

### Verify Database Connection

```bash
# Test connection
psql -h localhost -U worklog -d worklog -c "\dt"

# Expected output: List of existing tables (from previous migrations)
# If you see error, check PostgreSQL is running on port 5432
```

---

## Step 2: Run Database Migrations

Flyway migrations run automatically on backend startup, but you can verify the migration files:

```bash
# View authentication migration
cat backend/src/main/resources/db/migration/V003__user_auth.sql

# Expected tables in V003:
# - roles
# - permissions
# - role_permissions
# - users
# - user_sessions
# - persistent_logins
# - password_reset_tokens
# - audit_logs
```

---

## Step 3: Backend Setup

### Configure Application Properties

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/worklog
    username: worklog
    password: worklog_dev_password
  
  flyway:
    enabled: true
    baseline-on-migrate: true
  
  security:
    remember-me:
      key: ${REMEMBER_ME_KEY:dev-secret-key-change-in-production}
  
  mail:
    host: localhost
    port: 1025  # MailHog SMTP server (for dev)
    username: ''
    password: ''
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

server:
  port: 8080
  servlet:
    session:
      timeout: 30m

logging:
  level:
    com.worklog: DEBUG
    org.springframework.security: DEBUG

# Development-only: CORS for frontend (http://localhost:3000)
cors:
  allowed-origins: http://localhost:3000
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: '*'
  allow-credentials: true
```

### Start Backend Service

```bash
# Navigate to backend directory
cd backend

# Build and run (with auto-reload)
./gradlew bootRun

# Expected output:
# - Flyway migrations executed (V003__user_auth.sql)
# - Spring Boot started on port 8080
# - Security filter chain initialized
# - No errors in logs

# In a new terminal, verify backend is running:
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Common Backend Issues

**Issue**: `Flyway migration failed: V003__user_auth.sql`
- **Solution**: Drop database and recreate: `docker compose down -v && docker compose up -d postgres`

**Issue**: `Port 8080 already in use`
- **Solution**: Kill existing process: `lsof -ti:8080 | xargs kill -9`

**Issue**: `Authentication required for /actuator/health`
- **Solution**: Check SecurityConfig.kt permits `/actuator/**` endpoints

---

## Step 4: Email Service Setup (Development)

For local development, use MailHog to capture outgoing emails:

```bash
# Start MailHog (SMTP server + web UI)
docker run -d -p 1025:1025 -p 8025:8025 --name mailhog mailhog/mailhog

# Open MailHog web UI in browser
open http://localhost:8025

# All emails sent by backend will appear in MailHog UI
# (password reset, email verification, etc.)
```

---

## Step 5: Frontend Setup

### Install Dependencies

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Expected: No errors, all packages installed successfully
```

### Configure Environment Variables

Create `frontend/.env.local`:

```bash
# Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:8080/api

# Environment
NEXT_PUBLIC_ENV=development
```

### Start Frontend Development Server

```bash
# Start Next.js dev server
npm run dev

# Expected output:
# ▲ Next.js 16.1.1
# - Local:        http://localhost:3000
# - ready in X ms

# Open in browser
open http://localhost:3000
```

### Common Frontend Issues

**Issue**: `Module not found: Can't resolve '@/lib/api/auth'`
- **Solution**: Create auth API client first (see Step 7 for implementation)

**Issue**: `CORS error when calling backend API`
- **Solution**: Verify `cors.allowed-origins` in backend `application.yml` includes `http://localhost:3000`

**Issue**: `CSRF token missing error`
- **Solution**: Call `/auth/csrf` endpoint first, then include token in X-XSRF-TOKEN header

---

## Step 6: Create Initial Admin User

### Using Database Script

```bash
# Connect to database
docker exec -it miometry-postgres psql -U worklog -d worklog

# Run SQL to create admin user
-- Get ADMIN role ID
SELECT id FROM roles WHERE name = 'ADMIN';
-- Copy the UUID (e.g., 00000000-0000-0000-0000-000000000001)

-- Create admin user (password: Admin123)
INSERT INTO users (id, email, hashed_password, name, role_id, account_status, email_verified_at)
VALUES (
    gen_random_uuid(),
    'admin@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye7I18Z2dKGO2k1NQaYnOYgK4dQmZgFqK',  -- bcrypt hash of "Admin123"
    'System Administrator',
    '00000000-0000-0000-0000-000000000001',  -- ADMIN role ID
    'active',
    NOW()
);

-- Verify admin user created
SELECT email, name, account_status FROM users WHERE email = 'admin@example.com';

-- Exit psql
\q
```

### Password Hash Generation (if needed)

```bash
# Generate bcrypt hash for a custom password
cd backend
./gradlew bootRun --args='--spring.main.web-application-type=none' <<EOF
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
println(new BCryptPasswordEncoder(10).encode("YourPassword123"))
EOF
```

### Using Backend Admin API (Alternative)

If backend has a bootstrap endpoint (to be implemented):

```bash
# POST /api/admin/bootstrap
curl -X POST http://localhost:8080/api/admin/bootstrap \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@example.com",
    "password": "Admin123",
    "name": "System Administrator"
  }'
```

---

## Step 7: Test Authentication Flow

### 1. Get CSRF Token

```bash
curl -X GET http://localhost:8080/api/auth/csrf \
  -c cookies.txt

# Expected response:
# {
#   "token": "csrf-token-abc123...",
#   "headerName": "X-XSRF-TOKEN"
# }
```

### 2. Login as Admin

```bash
# Extract CSRF token from cookies.txt
CSRF_TOKEN=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b cookies.txt \
  -c cookies.txt \
  -d '{
    "email": "admin@example.com",
    "password": "Admin123",
    "rememberMe": false
  }'

# Expected response:
# {
#   "user": {
#     "id": "...",
#     "email": "admin@example.com",
#     "name": "System Administrator",
#     "role": "ADMIN",
#     "permissions": ["user.create", "user.edit", ...],
#     "accountStatus": "active"
#   },
#   "sessionExpiresAt": "2026-02-03T15:30:00Z"
# }
```

### 3. Get Current User Profile

```bash
curl -X GET http://localhost:8080/api/users/me \
  -b cookies.txt

# Expected: Same user object as login response
```

### 4. List All Users (Admin Permission)

```bash
curl -X GET http://localhost:8080/api/users \
  -b cookies.txt

# Expected: Paginated list of users
```

### 5. Logout

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -b cookies.txt

# Expected: 204 No Content
```

### 6. Verify Session Invalidated

```bash
curl -X GET http://localhost:8080/api/users/me \
  -b cookies.txt

# Expected: 401 Unauthorized
```

---

## Step 8: Frontend Authentication UI (To Be Implemented)

### Expected File Structure

```
frontend/app/
├── (auth)/                  # Auth route group (unauthenticated)
│   ├── login/
│   │   └── page.tsx         # Login page
│   ├── signup/
│   │   └── page.tsx         # Signup page
│   └── password-reset/
│       ├── page.tsx         # Request reset page
│       └── [token]/
│           └── page.tsx     # Confirm reset page
├── components/
│   ├── auth/
│   │   ├── LoginForm.tsx
│   │   ├── SignupForm.tsx
│   │   ├── PasswordResetForm.tsx
│   │   └── UnverifiedBanner.tsx  # FR-017: Email verification warning
│   └── shared/
│       └── ProtectedRoute.tsx
├── hooks/
│   ├── useAuth.ts           # Auth context hook
│   └── usePermission.ts     # Permission check hook
└── lib/
    ├── api/
    │   ├── auth.ts          # Auth API client
    │   └── users.ts         # User API client
    └── types/
        └── auth.ts          # Auth-related types
```

### Implementation Checklist

- [ ] AuthProvider component with session management
- [ ] useAuth hook for accessing current user
- [ ] usePermission hook for permission checks
- [ ] LoginForm with email/password/rememberMe fields
- [ ] SignupForm with email/password/name fields + password strength indicator
- [ ] PasswordResetForm (request + confirm)
- [ ] UnverifiedBanner for users with account_status = 'unverified'
- [ ] ProtectedRoute component for authenticated routes
- [ ] CSRF token management in API client
- [ ] Session timeout warning (25 minutes)

---

## Step 9: Verify System Health

### Backend Health Check

```bash
# Actuator health endpoint
curl http://localhost:8080/actuator/health | jq

# Expected:
# {
#   "status": "UP",
#   "components": {
#     "db": {
#       "status": "UP",
#       "details": {
#         "database": "PostgreSQL",
#         "validationQuery": "isValid()"
#       }
#     },
#     "diskSpace": { "status": "UP" },
#     "ping": { "status": "UP" }
#   }
# }
```

### Database Tables Verification

```bash
docker exec -it miometry-postgres psql -U worklog -d worklog <<EOF
-- Verify all tables exist
\dt

-- Expected tables:
-- roles, permissions, role_permissions, users, user_sessions,
-- persistent_logins, password_reset_tokens, audit_logs

-- Count seed data
SELECT 'Roles' AS entity, COUNT(*) AS count FROM roles
UNION ALL
SELECT 'Permissions', COUNT(*) FROM permissions
UNION ALL
SELECT 'Role-Permissions', COUNT(*) FROM role_permissions
UNION ALL
SELECT 'Users', COUNT(*) FROM users;

-- Expected:
-- Roles: 3 (ADMIN, USER, MODERATOR)
-- Permissions: 18 (user.create, user.edit, ...)
-- Role-Permissions: ~30 (various role-permission mappings)
-- Users: 1 (admin@example.com)
EOF
```

### Test Email Sending

```bash
# Trigger password reset email
curl -X POST http://localhost:8080/api/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@example.com"}'

# Check MailHog UI: http://localhost:8025
# Expected: Email with password reset link
```

---

## Step 10: Run Tests

### Backend Tests

```bash
cd backend

# Run all tests
./gradlew test

# Run auth-specific tests only
./gradlew test --tests "com.worklog.api.auth.*"
./gradlew test --tests "com.worklog.application.auth.*"
./gradlew test --tests "com.worklog.domain.user.*"

# Generate coverage report
./gradlew jacocoTestReport

# View coverage report
open build/reports/jacoco/html/index.html
```

### Frontend Tests

```bash
cd frontend

# Run all tests
npm test

# Run auth-specific tests
npm test -- auth

# Run with coverage
npm test -- --coverage

# View coverage report
open coverage/index.html
```

---

## Troubleshooting

### Database Connection Issues

**Symptom**: `Connection refused: localhost:5432`

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Restart PostgreSQL
docker compose restart postgres

# Check logs
docker compose logs postgres
```

### Session Not Persisting

**Symptom**: `/api/users/me` returns 401 after login

```bash
# Check session cookie in browser DevTools
# Application > Cookies > http://localhost:3000
# Should see: JSESSIONID, XSRF-TOKEN

# Verify backend session management
curl -v http://localhost:8080/api/auth/login ...
# Look for Set-Cookie header with JSESSIONID
```

### Permission Denied Errors

**Symptom**: 403 Forbidden when accessing admin endpoints

```bash
# Verify user has correct role
curl http://localhost:8080/api/users/me -b cookies.txt | jq '.role'

# Check role has required permissions
docker exec -it miometry-postgres psql -U worklog -d worklog <<EOF
SELECT r.name AS role, p.name AS permission
FROM roles r
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE r.name = 'ADMIN';
EOF
```

### Email Not Sending

**Symptom**: Password reset email not received in MailHog

```bash
# Check backend logs for email errors
docker compose logs backend | grep -i "email\|mail"

# Verify MailHog is running
curl http://localhost:8025/api/v2/messages | jq

# Check backend mail configuration
grep -A 10 "spring.mail" backend/src/main/resources/application.yml
```

---

## Next Steps

1. **Implement Frontend Auth UI**: Complete the components listed in Step 8
2. **Write Tests**: Add unit/integration tests for auth flows
3. **Security Audit**: Review CSRF, CORS, session management configurations
4. **Performance Testing**: Load test with 100+ concurrent users (SC-003)
5. **Documentation**: Update API docs with authentication examples

---

## Quick Reference

### Important URLs

| Service | URL | Description |
|---------|-----|-------------|
| Frontend | http://localhost:3000 | Next.js dev server |
| Backend API | http://localhost:8080/api | Spring Boot REST API |
| Backend Health | http://localhost:8080/actuator/health | Health check endpoint |
| MailHog UI | http://localhost:8025 | Email inbox (dev) |
| PostgreSQL | localhost:5432 | Database server |

### Default Credentials

| Account | Email | Password | Role |
|---------|-------|----------|------|
| Admin | admin@example.com | Admin123 | ADMIN |

### Common Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# View logs
docker compose logs -f [service_name]

# Restart backend
cd backend && ./gradlew bootRun

# Restart frontend
cd frontend && npm run dev

# Run migrations
# (automatic on backend startup)

# Drop database (reset)
docker compose down -v && docker compose up -d postgres
```

---

## Support

**Issue**: Not covered by this guide?

1. Check [spec.md](./spec.md) for functional requirements
2. Review [research.md](./research.md) for technical decisions
3. Inspect [data-model.md](./data-model.md) for entity schemas
4. See API contracts: [auth-api.yaml](./contracts/auth-api.yaml), [user-api.yaml](./contracts/user-api.yaml)
5. Open issue on GitHub with reproduction steps

**Last Updated**: 2026-02-03
