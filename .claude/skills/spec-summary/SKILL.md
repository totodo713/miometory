---
name: spec-summary
description: Summarize a spec directory to understand past feature decisions, data models, and implementation scope
---

# Spec Summary

Summarize one or more feature specification directories in `specs/` to quickly understand past design decisions and their relevance to current work.

## Usage

`/spec-summary <spec-dir>` — Summarize a specific spec (e.g., `/spec-summary 015-admin-management`)
`/spec-summary <keyword>` — Search and summarize specs matching a keyword (e.g., `/spec-summary auth`)
`/spec-summary all` — List all specs with one-line summaries
`/spec-summary recent [N]` — Summarize the N most recent specs (default: 3)

## Spec Directory Structure

Each spec in `specs/` follows a standard structure:

| File | Content |
|------|---------|
| `spec.md` | Feature requirements, user stories, acceptance criteria |
| `plan.md` | Implementation plan, architectural decisions |
| `tasks.md` | Task breakdown with dependencies |
| `data-model.md` | Entity/table definitions, relationships |
| `research.md` | Technical research, library evaluations |
| `quickstart.md` | Getting started guide for the feature |
| `contracts/` | API contracts, request/response schemas |
| `checklists/` | Review and testing checklists |

Not all files exist in every spec directory.

## Workflow

### For a specific spec (`/spec-summary <spec-dir>`)

1. **Read `spec.md`** first — extract:
   - Feature name and purpose (first heading + intro paragraph)
   - Key user stories or requirements (bullet points)
   - Scope boundaries (what's included and excluded)

2. **Read `data-model.md`** if it exists — extract:
   - Entity names and key fields
   - Relationships between entities
   - Database tables introduced

3. **Read `plan.md`** if it exists — extract:
   - Architectural approach
   - Key design decisions and rationale
   - Technology choices

4. **Scan `tasks.md`** if it exists — extract:
   - Total task count and completion status
   - High-level task categories

5. **List `contracts/`** if it exists — note API endpoints defined

### For keyword search (`/spec-summary <keyword>`)

```bash
# Search across all spec.md files for the keyword
grep -ril "<keyword>" specs/*/spec.md specs/*/plan.md
```

Summarize each matching spec with the single-spec workflow above.

### For all specs (`/spec-summary all`)

List each spec directory with a one-line summary extracted from the first paragraph of `spec.md`:

```
| # | Spec | Summary |
|---|------|---------|
| 001 | foundation | Project foundation setup... |
| 002 | work-log-entry | Work log entry CRUD... |
| ... | ... | ... |
```

### For recent specs (`/spec-summary recent [N]`)

Sort spec directories by number (highest = most recent), take the top N, and summarize each.

## Output Format

### Single Spec Summary

```
## [NNN] Feature Name

**Purpose**: One-sentence description of what this feature does.

**Key Requirements**:
- Requirement 1
- Requirement 2
- Requirement 3

**Data Model**: Entity1, Entity2 (with key relationships)

**Architecture**: Brief description of the approach taken

**API Endpoints** (if contracts exist):
- `GET /api/v1/resource` — description
- `POST /api/v1/resource` — description

**Status**: N tasks total, implementation approach noted

**Relevance**: How this spec relates to or depends on other specs
```

### All Specs Summary

```
## Specs Overview (N total)

| # | Feature | Purpose | Key Entities |
|---|---------|---------|-------------|
| 001 | Foundation | ... | ... |
| ... | ... | ... | ... |
```

## Guidelines

- Keep summaries concise — the goal is quick orientation, not full documentation
- Highlight design decisions that affect other features (e.g., shared tables, auth patterns)
- Note dependencies between specs when apparent
- When the user is working on a new feature, suggest related specs they should review
