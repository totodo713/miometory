---
description: Run frontend tests (all, filtered by file/pattern, or E2E)
---

## User Input

```text
$ARGUMENTS
```

## Outline

Run frontend tests from the `frontend/` directory using Vitest.

### Step 1: Parse arguments

Determine the test scope from `$ARGUMENTS`:

| Pattern | Example | Command |
|---------|---------|---------|
| Empty (no args) | `/test-frontend` | `npm test -- --run` (all unit tests) |
| `e2e` or `--e2e` | `/test-frontend e2e` | `npm run test:e2e` |
| File path or pattern | `/test-frontend useAutoSave` | `npm test -- --run $ARGUMENTS` (Vitest pattern filter) |

### Step 2: Execute tests

Run the resolved command from the `frontend/` directory.

- Let the command run to completion.

### Step 3: Report results

Summarize the outcome:

- **Success**: Report total tests run, passed count, and time taken.
- **Failure**: List each failing test with its file, test name, and a brief description of the failure reason (assertion message or exception).
- If no tests matched the filter, mention that explicitly and suggest checking the file/pattern name.
