# Implementation Plan: Daily Approval Layout Improvement

**Branch**: `017-daily-approval-layout` | **Date**: 2026-02-21 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-daily-approval-layout/spec.md`

## Summary

Refactor the daily approval dashboard UI to improve visual hierarchy, add summary statistics, format dates in Japanese style with day-of-week, add per-member hour subtotals, and apply consistent styling to status badges and action buttons. This is a frontend-only change with no backend modifications.

## Technical Context

**Language/Version**: TypeScript 5.x
**Primary Dependencies**: Next.js 16.x, React 19.x, Tailwind CSS v4
**Storage**: N/A (no backend changes)
**Testing**: Vitest + React Testing Library
**Target Platform**: Web (desktop 1280px+, responsive down to 1024px)
**Project Type**: Web application (frontend only)
**Performance Goals**: No regression in render time; page loads within existing performance envelope
**Constraints**: Frontend-only; existing API response structure (DailyGroup > MemberEntryGroup > EntryRow) unchanged
**Scale/Scope**: 1 page component + 1 dashboard component refactor, ~305 lines current

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | PASS | Biome lint/format enforced; component refactored for readability |
| II. Testing Discipline | PASS | Unit tests will be added for the new component (currently none exist) |
| III. Consistent UX | PASS | Uses existing Tailwind design tokens; matches admin panel patterns |
| IV. Performance | PASS | No new data fetching; client-side date formatting only |

No violations. No complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/017-daily-approval-layout/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
frontend/
├── app/
│   ├── worklog/daily-approval/
│   │   └── page.tsx                          # Page wrapper (minor update)
│   ├── components/admin/
│   │   └── DailyApprovalDashboard.tsx        # Main component (major refactor)
│   └── lib/
│       └── date-format.ts                    # Date formatting utility (new)
└── tests/
    └── unit/
        ├── components/admin/
        │   └── DailyApprovalDashboard.test.tsx  # Component tests (new)
        └── lib/
            └── date-format.test.ts              # Date utility tests (new)
```

**Structure Decision**: Minimal footprint — refactor the existing single component, extract a reusable date formatting utility, and add corresponding tests.

## Component Design

### DailyApprovalDashboard Refactored Layout

```
┌─────────────────────────────────────────────────────────┐
│  Filter Bar                                              │
│  [開始日: ____] [終了日: ____]     [選択した N 件を承認]  │
├─────────────────────────────────────────────────────────┤
│  Summary Cards                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ 未承認    │  │ 承認済    │  │ 差戻     │              │
│  │   12     │  │    5     │  │    2     │              │
│  └──────────┘  └──────────┘  └──────────┘              │
├─────────────────────────────────────────────────────────┤
│  2026年2月21日(金)                                       │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 田中太郎                                           │  │
│  │ ☐ │ PRJ001 プロジェクトA │ 4.0h │ 作業内容... │ 未承認 │ 差戻 │
│  │ ☐ │ PRJ002 プロジェクトB │ 3.5h │ テスト...   │ 未承認 │ 差戻 │
│  │                                    合計: 7.5h     │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 佐藤花子                                           │  │
│  │   │ PRJ001 プロジェクトA │ 8.0h │ 開発完了   │ 承認済 │ 取消 │
│  │                                    合計: 8.0h     │  │
│  └───────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│  2026年2月20日(木)                                       │
│  ...                                                     │
└─────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Date formatting**: Use `Intl.DateTimeFormat` with `ja-JP` locale for `YYYY年M月D日(曜)` format. Extract to `date-format.ts` utility for reuse.
2. **Summary cards**: Computed client-side from loaded groups data. No additional API call needed.
3. **Hour subtotals**: Computed inline per member section by summing entry hours.
4. **Comment truncation**: CSS `truncate` class with `title` attribute for hover tooltip. Max width set via Tailwind.
5. **Responsive**: `overflow-x-auto` wrapper for table on narrow viewports.
6. **No component splitting**: Keep as single component since it's ~305 lines and the refactor is primarily styling. Only extract the date formatter as a utility.
