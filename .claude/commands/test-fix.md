---
description: Run all backend and frontend tests, then auto-fix any failures
---

## User Input

```text
$ARGUMENTS
```

## Outline

Run all tests for both backend and frontend, and automatically fix any failing tests.

### Step 1: Run backend tests

Run `./gradlew test` from the `backend/` directory. Capture the full output including failure details.

### Step 2: Run frontend tests

Run `npm test -- --run` from the `frontend/` directory. Capture the full output including failure details.

### Step 3: Analyze results

Summarize the results from both test suites in a table:

| Suite | Total | Passed | Failed |
|-------|-------|--------|--------|

- If all tests pass, report success and stop here.
- If any tests fail, proceed to Step 4.

### Step 4: Fix failing tests

For each failing test:

1. Read the failing test file and the source code it tests.
2. Determine whether the failure is caused by:
   - **Test bug**: The test itself is wrong (outdated assertion, wrong mock setup, etc.) — fix the test.
   - **Source bug**: The production code has a bug — fix the source code.
   - **Integration issue**: Missing seed data, configuration, or dependency — fix the root cause.
3. Apply the fix.

### Step 5: Re-run tests to verify

After all fixes are applied:

1. Re-run only the previously failing tests to confirm the fix.
2. Then run the full test suite for the affected side (backend/frontend) to ensure no regressions.
3. Report the final results.

If new failures appear after fixes, repeat from Step 4 (up to 3 iterations max). If failures persist after 3 iterations, stop and report the remaining issues for manual review.
