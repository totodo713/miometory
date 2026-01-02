# Research Documentation: Work-Log Entry System

**Feature**: 002-work-log-entry  
**Date**: 2026-01-02  
**Purpose**: Technical research to resolve unknowns from implementation plan  
**Status**: ✅ Complete

This document consolidates research findings for technical decisions that were marked as "NEEDS CLARIFICATION" in the Technical Context section of [plan.md](./plan.md).

---

## Table of Contents

1. [Frontend Testing Framework](#1-frontend-testing-framework)
2. [SSO/SAML/OAuth2 Integration](#2-ssosamloidc-oauth2-integration)
3. [Streaming CSV Processing](#3-streaming-csv-processing)
4. [Auto-Save Implementation](#4-auto-save-implementation)
5. [Database Encryption at Rest](#5-database-encryption-at-rest)
6. [Session Timeout Implementation](#6-session-timeout-implementation)
7. [Best Practices](#best-practices)

---

## 1. Frontend Testing Framework

### Decision
**Vitest + React Testing Library (Unit/Component) + Playwright (E2E)**

### Rationale
- **Next.js 16 officially recommends Vitest** for unit testing alongside Jest - Vitest has better performance and modern DX
- **React 19 compatibility**: Both Vitest and Playwright fully support React 19.2.3
- **TypeScript 5.x support**: Native TypeScript support without complex configuration
- **Performance**: Vitest is 5-10x faster than Jest due to Vite's architecture and ES modules
- **Biome compatibility**: Works seamlessly with Biome linting (no ESLint conflicts)
- **Next.js App Router support**: Vitest works well with App Router components (except async Server Components - use E2E for those)
- **Community momentum**: Vitest is gaining significant adoption in modern React/Next.js projects
- **E2E coverage**: Playwright provides robust E2E testing for async Server Components and full user flows

### Alternatives Considered

**Jest + React Testing Library**:
- ❌ Slower test execution (no native ESM support)
- ❌ More complex configuration with Next.js 16
- ❌ Requires additional transform setup
- ✅ Still viable but older technology

**Vitest only (without E2E)**:
- ❌ Cannot test async Server Components (Next.js 16 limitation)
- ❌ Missing integration/full-stack testing capabilities

**Cypress for E2E**:
- ❌ Heavier and slower than Playwright
- ❌ Less modern API
- ✅ Good option but Playwright is now preferred by Next.js team

### Setup Requirements

**Package Dependencies**:
```bash
npm install -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/dom @testing-library/jest-dom vite-tsconfig-paths @playwright/test
```

**Configuration Files**:

1. **`vitest.config.mts`** (root directory):
```typescript
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
  },
})
```

2. **`vitest.setup.ts`** (root directory):
```typescript
import '@testing-library/jest-dom'
```

3. **`playwright.config.ts`** (auto-generated via `npm init playwright`):
```typescript
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run start',
    port: 3000,
    reuseExistingServer: !process.env.CI,
  },
})
```

**Scripts to Add to package.json**:
```json
{
  "scripts": {
    "test": "vitest",
    "test:ui": "vitest --ui",
    "test:coverage": "vitest --coverage",
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui"
  }
}
```

---

## 2. SSO/SAML/OAuth2 Integration

### Decision
**Spring Security OAuth2 Client (spring-security-oauth2-client) + Spring Security SAML2 (spring-security-saml2-service-provider) in a Combination Approach**

### Rationale
- Spring Boot 3.5.9 has native, first-class support for both OAuth2/OIDC and SAML2 without requiring external extensions
- OAuth2 Client handles modern providers (OpenID Connect, OAuth2) including role/claim mapping from JWT tokens
- SAML2 support is built directly into Spring Security 6.x+ (no longer requires separate extensions)
- Both can coexist in the same application, allowing organizations to support multiple identity provider types
- Session management with configurable timeout is built-in and works seamlessly with both authentication methods
- Spring Security 6.x introduced `requireExplicitSave` (default in Spring Boot 3.x), providing better session control
- Configuration via `application.yml` properties is straightforward for both protocols

### Alternatives Considered

**Spring Security SAML Extension**:
- ❌ **Rejected** - Deprecated and replaced by native SAML2 support in Spring Security 6.x+
- No longer recommended for Spring Boot 3.x

**OAuth2 Resource Server only**:
- ❌ **Rejected** - Designed for stateless API protection with Bearer tokens, not session-based user authentication with SSO

**SAML2 only**:
- ❌ **Rejected** - Doesn't support modern OAuth2/OIDC providers (Azure AD with OAuth2, Okta OIDC, etc.)
- Limits organizational flexibility

### Setup Requirements

**Dependencies (build.gradle.kts)**:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
}

repositories {
    mavenCentral()
    maven { url = uri("https://build.shibboleth.net/maven/releases/") }
}
```

**Configuration Properties (application.yaml)**:
```yaml
# OAuth2/OIDC Configuration
spring:
  security:
    oauth2:
      client:
        registration:
          azure:
            provider: azure
            client-id: ${AZURE_CLIENT_ID}
            client-secret: ${AZURE_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: openid,profile,email
        provider:
          azure:
            issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0

# SAML2 Configuration
spring:
  security:
    saml2:
      relyingparty:
        registration:
          okta:
            signing.credentials:
              - private-key-location: classpath:saml-rp.key
                certificate-location: classpath:saml-rp.crt
            assertingparty:
              entity-id: ${OKTA_ISSUER}
              verification.credentials:
                - certificate-location: classpath:okta-idp.crt
              singlesignon:
                url: ${OKTA_SSO_URL}
                sign-request: true

# Session Management (30-minute timeout)
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        http-only: true
        secure: true
```

**Security Configuration Code Structure**:
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/", "/login/**").permitAll()
                    .anyRequest().authenticated()
            }
            // OAuth2 Login for OIDC providers
            .oauth2Login { oauth2 ->
                oauth2.loginPage("/login")
            }
            // SAML2 Login for SAML providers
            .saml2Login { saml2 ->
                saml2.loginPage("/login")
            }
            // Session Management
            .sessionManagement { session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            }
        
        return http.build()
    }

    @Bean
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultOAuth2UserService()
        return OAuth2UserService { request ->
            val user = delegate.loadUser(request)
            // Map roles from HR system claims
            val authorities = extractAuthorities(user)
            DefaultOAuth2User(authorities, user.attributes, "email")
        }
    }

    private fun extractAuthorities(user: OAuth2User): Set<GrantedAuthority> {
        val roles: List<String> = user.getAttribute("roles") ?: emptyList()
        return roles.map { SimpleGrantedAuthority("ROLE_$it") }.toSet()
    }
}
```

---

## 3. Streaming CSV Processing

### Decision
**Apache Commons CSV with Async Processing + WebSocket for Progress Reporting**

### Rationale
- **Memory efficiency**: Apache Commons CSV provides true streaming parser with `CSVParser` that reads row-by-row without loading entire file into memory, perfect for multi-GB files
- **Spring Boot 3.5.9 compatibility**: Lightweight library (no version conflicts), easy integration with Spring's `@Async` and `TaskExecutor`
- **Real-time progress**: Natural fit with Spring WebSocket/STOMP for bi-directional progress updates without polling overhead
- **Performance**: Can easily exceed 100 rows/second (typically 1000-5000+ rows/sec for simple processing)
- **Row-by-row validation**: Iterator-based approach allows immediate validation and error collection per row
- **Simpler than Spring Batch**: No job repository overhead, direct control over processing logic, easier to maintain

### Alternatives Considered

**Spring Batch**:
- ❌ **Rejected** - Adds complexity (job repository, chunk semantics, transaction overhead) that's unnecessary for streaming use case
- Better suited for complex ETL pipelines with restart/recovery requirements
- Job repository adds database overhead that can slow down simple imports

**OpenCSV**:
- ❌ **Rejected** - While it has streaming capabilities, Apache Commons CSV has better RFC 4180 compliance, more active maintenance, and cleaner API for Spring integration

**Custom streaming parser with reactive streams**:
- ❌ **Rejected** - Over-engineering
- Project Reactor adds complexity without significant benefit for file I/O (which is inherently blocking)
- Apache Commons CSV with async processing provides same memory efficiency with simpler code

### Implementation Approach

**Architecture Pattern**:
1. Async Controller Pattern
   - REST endpoint accepts multipart file upload
   - Immediately returns import job ID
   - `@Async` service processes file in background thread
   - Progress updates pushed via WebSocket

2. Progress Channel Architecture
   - `SimpMessagingTemplate` for WebSocket broadcasts
   - `ProgressTracker` bean with import statistics (rows processed, errors, percentage, ETA)
   - Non-blocking updates every N rows (e.g., every 100 rows)

**Progress Reporting Mechanism**:
- WebSocket with STOMP protocol (Spring's built-in support)
- Topic-based: `/topic/import/{jobId}`
- Message format: `{ "jobId": "uuid", "rowsProcessed": 1500, "totalRows": 10000, "percentage": 15.0, "errors": 3, "estimatedTimeRemaining": "45s", "status": "PROCESSING" }`
- Client subscribes before upload, receives real-time updates
- Advantage over SSE: Bi-directional (can cancel), better Spring support
- Advantage over polling: Real-time, no server overhead

**Error Handling Strategy**:
1. **Validation Errors (row-level)**
   - Collect errors with row numbers: `List<ImportError>`
   - Continue processing (skip invalid rows)
   - Include in progress updates
   - Return full error report at end

2. **Fatal Errors (file-level)**
   - File format issues, I/O errors
   - Stop processing immediately
   - Send error status via WebSocket
   - Clean up resources

3. **Memory Safety**
   - Process with fixed buffer (e.g., 1000 rows in memory max)
   - Flush to database in batches using Spring Data JDBC batch insert
   - Clear processed rows from memory

**Dependencies (build.gradle.kts)**:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.apache.commons:commons-csv:1.12.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
}
```

**Sample Code Structure**:
```kotlin
@RestController
@RequestMapping("/api/imports")
class ImportController(private val importService: ImportService) {
    
    @PostMapping("/csv")
    fun uploadCsv(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportJobResponse> {
        val jobId = UUID.randomUUID().toString()
        importService.processAsync(jobId, file)
        return ResponseEntity.ok(ImportJobResponse(jobId))
    }
}

@Service
class ImportService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val repository: WorkLogRepository
) {
    
    @Async("importTaskExecutor")
    fun processAsync(jobId: String, file: MultipartFile) {
        file.inputStream.reader().use { reader ->
            CSVParser(reader, CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()).use { parser ->
                
                val totalRows = estimateRowCount(file)
                var processedRows = 0L
                val batch = mutableListOf<WorkLogEntry>()
                val errors = mutableListOf<ImportError>()
                
                for (record in parser) {
                    try {
                        val entry = validateAndMap(record)
                        batch.add(entry)
                        
                        if (batch.size >= 100) {
                            repository.saveAll(batch)
                            batch.clear()
                        }
                    } catch (e: ValidationException) {
                        errors.add(ImportError(record.recordNumber, e.message))
                    }
                    
                    processedRows++
                    
                    if (processedRows % 100 == 0L) {
                        sendProgress(jobId, processedRows, totalRows, errors.size)
                    }
                }
                
                if (batch.isNotEmpty()) {
                    repository.saveAll(batch)
                }
                
                sendCompletion(jobId, processedRows, errors)
            }
        }
    }
    
    private fun sendProgress(jobId: String, processed: Long, total: Long, errorCount: Int) {
        val update = ProgressUpdate(
            jobId, processed, total,
            (processed * 100.0 / total),
            errorCount,
            estimateTimeRemaining(processed, total),
            "PROCESSING"
        )
        messagingTemplate.convertAndSend("/topic/import/$jobId", update)
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")
        config.setApplicationDestinationPrefixes("/app")
    }
    
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/import")
            .setAllowedOrigins("*")
            .withSockJS()
    }
}
```

**Performance Expectations**:
- Simple validation: 2000-5000 rows/second
- Complex validation + DB insert: 500-1500 rows/second
- Easily exceeds 100 rows/second requirement (SC-005)
- Memory usage: Constant (~50MB regardless of file size)

---

## 4. Auto-Save Implementation

### Decision
**TanStack Query (React Query) with Optimistic Updates + localStorage Backup**

### Rationale
- Built-in retry logic and network failure recovery with configurable retry strategies
- Native support for optimistic updates and rollback mechanisms via `onMutate`/`onError` callbacks
- Excellent React 19 compatibility with concurrent rendering support
- Comprehensive mutation state tracking (`isPending`, `isError`, `isSuccess`, `variables`)
- TypeScript-first design with full type inference
- Built-in mutation queuing and deduplication capabilities
- Proven at scale with 99.9%+ reliability when configured properly

### Alternatives Considered

**Custom debounced service with localStorage**:
- ❌ **Rejected** - Requires building retry logic, queue management, and error handling from scratch
- More code to maintain, higher risk of bugs

**SWR with auto-revalidation**:
- ❌ **Rejected** - Excellent for fetching, but limited mutation/optimistic update support
- Lacks robust retry mechanisms for mutations

**useEffect with debounced API calls**:
- ❌ **Rejected** - Too low-level, requires manual implementation of all reliability features
- High maintenance burden, difficult to achieve 99.9% reliability

### Implementation Approach

**Hook Architecture (`useAutoSave`)**:
```typescript
// hooks/useAutoSave.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

interface AutoSaveOptions {
  mutationFn: (data: any) => Promise<any>
  interval?: number // default 60000ms
  enabled?: boolean
  onSuccess?: (data: any) => void
  onError?: (error: Error) => void
}

export function useAutoSave(options: AutoSaveOptions) {
  const queryClient = useQueryClient()
  const lastSavedRef = useRef<Date>()
  const localStorageKey = 'draft_backup'
  
  const mutation = useMutation({
    mutationFn: options.mutationFn,
    retry: 3, // Retry failed saves 3 times
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
    networkMode: 'offlineFirst', // Queue when offline
    
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: ['timeEntry', variables.id] })
      const previousData = queryClient.getQueryData(['timeEntry', variables.id])
      
      // Backup to localStorage
      localStorage.setItem(localStorageKey, JSON.stringify({
        data: variables,
        timestamp: new Date().toISOString()
      }))
      
      // Optimistically update cache
      queryClient.setQueryData(['timeEntry', variables.id], variables)
      
      return { previousData }
    },
    
    onError: (error, variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(['timeEntry', variables.id], context.previousData)
      }
      options.onError?.(error)
    },
    
    onSuccess: (data) => {
      lastSavedRef.current = new Date()
      localStorage.removeItem(localStorageKey)
      options.onSuccess?.(data)
    }
  })
  
  return {
    save: mutation.mutate,
    isPending: mutation.isPending,
    isError: mutation.isError,
    lastSaved: lastSavedRef.current,
    error: mutation.error
  }
}
```

**State Management Pattern**:
```typescript
// components/TimeEntryForm.tsx
import { useState, useEffect } from 'react'
import { useAutoSave } from '@/hooks/useAutoSave'
import { useDebounce } from '@/hooks/useDebounce'

export function TimeEntryForm({ entryId }: { entryId: string }) {
  const [formData, setFormData] = useState({})
  const debouncedData = useDebounce(formData, 2000)
  
  const { save, isPending, lastSaved, isError } = useAutoSave({
    mutationFn: async (data) => {
      const response = await fetch(`/api/entries/${entryId}`, {
        method: 'PUT',
        headers: { 
          'Content-Type': 'application/json',
          'If-Match': data.version // Optimistic locking
        },
        body: JSON.stringify(data)
      })
      
      if (response.status === 412) {
        throw new ConflictError('Entry was modified by another user')
      }
      
      return response.json()
    },
    onError: (error) => {
      if (error instanceof ConflictError) {
        showConflictDialog()
      }
    }
  })
  
  // Auto-save on interval
  useEffect(() => {
    const interval = setInterval(() => {
      if (debouncedData) {
        save(debouncedData)
      }
    }, 60000)
    
    return () => clearInterval(interval)
  }, [debouncedData, save])
  
  return (
    <div>
      {isPending && <SaveIndicator status="saving" />}
      {lastSaved && <SaveIndicator status="saved" timestamp={lastSaved} />}
      {isError && <SaveIndicator status="error" />}
      {/* Form fields */}
    </div>
  )
}
```

**Conflict Resolution Strategy**:
- Server returns HTTP 412 Precondition Failed when version mismatch detected
- Client shows merge UI comparing local vs server versions
- User chooses: keep local, accept server, or manual merge
- Optimistic locking via ETag/If-Match headers or version field

**Error Handling and Retry Logic**:
- Automatic exponential backoff retry (3 attempts)
- Offline queue with automatic resume when online
- localStorage backup for crash recovery
- User notification on persistent failures after all retries exhausted

**Dependencies (package.json)**:
```json
{
  "dependencies": {
    "@tanstack/react-query": "^5.0.0"
  }
}
```

---

## 5. Database Encryption at Rest

### Decision
**Filesystem-level encryption (LUKS/dm-crypt) for production with plaintext for local development**

### Rationale
- Native PostgreSQL lacks built-in transparent data encryption (TDE) - pgcrypto only encrypts specific columns at the application layer, requiring significant code changes
- Filesystem-level encryption provides transparent encryption at rest with minimal performance overhead (typically 3-10%)
- Docker volume encryption via host filesystem encryption is production-ready and doesn't require PostgreSQL configuration changes
- Zero application code changes required - Spring Boot sees standard PostgreSQL connection
- Compatible with Docker Compose, backups, and standard operational procedures
- Cloud providers (AWS RDS, Azure Database, GCP CloudSQL) offer managed encryption by default for production

### Alternatives Considered

**PostgreSQL pgcrypto extension**:
- ❌ **Rejected for FR-033** (encryption at rest)
- Reason: Column-level encryption, not transparent. Requires application code changes to encrypt/decrypt each field, significant performance overhead (20-40%), breaks indexing on encrypted columns
- Use case: Only for selective field encryption (e.g., SSN, credit cards) beyond base encryption at rest

**Docker volume encryption plugins**:
- ❌ **Rejected for primary approach**
- Reason: Limited ecosystem, vendor lock-in, complex key management, not widely tested in production
- Use case: Potential future consideration if Docker-native solution matures

**Cloud provider managed encryption**:
- ✅ **RECOMMENDED for production deployment**
- Reason: AWS RDS, Azure Database for PostgreSQL, GCP CloudSQL provide transparent encryption at rest by default, integrated key management, automated backups with encryption, compliance certifications (FIPS 140-2)
- Use case: Production environments in cloud infrastructure

### Implementation Approach

**Local Development Setup (Docker Compose)**:
- No encryption required for local development
- Current `docker-compose.dev.yml` configuration is sufficient
- Use named volumes as currently configured (`postgres-data`)
- Rationale: Development data is non-sensitive, encryption adds unnecessary complexity

**Production Deployment Considerations**:

**Option A - Cloud Managed Databases (RECOMMENDED)**:
1. Use AWS RDS for PostgreSQL with encryption at rest enabled
2. Or Azure Database for PostgreSQL with encryption enabled
3. Or Google Cloud SQL for PostgreSQL with encryption enabled
4. All major cloud providers enable encryption by default
5. Key management handled by cloud provider KMS
6. Configuration: Single checkbox/parameter during provisioning
7. Spring Boot connection string remains unchanged

**Option B - Self-Hosted with LUKS (Linux)**:
```bash
# Create encrypted volume
cryptsetup luksFormat /dev/sdX
cryptsetup luksOpen /dev/sdX postgres_encrypted
mkfs.ext4 /dev/mapper/postgres_encrypted
mount /dev/mapper/postgres_encrypted /var/lib/postgresql/data
```

**Key Management Strategy**:
- Development: No encryption, no key management needed
- Production (Cloud): Use cloud provider KMS (AWS KMS, Azure Key Vault, GCP KMS)
- Production (Self-hosted): Use HashiCorp Vault or equivalent secrets management
- Never store keys in Docker images or environment variables
- Implement key rotation schedule (annually minimum)

**Performance Impact Assessment**:
- pgcrypto column encryption: 20-40% overhead for encrypted columns, breaks indexes
- Filesystem encryption (LUKS): 3-10% overhead, negligible for most workloads
- Cloud provider encryption: <5% overhead, transparent to application
- Docker volume plugins: Variable, 10-30% overhead depending on implementation
- **RECOMMENDATION**: Filesystem or cloud provider encryption have acceptable performance

---

## 6. Session Timeout Implementation

### Decision
**Combined Approach - Frontend Idle Detection + Backend Session Management**

### Rationale
- **SSO Compatibility**: Works seamlessly with both SAML and OAuth2 flows by maintaining application-level session control independent of IdP session management
- **True Idle Detection**: Frontend activity tracking (mouse, keyboard, scroll) ensures the timeout reflects actual user inactivity, not just "no API calls"
- **Superior User Experience**: Provides 2-minute warning before logout, allows user to continue working, and detects activity across multiple browser tabs using BroadcastChannel API
- **Minimal Server Overhead**: Session refresh only happens on user activity (throttled to maximum once per minute), not constant heartbeat polling
- **Security Best Practice**: Separates frontend timeout logic from backend session expiration (35-minute backend buffer vs 30-minute frontend timeout), preventing race conditions

### Alternatives Considered

**Spring Security session timeout + constant heartbeat**:
- ❌ **Rejected** - Creates unnecessary server load with constant polling
- With SSO, IdP session outliving app session causes user confusion

**JWT with sliding expiration (stateless)**:
- ❌ **Rejected** - SAML authentication returns session cookies, not JWTs
- Converting SAML assertions to JWT adds complexity without clear benefit

**OAuth2 token refresh pattern only**:
- ❌ **Rejected** - Only works for OAuth2, not SAML
- Requirement supports both SSO methods

### Implementation Approach

**Backend Configuration (application.yaml)**:
```yaml
server:
  servlet:
    session:
      timeout: 35m  # Buffer beyond frontend timeout
      cookie:
        http-only: true
        secure: true
        same-site: strict
```

**Session Refresh Endpoint**:
```kotlin
@RestController
@RequestMapping("/api/session")
class SessionController {
    
    @PostMapping("/refresh")
    fun refreshSession(request: HttpServletRequest): Map<String, Any> {
        val session = request.getSession(false)
        
        return if (session != null) {
            mapOf(
                "status" to "active",
                "lastAccessed" to session.lastAccessedTime,
                "maxInactiveInterval" to session.maxInactiveInterval
            )
        } else {
            mapOf("status" to "expired")
        }
    }
}
```

**Frontend Activity Detection (`useIdleTimeout` hook)**:
```typescript
// frontend/app/hooks/useIdleTimeout.ts
export function useIdleTimeout({
  timeoutMs,
  warningMs,
  onWarning,
  onTimeout,
  onActivity,
}: UseIdleTimeoutOptions) {
  // Detect user activity (mouse, keyboard, scroll, touch)
  // Throttle activity detection to once per minute
  // Broadcast activity to other tabs via BroadcastChannel
  // Show warning dialog at 28 minutes
  // Auto-logout at 30 minutes
}
```

**Session Manager Component**:
```typescript
// frontend/app/components/SessionManager.tsx
export function SessionManager({ children }: { children: React.ReactNode }) {
  const { isWarning, resetTimers } = useIdleTimeout({
    timeoutMs: 30 * 60 * 1000, // 30 minutes
    warningMs: 28 * 60 * 1000, // 28 minutes
    onWarning: handleWarning,
    onTimeout: handleLogout,
    onActivity: handleActivity, // Refresh backend session
  })
  
  // Show warning dialog with 2-minute countdown
  // Allow user to continue or logout
}
```

**Configuration Summary**:

| Component | Value | Purpose |
|-----------|-------|---------|
| Frontend Timeout | 30 minutes | User-facing idle timeout (FR-030) |
| Frontend Warning | 28 minutes | 2-minute warning before logout |
| Backend Session | 35 minutes | Buffer to prevent race conditions |
| Activity Throttle | 1 minute | Limit session refresh API calls |
| Cross-Tab Sync | BroadcastChannel | Synchronize timers across tabs |

---

## Best Practices

### Event Sourcing for Time Entry Domain

**Event Modeling**:
- **WorkLogEntryCreated**: Initial time entry creation with project, date, hours
- **WorkLogEntryUpdated**: Modification of hours or project (before approval)
- **WorkLogEntryDeleted**: Soft deletion (keep event history)
- **MonthSubmittedForApproval**: Entire month's entries submitted
- **MonthApproved**: Manager approval with timestamp and approver ID
- **MonthRejected**: Manager rejection with feedback reason

**Snapshot Strategy**:
- Create snapshots every 100 events for long-lived aggregates
- Snapshot contains: current state, version number, timestamp
- Rebuild from last snapshot + subsequent events
- 7-year retention (FR-028) requires archival strategy for old events

**Event Versioning**:
- Use event schema version field for backward compatibility
- Support multiple event versions during transitions
- Document event schema changes in migration guide

### Calendar UI Performance

**React 19 Patterns**:
- Use React 19 concurrent features for responsive rendering
- Implement `useTransition` for calendar month navigation
- Leverage Suspense boundaries for async data loading

**Virtualization Needs**:
- For 30-day calendar: No virtualization needed (small dataset)
- For yearly view (365 days): Consider `react-window` or `react-virtual`
- Optimize with `React.memo` for individual day cells

**State Management**:
- Use TanStack Query for server state (time entries, absences)
- Use React Context or Zustand for UI state (selected date, view mode)
- Implement optimistic updates for instant UI feedback

### Multi-Tenant Data Isolation

**Tenant Isolation Patterns**:
- Every aggregate root contains `tenantId` field
- Event store queries always filter by `tenantId`
- Projection tables include `tenantId` in primary/foreign keys
- Repository methods enforce tenant-scoped queries

**Query Performance**:
- Create composite indexes: `(tenant_id, date)`, `(tenant_id, user_id, date)`
- Partition large tables by tenant_id for horizontal scaling
- Use connection pooling with tenant-aware routing

**Index Strategy**:
```sql
-- Time entry projections
CREATE INDEX idx_time_entries_tenant_date ON time_entries(tenant_id, entry_date);
CREATE INDEX idx_time_entries_tenant_user ON time_entries(tenant_id, user_id);
CREATE INDEX idx_time_entries_tenant_project ON time_entries(tenant_id, project_id);

-- Approval workflow
CREATE INDEX idx_approvals_tenant_status ON approvals(tenant_id, status);
CREATE INDEX idx_approvals_tenant_approver ON approvals(tenant_id, approver_id);
```

### Responsive Design Patterns

**Mobile-First Calendar UI**:
- Stack layout for mobile (<768px): List view instead of grid
- Tablet layout (768-1024px): 7-column grid with reduced padding
- Desktop layout (>1024px): Full calendar grid with side panels

**Touch Interactions**:
- Swipe gestures for month navigation
- Long-press for context menus
- Large tap targets (min 44x44px) for touch-friendly UI
- Prevent double-tap zoom on form inputs

**Responsive Table Layouts**:
- Use CSS Grid for calendar layout: `grid-template-columns: repeat(7, 1fr)`
- Horizontal scroll for wide tables on mobile
- Card-based layout alternative for mobile devices
- Progressive disclosure: Show summary on mobile, details on desktop

---

## Summary

All technical unknowns from [plan.md](./plan.md) have been resolved through research. The decisions documented above provide:

1. **Clear technology choices** for all NEEDS CLARIFICATION items
2. **Rationale for each decision** based on requirements and constraints
3. **Rejected alternatives** with explanations
4. **Implementation guidance** with code samples and configuration

**Next Phase**: Proceed to Phase 1 (Design & Contracts) to create data models, API contracts, and quickstart documentation based on these research findings.

**Research Status**: ✅ **COMPLETE** - All 6 technical unknowns resolved, 4 best practices documented.
