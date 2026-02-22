---
name: gen-test
description: Generate test files following Miometry project patterns (Vitest for frontend, JUnit 5 for backend)
disable-model-invocation: true
---

# Generate Test File

Generate a test file for a given source file, following the project's established test patterns.

## Usage

`/gen-test <source-file-path>` or `/gen-test <component-or-class-name>`

## Detect Target

Determine frontend vs backend from the file path:
- `frontend/` → Vitest + React Testing Library
- `backend/` → JUnit 5 + Spring Boot Test (Kotlin)

---

## Frontend Test Pattern

### File Placement

Mirror the app structure under `frontend/tests/unit/`:
- `frontend/app/components/worklog/Calendar.tsx` → `frontend/tests/unit/components/worklog/Calendar.test.tsx`
- `frontend/app/hooks/useAutoSave.ts` → `frontend/tests/unit/hooks/useAutoSave.test.ts`
- `frontend/app/lib/validation/password.ts` → `frontend/tests/unit/lib/validation/password.test.ts`

### Template

Reference: `frontend/tests/unit/components/worklog/SubmitDailyButton.test.tsx`

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

// Module-level mocks (hoisted before imports)
vi.mock("relative/path/to/dependency", () => ({
  dependencyFunction: vi.fn(),
}));

// Import component AFTER mocks
import { ComponentName } from "relative/path/to/component";
import { dependencyFunction } from "relative/path/to/dependency";

const mockDependency = dependencyFunction as ReturnType<typeof vi.fn>;

describe("ComponentName", () => {
  const defaultProps = {
    // Default prop values for testing
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders correctly with default props", () => {
    render(<ComponentName {...defaultProps} />);
    expect(screen.getByRole("...")).toBeInTheDocument();
  });

  it("handles user interaction", async () => {
    render(<ComponentName {...defaultProps} />);
    // fireEvent or userEvent interactions
    await waitFor(() => {
      expect(mockDependency).toHaveBeenCalledWith(/* args */);
    });
  });
});
```

### Conventions

- **Mocks**: `vi.mock()` at module level (before imports), `vi.clearAllMocks()` in `beforeEach`
- **Queries**: Prefer `screen.getByRole()` → `screen.getByText()` → `screen.getByTestId()` (accessibility order)
- **Async**: Use `waitFor()` for async assertions, never raw `setTimeout`
- **Props**: Define `defaultProps` object, spread with overrides per test
- **API mocks**: Mock `../../../../app/services/api` module, cast to `ReturnType<typeof vi.fn>`
- **No explicit any**: Use proper types or `ReturnType<typeof vi.fn>`

### Run

```bash
cd frontend && npx vitest run tests/unit/path/to/Test.test.tsx
```

---

## Backend Test Pattern

### Unit Tests (Domain)

**File**: `backend/src/test/kotlin/com/worklog/domain/{aggregate}/{Aggregate}Test.kt`

```kotlin
package com.worklog.domain.{aggregate}

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class {Aggregate}Test {

    @Test
    fun `should create {aggregate} with valid parameters`() {
        // Arrange
        val id = UUID.randomUUID()

        // Act
        val aggregate = {Aggregate}.create(/* params */)

        // Assert
        assertNotNull(aggregate)
        assertEquals(/* expected */, aggregate./* property */)
    }

    @Test
    fun `should reject invalid input`() {
        assertThrows<IllegalArgumentException> {
            {Aggregate}.create(/* invalid params */)
        }
    }
}
```

### Integration Tests (API)

**File**: `backend/src/test/kotlin/com/worklog/api/{Controller}Test.kt`

Choose base class:
- `AdminIntegrationTestBase` — for `Admin*Controller` tests (auto-creates admin user)
- `IntegrationTestBase` — for regular user controller tests

```kotlin
package com.worklog.api

import com.worklog.testutil.AdminIntegrationTestBase  // or IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class {Controller}Test : AdminIntegrationTestBase() {

    @Test
    fun `GET endpoint returns expected data`() {
        // Arrange: create test data in transaction
        executeInNewTransaction {
            // Insert test data via repository or raw SQL
        }

        // Act & Assert
        mockMvc.perform(
            get("/api/v1/endpoint")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.field").value("expected"))
    }

    @Test
    fun `POST endpoint creates resource`() {
        val requestBody = """
            {
                "field": "value"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/endpoint")
                .with(user(adminEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
    }
}
```

### Conventions

- **Test naming**: Backtick-enclosed descriptive names (Kotlin)
- **Test data**: Use `executeInNewTransaction {}` for setup (ensures clean state)
- **Auth**: Use `.with(user(email))` from spring-security-test
- **JSON assertions**: `jsonPath()` for response body validation
- **Arrange-Act-Assert**: Follow AAA pattern consistently
- **No Testcontainers config needed**: Base classes handle PostgreSQL container lifecycle

### Run

```bash
# Single test class
cd backend && ./gradlew test --tests "com.worklog.api.{Controller}Test"

# Single test method
cd backend && ./gradlew test --tests "com.worklog.domain.{aggregate}.{Aggregate}Test.should create {aggregate} with valid parameters"
```
