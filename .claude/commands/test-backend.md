---
description: Run backend tests (all or filtered by class/method name)
---

## User Input

```text
$ARGUMENTS
```

## Outline

Run backend tests from the `backend/` directory using Gradle.

### Step 1: Parse arguments

Determine the test scope from `$ARGUMENTS`:

| Pattern | Example | Gradle command |
|---------|---------|----------------|
| Empty (no args) | `/test-backend` | `./gradlew test` |
| Contains `.` (FQCN or Class.method) | `com.worklog.api.HealthControllerTest` or `HealthControllerTest.healthEndpointReturnsOK` | `./gradlew test --tests "$ARGUMENTS"` |
| Simple name (no `.`) | `HealthControllerTest` | `./gradlew test --tests "com.worklog.*$ARGUMENTS*"` (wildcard match) |

### Step 2: Execute tests

Run the resolved Gradle command from the `backend/` directory.

- Use `--info` flag only when a specific test is targeted (not for full suite) to show individual test results.
- Let the command run to completion. Do NOT add a timeout — test suites can take several minutes.

### Step 3: Report results

Summarize the outcome:

- **Success**: Report total tests run and time taken.
- **Failure**: List each failing test with its class, method name, and a brief description of the failure reason (assertion message or exception).
- If no tests matched the filter, mention that explicitly and suggest checking the class/method name.
