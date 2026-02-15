# Technical Research: ユーザーログイン認証・認可システム

**Feature Branch**: `004-user-login-auth`  
**Date**: 2026-02-03  
**Status**: Phase 0 Complete  
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md)

## Summary

This document resolves the 6 technical research topics identified in plan.md for implementing user authentication and authorization in the Miometry system. All decisions are based on existing project dependencies (Spring Boot 3.5.9, Kotlin 2.3, PostgreSQL) and constitution requirements for security, performance, and maintainability.

---

## Research Topic 1: Password Hashing Algorithm

### Decision
**Use bcrypt via Spring Security's `BCryptPasswordEncoder`**

### Rationale
1. **Spring Security Default**: BCryptPasswordEncoder is the default and recommended password encoder in Spring Security 6.x (included in Spring Boot 3.5.9)
2. **Security Strength**: 
   - Uses adaptive hashing with configurable strength (default: 10 rounds)
   - Automatically handles salt generation and storage
   - Industry-proven resistance to rainbow table and brute-force attacks
3. **Performance**: 
   - Configurable work factor allows balancing security vs performance
   - Default strength (10) provides ~100ms hashing time, acceptable for login flows
4. **Compatibility**: 
   - Zero additional dependencies (already in `spring-boot-starter-security`)
   - Native support in Spring Security's authentication flow

### Alternatives Considered
- **Argon2**: More modern and resistant to GPU/ASIC attacks, but requires additional dependency (`spring-security-crypto` + Bouncy Castle) and has limited Spring Security integration
- **PBKDF2**: Supported by Spring Security (`Pbkdf2PasswordEncoder`), but bcrypt is more resistant to GPU-based attacks due to memory-hard properties
- **SCrypt**: Strong security but higher memory consumption; not natively supported by Spring Security

### Implementation Notes
```kotlin
@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(10) // strength = 10 (default)
    }
}
```

**Password validation requirements (FR-003)**:
- Minimum 8 characters
- At least 1 digit
- At least 1 uppercase letter
- Regex: `^(?=.*[0-9])(?=.*[A-Z]).{8,}$`

---

## Research Topic 2: Session Management Approach

### Decision
**Use Spring Security Session Management with PostgreSQL-backed persistence (no Redis required initially)**

### Rationale
1. **Simplicity**: 
   - Spring Security's built-in session management handles authentication state, CSRF protection, and remember-me functionality
   - No additional infrastructure (Redis) required for MVP
2. **Multiple Device Support (FR-013)**: 
   - Spring Security's `sessionConcurrency().maximumSessions(-1)` allows unlimited concurrent sessions per user
   - Each session stored with unique session ID
3. **30-Day Persistence (FR-014)**: 
   - Remember-me feature using persistent token repository (PostgreSQL table: `persistent_logins`)
   - Tokens stored with expiration timestamp (30 days from creation)
4. **Performance**: 
   - PostgreSQL session storage sufficient for 100+ concurrent users (SC-003)
   - Can migrate to Spring Session + Redis later if >500 concurrent users or distributed deployment needed
5. **Security**: 
   - HTTP-only, Secure, SameSite=Strict cookies
   - Automatic CSRF token generation and validation

### Alternatives Considered
- **Spring Session + Redis**: 
  - Pros: Better horizontal scalability, faster session lookup
  - Cons: Additional infrastructure complexity, not needed for target scale (100 users)
- **JWT Tokens**: 
  - Pros: Stateless, no server-side storage
  - Cons: Cannot revoke tokens before expiration (violates FR-013 for immediate logout), larger cookie size

### Implementation Notes
**Security Configuration**:
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .sessionManagement { session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(-1) // unlimited concurrent sessions
                    .maxSessionsPreventsLogin(false)
            }
            .rememberMe { remember ->
                remember
                    .tokenRepository(persistentTokenRepository())
                    .tokenValiditySeconds(30 * 24 * 60 * 60) // 30 days
            }
            .logout { logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .deleteCookies("JSESSIONID", "remember-me")
                    .invalidateHttpSession(true)
            }
        return http.build()
    }
}
```

**Database Schema (V003__user_auth.sql)**:
```sql
-- Spring Security remember-me persistent tokens
CREATE TABLE persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);

-- Custom session tracking for audit logs
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    ip_address INET,
    user_agent TEXT
);
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
```

**Session Timeout (FR-013)**:
- Default: 30 minutes of inactivity
- Configuration: `server.servlet.session.timeout=30m` (application.yml)

---

## Research Topic 3: Email Service

### Decision
**Use Spring Boot Mail (`spring-boot-starter-mail`) with SMTP configuration**

### Rationale
1. **Zero Additional Cost**: 
   - SMTP server can be self-hosted (e.g., Postfix) or use free tier of Gmail/Outlook SMTP
   - No per-email API charges like SendGrid/AWS SES
2. **Simplicity**: 
   - Spring Boot's `JavaMailSender` provides simple, testable API
   - Template-based email generation with Thymeleaf or plain text
3. **Error Handling (FR-011)**: 
   - Email failures logged internally, user always sees success message for security (prevents email enumeration attacks)
   - Async sending with retry mechanism via Spring `@Async` + `@Retryable`
4. **Testing**: 
   - Easily mockable in unit tests
   - Integration tests can use embedded SMTP server (GreenMail)

### Alternatives Considered
- **SendGrid API**: 
  - Pros: Higher deliverability, detailed analytics, no SMTP config
  - Cons: Additional dependency, cost ($15/month for 40k emails), external service dependency
- **AWS SES**: 
  - Pros: AWS ecosystem integration, pay-per-use ($0.10/1000 emails)
  - Cons: Requires AWS credentials, region-specific configuration, initial sandbox restrictions

### Implementation Notes
**Gradle Dependency**:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-mail")
}
```

**Configuration (application.yml)**:
```yaml
spring:
  mail:
    host: smtp.gmail.com  # or self-hosted SMTP server
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 5000
```

**Email Service**:
```kotlin
@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val auditLogRepository: AuditLogRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @Retryable(
        value = [MailException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    suspend fun sendPasswordResetEmail(email: String, token: String) {
        try {
            val message = mailSender.createMimeMessage()
            message.setRecipients(Message.RecipientType.TO, email)
            message.subject = "Password Reset Request"
            message.setText("""
                Click the link below to reset your password:
                ${getResetLink(token)}
                
                This link expires in 24 hours.
            """.trimIndent())
            
            mailSender.send(message)
            logger.info("Password reset email sent to $email")
        } catch (e: MailException) {
            logger.error("Failed to send password reset email to $email", e)
            auditLogRepository.save(
                AuditLog(
                    eventType = "EMAIL_SEND_FAILURE",
                    details = "Password reset email failed: ${e.message}",
                    ipAddress = null
                )
            )
            throw e // trigger retry
        }
    }
}
```

**Testing with GreenMail**:
```kotlin
@SpringBootTest
@ExtendWith(GreenMailExtension::class)
class EmailServiceTest {
    @Test
    fun `should send password reset email`(greenMail: GreenMail) {
        emailService.sendPasswordResetEmail("test@example.com", "token123")
        
        val messages = greenMail.receivedMessages
        assertThat(messages).hasSize(1)
        assertThat(messages[0].subject).isEqualTo("Password Reset Request")
    }
}
```

---

## Research Topic 4: Permission Management Pattern

### Decision
**Use Spring Security Method Security with custom `PermissionEvaluator`**

### Rationale
1. **Fine-Grained Control (FR-009, FR-019)**: 
   - Supports function-level permissions (e.g., `user.create`, `user.edit`, `report.view`)
   - Declarative via `@PreAuthorize` annotations on controller/service methods
2. **Database-Driven**: 
   - Permissions stored in PostgreSQL (`permissions`, `role_permissions` tables)
   - Dynamic permission changes without code deployment
3. **Performance**: 
   - Permissions cached in authentication token (no DB lookup per request)
   - Optional Redis caching for permission resolution if needed
4. **Testability**: 
   - Easy to test with `@WithMockUser` annotations in Spring Security Test

### Alternatives Considered
- **Hard-coded Role Checks**: 
  - Pros: Simple, no DB schema
  - Cons: Not flexible, requires code changes for new permissions (violates FR-019)
- **Spring Security ACL**: 
  - Pros: Object-level permissions (e.g., "user A can edit document B")
  - Cons: Over-engineered for function-level permissions, complex schema

### Implementation Notes
**Database Schema**:
```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE, -- e.g., 'user.create', 'report.view'
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Seed initial permissions
INSERT INTO permissions (name, description) VALUES
    ('user.create', 'Create new users'),
    ('user.edit', 'Edit existing users'),
    ('user.delete', 'Delete users'),
    ('user.view', 'View user details'),
    ('report.view', 'View reports'),
    ('report.export', 'Export reports'),
    ('admin.access', 'Access admin panel');
```

**Custom PermissionEvaluator**:
```kotlin
@Component
class CustomPermissionEvaluator(
    private val permissionRepository: PermissionRepository
) : PermissionEvaluator {
    
    override fun hasPermission(
        authentication: Authentication,
        targetDomainObject: Any?,
        permission: Any
    ): Boolean {
        val user = authentication.principal as UserDetails
        val permissionName = permission.toString()
        
        return user.authorities.any { 
            it.authority == permissionName 
        }
    }
    
    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable?,
        targetType: String?,
        permission: Any
    ): Boolean {
        return hasPermission(authentication, null, permission)
    }
}
```

**Method Security Configuration**:
```kotlin
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig {
    @Bean
    fun methodSecurityExpressionHandler(
        permissionEvaluator: PermissionEvaluator
    ): MethodSecurityExpressionHandler {
        val handler = DefaultMethodSecurityExpressionHandler()
        handler.setPermissionEvaluator(permissionEvaluator)
        return handler
    }
}
```

**Usage in Controllers**:
```kotlin
@RestController
@RequestMapping("/api/users")
class UserController {
    
    @PreAuthorize("hasAuthority('user.create')")
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse {
        // Only users with 'user.create' permission can execute this
    }
    
    @PreAuthorize("hasAuthority('user.edit')")
    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: UUID, @RequestBody request: UpdateUserRequest): UserResponse {
        // Only users with 'user.edit' permission can execute this
    }
}
```

---

## Research Topic 5: Frontend Session Management

### Decision
**Use HTTP-only Cookies with custom auth context (no NextAuth.js)**

### Rationale
1. **Next.js 16 Compatibility**: 
   - NextAuth.js v5 (Auth.js) has breaking changes and complexity
   - Custom implementation provides full control over authentication flow
2. **Security (CSRF Protection)**: 
   - HTTP-only, Secure, SameSite=Strict cookies prevent XSS attacks
   - Spring Security's built-in CSRF token protection for state-changing requests
   - CSRF token stored in custom header (X-XSRF-TOKEN) for API calls
3. **Session Persistence**: 
   - Backend handles all session state (see Research Topic 2)
   - Frontend only stores session ID in cookie (no sensitive data)
4. **Simplicity**: 
   - No OAuth provider configuration needed
   - Direct integration with Spring Security's session management

### Alternatives Considered
- **NextAuth.js**: 
  - Pros: OAuth integration, session management UI
  - Cons: Overkill for simple email/password auth, adds complexity, requires database adapter configuration
- **localStorage + JWT**: 
  - Pros: Works for SPAs, no cookie concerns
  - Cons: Vulnerable to XSS, cannot use HTTP-only protection, larger token size

### Implementation Notes
**API Client (lib/api/auth.ts)**:
```typescript
// CSRF token management
let csrfToken: string | null = null;

async function getCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  
  const response = await fetch('/api/csrf', {
    credentials: 'include', // send cookies
  });
  const data = await response.json();
  csrfToken = data.token;
  return csrfToken;
}

export async function login(email: string, password: string): Promise<void> {
  const token = await getCsrfToken();
  
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': token, // CSRF token
    },
    credentials: 'include', // send/receive cookies
    body: JSON.stringify({ email, password }),
  });
  
  if (!response.ok) {
    throw new Error('Login failed');
  }
  
  // Session cookie automatically set by backend
}

export async function logout(): Promise<void> {
  const token = await getCsrfToken();
  
  await fetch('/api/auth/logout', {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': token,
    },
    credentials: 'include',
  });
  
  csrfToken = null;
}

export async function getCurrentUser(): Promise<User | null> {
  const response = await fetch('/api/users/me', {
    credentials: 'include',
  });
  
  if (response.status === 401) {
    return null; // not authenticated
  }
  
  return response.json();
}
```

**Auth Context (hooks/useAuth.ts)**:
```typescript
import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { getCurrentUser, login as apiLogin, logout as apiLogout } from '@/lib/api/auth';

interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  permissions: string[];
  accountStatus: 'active' | 'unverified' | 'locked';
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getCurrentUser()
      .then(setUser)
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    await apiLogin(email, password);
    const user = await getCurrentUser();
    setUser(user);
  };

  const logout = async () => {
    await apiLogout();
    setUser(null);
  };

  const hasPermission = (permission: string) => {
    return user?.permissions.includes(permission) ?? false;
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, hasPermission }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
```

**Protected Route Middleware (middleware.ts)**:
```typescript
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const sessionCookie = request.cookies.get('JSESSIONID');
  
  // Redirect to login if not authenticated
  if (!sessionCookie && !request.nextUrl.pathname.startsWith('/login')) {
    return NextResponse.redirect(new URL('/login', request.url));
  }
  
  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all request paths except:
     * - /login, /signup, /password-reset (public auth pages)
     * - /api/auth/* (auth endpoints)
     * - /_next/static (static files)
     * - /_next/image (image optimization files)
     * - /favicon.ico (favicon file)
     */
    '/((?!login|signup|password-reset|api/auth|_next/static|_next/image|favicon.ico).*)',
  ],
};
```

---

## Research Topic 6: Audit Log Deletion Strategy

### Decision
**Use PostgreSQL TTL via scheduled job (Spring Scheduler)**

### Rationale
1. **Performance Impact**: 
   - Scheduled job runs off-peak hours (e.g., daily at 2 AM)
   - Batch deletion (DELETE with LIMIT) prevents long-running transactions
   - Minimal impact on active user sessions
2. **Data Integrity**: 
   - Explicit deletion logic auditable in application code
   - Can implement soft-delete first if needed (deleted_at column)
   - Transaction-safe with rollback on failure
3. **Simplicity**: 
   - No external dependencies (vs. pg_cron extension)
   - Spring Scheduler built into Spring Boot
   - Testable with `@EnableScheduling` in tests
4. **Compliance**: 
   - 90-day retention (FR-020) enforced consistently
   - Deletion events themselves logged for compliance

### Alternatives Considered
- **PostgreSQL TTL (pg_cron extension)**: 
  - Pros: Database-level automation, no application code
  - Cons: Requires superuser to install extension, not available in managed PostgreSQL (AWS RDS free tier)
- **Manual Archive**: 
  - Pros: Full control, can export to cold storage
  - Cons: Operational burden, human error risk

### Implementation Notes
**Audit Log Entity with Retention**:
```kotlin
@Table("audit_logs")
data class AuditLog(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID?,
    val eventType: String,
    val ipAddress: String?,
    val timestamp: Instant = Instant.now(),
    val details: String?,
    val retentionDays: Int = 90 // configurable per event type if needed
)
```

**Scheduled Deletion Job**:
```kotlin
@Component
class AuditLogCleanupScheduler(
    private val auditLogRepository: AuditLogRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    fun deleteExpiredAuditLogs() {
        logger.info("Starting audit log cleanup job")
        
        val cutoffDate = Instant.now().minus(90, ChronoUnit.DAYS)
        val batchSize = 1000
        var totalDeleted = 0
        
        try {
            var deletedInBatch: Int
            do {
                deletedInBatch = auditLogRepository.deleteExpiredLogs(cutoffDate, batchSize)
                totalDeleted += deletedInBatch
                logger.debug("Deleted $deletedInBatch audit logs in current batch")
            } while (deletedInBatch == batchSize)
            
            logger.info("Audit log cleanup completed: $totalDeleted logs deleted")
            
            // Log the cleanup event itself
            auditLogRepository.save(
                AuditLog(
                    userId = null,
                    eventType = "AUDIT_LOG_CLEANUP",
                    details = "Deleted $totalDeleted audit logs older than 90 days",
                    ipAddress = null
                )
            )
        } catch (e: Exception) {
            logger.error("Audit log cleanup failed", e)
            throw e
        }
    }
}
```

**Repository Method**:
```kotlin
interface AuditLogRepository : CrudRepository<AuditLog, UUID> {
    @Modifying
    @Query("""
        DELETE FROM audit_logs 
        WHERE timestamp < :cutoffDate 
        AND id IN (
            SELECT id FROM audit_logs 
            WHERE timestamp < :cutoffDate 
            LIMIT :batchSize
        )
    """)
    fun deleteExpiredLogs(cutoffDate: Instant, batchSize: Int): Int
}
```

**Configuration (application.yml)**:
```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2 # separate thread pool for scheduled tasks
      
audit-log:
  retention-days: 90
  cleanup-batch-size: 1000
  cleanup-cron: "0 0 2 * * *" # daily at 2 AM
```

---

## Summary of Decisions

| Research Topic | Decision | Key Rationale |
|----------------|----------|---------------|
| Password Hashing | bcrypt (BCryptPasswordEncoder) | Spring Security default, proven security, zero dependencies |
| Session Management | Spring Security + PostgreSQL | Sufficient for 100 users, supports multi-device, no Redis complexity |
| Email Service | Spring Boot Mail + SMTP | Zero cost, simple, testable, retry mechanism |
| Permission Management | Method Security + PermissionEvaluator | Fine-grained function-level control, database-driven, cacheable |
| Frontend Session | HTTP-only Cookies + custom auth | Security (CSRF), simple, no NextAuth.js complexity |
| Audit Log Deletion | Spring Scheduler + batch deletion | Predictable performance, auditable, no DB extensions |

---

## Next Steps

Proceed to **Phase 1: Design & Contracts** to generate:
1. `data-model.md`: Entity definitions with validation rules and state transitions
2. `contracts/auth-api.yaml`: OpenAPI spec for authentication endpoints
3. `contracts/user-api.yaml`: OpenAPI spec for user management endpoints
4. `quickstart.md`: Local development setup guide

All decisions in this research document will inform the detailed design in Phase 1.
