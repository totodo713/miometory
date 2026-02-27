---
description: Plan files multi-agent review (CPO + Security + UX)
---

## User Input

```text
$ARGUMENTS
```

## Outline

Invoke the `review-plan` skill to perform multi-agent review on plan files.

### Step 1: Determine review target

From `$ARGUMENTS`:

| Pattern | Example | Action |
|---------|---------|--------|
| File path specified | `/review-plan docs/plan/feature-x.md` | Review that specific file |
| No arguments | `/review-plan` | Review the most recently modified file in `docs/plan/`, or plan files in current context |

### Step 2: Execute review

Invoke the `review-plan` skill with the determined target file path as argument.

### Step 3: Report results

Display the synthesized review results:
- List each reviewer's verdict (APPROVED / REJECTED)
- If any REJECTED, list the specific issues
- State whether implementation may proceed
