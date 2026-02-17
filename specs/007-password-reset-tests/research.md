# Research: Password Reset Integration & E2E Tests

**Branch**: `007-password-reset-tests` | **Date**: 2026-02-17

## R1: Repository Integration Test Pattern

**Decision**: Follow the `JdbcUserRepositoryTest` pattern — `@SpringBootTest` + `@Testcontainers` + `@Transactional` with a standalone `PostgreSQLContainer`.

**Rationale**: This is the established pattern in the codebase for repository-level integration tests. Using `@Transactional` ensures automatic rollback after each test, providing clean isolation without manual cleanup. The standalone container approach (vs. extending IntegrationTestBase) avoids pulling in Redis and MailHog containers that are unnecessary for pure repository tests.

**Alternatives considered**:
- `IntegrationTestBase` — rejected because it starts Redis + MailHog containers unnecessarily for repository-only tests
- `@DataJdbcTest` — rejected because the project uses raw `JdbcTemplate` (not Spring Data JDBC repositories), and `@DataJdbcTest` auto-configuration may not wire `PasswordResetTokenRepository` correctly since it's a plain `@Repository` class, not a Spring Data interface

## R2: CSRF Testing Strategy

**Decision**: Create a dedicated CSRF integration test class using `@WebMvcTest(AuthController.class)` with a custom `@TestConfiguration` that provides a `SecurityFilterChain` bean with CSRF enabled.

**Rationale**: The application disables CSRF in `test`/`dev` profiles via `SecurityConfig.devFilterChain`. The prod filter chain enables CSRF but activating the `prod` profile in tests would bring in other production configurations. A custom test-only SecurityFilterChain is the most isolated approach — it enables CSRF without requiring profile switching or the full application context.

**Alternatives considered**:
- `@ActiveProfiles("prod")` with IntegrationTestBase — rejected because prod profile may activate unwanted configurations (CORS, session limits, etc.)
- Modifying SecurityConfig to add a `test-csrf` profile — rejected because it modifies production code for test purposes
- Testing CSRF via existing AuthControllerTest — rejected because it explicitly excludes SecurityAutoConfiguration

## R3: Controller Validation Test Approach

**Decision**: Add validation-specific test methods to the existing `AuthControllerTest` class, leveraging the existing `@WebMvcTest` setup and mock configurations.

**Rationale**: The existing AuthControllerTest already has the `@WebMvcTest(AuthController.class)` setup with mocked `PasswordResetService`. Validation errors from `@Valid` + Jakarta annotations (`@NotBlank`, `@Email`, `@Size`) are handled at the controller layer before reaching the service, so they don't require service mocks to throw. Adding tests for empty/invalid input directly exercises the validation framework. The existing test excludes SecurityAutoConfiguration which means CSRF won't interfere, allowing us to focus purely on validation logic.

**Alternatives considered**:
- New dedicated test class — rejected because the setup would be identical to AuthControllerTest
- IntegrationTestBase with full stack — rejected because over-engineering for input validation testing

## R4: E2E Test Architecture

**Decision**: Create a new `PasswordResetE2ETest` class extending `IntegrationTestBase`, using `WebTestClient` for API calls and direct `JdbcTemplate` for token extraction and timestamp manipulation.

**Rationale**: IntegrationTestBase provides PostgreSQL + Redis + MailHog containers with the full Spring Boot context. The E2E test needs to: (1) create a user, (2) call the reset request endpoint, (3) extract the token from the database, (4) call the confirm endpoint, (5) verify login works with new password. Direct database access is needed for token extraction (since email delivery goes to MailHog, not a real inbox) and for manipulating timestamps to test expiration scenarios.

**Alternatives considered**:
- Extracting token from MailHog API — rejected because it adds complexity; direct DB access is simpler and the email delivery is already tested in EmailServiceTest
- Using MockMvc instead of WebTestClient — rejected because IntegrationTestBase uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` which pairs with WebTestClient

## R5: Token Expiration Testing

**Decision**: Manipulate the `expires_at` column directly via `JdbcTemplate` SQL update to set tokens as expired, then call the confirm endpoint.

**Rationale**: The `PasswordResetToken.create()` factory method uses `Instant.now()` internally, making it impossible to inject a custom clock. Directly updating the database timestamp is a pragmatic and reliable approach used in the existing codebase (see `JdbcUserRepositoryTest.findExpiredLockedUsers` which drops a CHECK constraint and updates timestamps via SQL).

**Alternatives considered**:
- Injecting a `Clock` abstraction — rejected because it requires modifying production domain code for test purposes
- Waiting for real time to pass — rejected because 24-hour expiry would make tests impractical
- Reflection to modify private fields — rejected because fragile and couples tests to internal implementation

## R6: Session Invalidation Verification

**Decision**: Test session invalidation by: (1) logging in to create a session, (2) performing password reset, (3) verifying that the old session's authentication is no longer valid. Use `WebTestClient` with session cookies to verify the session lifecycle.

**Rationale**: The `PasswordResetService.confirmReset()` calls `userSessionRepository.deleteByUserId()` to invalidate all sessions. The E2E test should verify this behavior by attempting to use the old session after password reset.

**Alternatives considered**:
- Checking the database directly for session records — could be added as supplementary verification alongside API-level check
