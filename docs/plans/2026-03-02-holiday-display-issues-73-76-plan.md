# Holiday Display & Name_ja Issues (#73-#76) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add holiday name display to calendar, change holiday colors to pink, and add Japanese name field to admin form.

**Architecture:** Four related frontend changes: type update (#73), CSS color tokens (#74), Calendar component display logic (#75), and admin form enhancement (#76). Backend already supports `nameJa` — only frontend work needed for #76.

**Tech Stack:** TypeScript, React, Tailwind CSS, next-intl, Vitest

---

## Dependency Graph

```
#73 (type) ──┐
             ├──► #75 (Calendar.tsx)
#74 (CSS)  ──┘
#76 (admin form) — independent
```

#73 + #74 can be done first (parallel), then #75, then #76.

---

### Task 1: Add holidayName fields to DailyCalendarEntry type (#73)

**Files:**
- Modify: `frontend/app/types/worklog.ts:59-69`

**Step 1: Add fields to interface**

In `frontend/app/types/worklog.ts`, add two fields after `isHoliday: boolean` (line 65):

```typescript
export interface DailyCalendarEntry {
  date: string;
  totalWorkHours: number;
  totalAbsenceHours: number;
  status: DailyStatus;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  holidayNameJa: string | null;
  hasProxyEntries: boolean;
  rejectionSource: "monthly" | "daily" | null;
  rejectionReason: string | null;
}
```

**Step 2: Update all mock data in Calendar tests**

In `frontend/tests/unit/components/Calendar.test.tsx`, add `holidayName: null, holidayNameJa: null` to every `DailyCalendarEntry` in `mockDates` (lines 34-121) and all other test data arrays.

Exception: the holiday entry (date `2026-01-28`, line 111-121) should have:
```typescript
holidayName: "Test Holiday",
holidayNameJa: "テスト祝日",
```

**Step 3: Run tests**

Run: `npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: All tests pass (type-only change, no behavior change yet)

**Step 4: Commit**

```
feat(frontend): add holidayName/holidayNameJa to DailyCalendarEntry type

Closes #73
```

---

### Task 2: Change holiday color tokens to pink (#74)

**Files:**
- Modify: `frontend/app/globals.css:38-43`

**Step 1: Replace holiday color values**

Change the holiday color block from warm orange to pink:

```css
/* Holiday colors - pink for holidays and Sundays */
--color-holiday-50: oklch(0.97 0.03 350);
--color-holiday-100: oklch(0.93 0.06 350);
--color-holiday-200: oklch(0.88 0.10 350);
--color-holiday-500: oklch(0.65 0.20 350);
--color-holiday-600: oklch(0.55 0.22 350);
```

**Step 2: Run tests**

Run: `npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: All pass (CSS variables don't affect unit tests)

**Step 3: Commit**

```
style(frontend): change holiday color tokens from orange to pink

Closes #74
```

---

### Task 3: Update Calendar.tsx for Sunday/holiday pink & holiday name (#75)

**Files:**
- Modify: `frontend/app/components/worklog/Calendar.tsx`
- Modify: `frontend/tests/unit/components/Calendar.test.tsx`
- Modify: `frontend/messages/en.json` (add i18n keys if needed)
- Modify: `frontend/messages/ja.json` (add i18n keys if needed)

This is the largest task. Changes:

1. **Sunday detection helper** — `new Date(dateEntry.date).getDay() === 0`
2. **Background color** — holiday OR Sunday → `bg-holiday-100` (both mobile and desktop)
3. **Date number text color** — holiday/Sunday → `text-holiday-600`, Saturday → `text-blue-600`, weekday → `text-gray-900`
4. **Replace "H" badge with holiday name** — desktop: truncated text with title tooltip; mobile: same
5. **Weekday header colors** — Sunday → `text-holiday-600`, Saturday → `text-blue-600`
6. **Locale-based holiday name** — use `useLocale()` to pick `holidayNameJa` (ja) or `holidayName` (other)

#### Step 1: Write failing tests for new behavior

Add/update tests in `frontend/tests/unit/components/Calendar.test.tsx`:

```typescript
// In the "Weekend and Holiday Indicators" describe block:

it("should highlight Sunday dates with holiday background", () => {
  // 2026-01-25 is a Sunday
  const sundayDates: DailyCalendarEntry[] = [
    {
      date: "2026-01-25",
      totalWorkHours: 0,
      totalAbsenceHours: 0,
      status: "DRAFT",
      isWeekend: true,
      isHoliday: false,
      holidayName: null,
      holidayNameJa: null,
      hasProxyEntries: false,
      rejectionSource: null,
      rejectionReason: null,
    },
  ];

  const { container } = render(
    <IntlWrapper>
      <Calendar year={2026} month={1} dates={sundayDates} />
    </IntlWrapper>,
  );

  const buttons = screen.getAllByRole("button");
  const sundayButton = buttons.find((btn) => btn.textContent?.includes("25"));
  expect(sundayButton).toHaveClass("bg-holiday-100");
});

it("should display holiday name instead of H badge", () => {
  const holidayDates: DailyCalendarEntry[] = [
    {
      date: "2026-01-28",
      totalWorkHours: 0,
      totalAbsenceHours: 0,
      status: "DRAFT",
      isWeekend: false,
      isHoliday: true,
      holidayName: "Coming of Age Day",
      holidayNameJa: "成人の日",
      hasProxyEntries: false,
      rejectionSource: null,
      rejectionReason: null,
    },
  ];

  render(
    <IntlWrapper>
      <Calendar year={2026} month={1} dates={holidayDates} />
    </IntlWrapper>,
  );

  // "H" badge should NOT be present
  expect(screen.queryByText("H")).not.toBeInTheDocument();
  // Holiday name should be displayed (en locale in test)
  expect(screen.getByText("Coming of Age Day")).toBeInTheDocument();
});

it("should color Sunday day number with holiday color", () => {
  // 2026-01-25 is a Sunday
  const sundayDates: DailyCalendarEntry[] = [
    {
      date: "2026-01-25",
      totalWorkHours: 0,
      totalAbsenceHours: 0,
      status: "DRAFT",
      isWeekend: true,
      isHoliday: false,
      holidayName: null,
      holidayNameJa: null,
      hasProxyEntries: false,
      rejectionSource: null,
      rejectionReason: null,
    },
  ];

  render(
    <IntlWrapper>
      <Calendar year={2026} month={1} dates={sundayDates} />
    </IntlWrapper>,
  );

  const dayNum = screen.getByText("25");
  expect(dayNum).toHaveClass("text-holiday-600");
});

it("should color Saturday day number with blue", () => {
  // 2026-01-24 is a Saturday
  render(
    <IntlWrapper>
      <Calendar year={2026} month={1} dates={mockDates} />
    </IntlWrapper>,
  );

  // Find the 24th day number span
  const dayNum = screen.getByText("24");
  expect(dayNum).toHaveClass("text-blue-600");
});
```

**Step 2: Run tests to verify they fail**

Run: `npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: New tests FAIL (Sunday bg uses `bg-weekend-100`, "H" badge still present, wrong text colors)

#### Step 3: Implement Calendar.tsx changes

In `frontend/app/components/worklog/Calendar.tsx`:

**3a: Add `useLocale` import**

```typescript
import { useFormatter, useLocale, useTranslations } from "next-intl";
```

**3b: Add locale variable inside component**

```typescript
const locale = useLocale();
```

**3c: Add Sunday helper + holiday name resolver in both mobile and desktop render paths**

For each dateEntry, compute:
```typescript
const isSunday = new Date(dateEntry.date).getDay() === 0;
const displayHolidayName = dateEntry.isHoliday
  ? (locale === "ja" ? dateEntry.holidayNameJa : dateEntry.holidayName) ?? dateEntry.holidayName
  : null;
```

**3d: Update background color logic (both mobile line ~136 and desktop line ~243)**

```typescript
const backgroundClass = (dateEntry.isHoliday || isSunday)
  ? "bg-holiday-100"
  : hasAbsenceHours
    ? "bg-blue-50"
    : dateEntry.isWeekend
      ? "bg-weekend-100"
      : "bg-white";
```

**3e: Update date number text color (both mobile line ~160 and desktop line ~266-268)**

Replace the existing ternary:
```typescript
// Before:
${dateEntry.isWeekend ? "text-gray-600" : "text-gray-900"}

// After:
${(dateEntry.isHoliday || isSunday) ? "text-holiday-600" : dateEntry.isWeekend ? "text-blue-600" : "text-gray-900"}
```

**3f: Replace "H" badge with holiday name (desktop line ~284)**

Replace:
```tsx
{dateEntry.isHoliday && <span className="text-xs text-holiday-600">H</span>}
```

With:
```tsx
{displayHolidayName && (
  <span className="text-xs text-holiday-600 truncate max-w-[80px]" title={displayHolidayName}>
    {displayHolidayName}
  </span>
)}
```

**3g: Replace "H" badge with holiday name (mobile line ~166)**

Replace:
```tsx
{dateEntry.isHoliday && <span className="text-xs text-holiday-600">H</span>}
```

With:
```tsx
{displayHolidayName && (
  <span className="text-xs text-holiday-600 truncate max-w-[100px]" title={displayHolidayName}>
    {displayHolidayName}
  </span>
)}
```

**3h: Update weekday headers (desktop line ~213)**

Replace:
```tsx
<div key={day} className="bg-gray-50 px-2 py-2 text-center text-sm font-medium text-gray-700">
```

With:
```tsx
<div key={day} className={`bg-gray-50 px-2 py-2 text-center text-sm font-medium ${
  idx === 0 ? "text-holiday-600" : idx === 6 ? "text-blue-600" : "text-gray-700"
}`}>
```

Note: need to add `idx` to the `.map()` callback:
```tsx
{DAY_NAMES.map((day, idx) => (
```

#### Step 4: Update existing test expectations

The existing test at line 370 (`expect(screen.getByText("H"))`) must be updated since "H" badge is replaced by holiday name text. Update the mock holiday entry to include `holidayName: "Test Holiday"` and change the assertion to check for "Test Holiday" instead of "H".

#### Step 5: Run tests to verify they pass

Run: `npm test -- --run tests/unit/components/Calendar.test.tsx`
Expected: All tests PASS

#### Step 6: Commit

```
feat(frontend): add Sunday/holiday pink styling and holiday name display

- Sunday dates now use holiday background (bg-holiday-100) instead of weekend blue
- Date numbers colored: holiday/Sunday=pink, Saturday=blue, weekday=gray
- "H" badge replaced with actual holiday name (truncated with tooltip)
- Weekday headers: Sunday=pink, Saturday=blue
- Locale-aware: uses holidayNameJa for ja locale, holidayName otherwise

Closes #75
```

---

### Task 4: Add name_ja field to HolidayEntryForm (#76)

**Files:**
- Modify: `frontend/app/components/admin/HolidayEntryForm.tsx`
- Modify: `frontend/app/components/admin/HolidayCalendarPresetList.tsx`
- Modify: `frontend/messages/en.json`
- Modify: `frontend/messages/ja.json`

Note: Backend already has `nameJa` in `CreateHolidayEntryRequest` and `HolidayEntryRow`. Only frontend changes needed.

#### Step 1: Add i18n keys

In `frontend/messages/en.json`, under `admin.masterData.holidayCalendar`, add:
```json
"entryNameJa": "Holiday Name (Japanese)"
```

In `frontend/messages/ja.json`, under `admin.masterData.holidayCalendar`, add:
```json
"entryNameJa": "祝日名（日本語）"
```

#### Step 2: Add nameJa state and form field to HolidayEntryForm.tsx

**2a: Add state** (after line 58):
```typescript
const [nameJa, setNameJa] = useState(entry?.nameJa ?? "");
```

**2b: Add `nameJa` to payload** (line 80, add after `name`):
```typescript
const data = {
  name,
  nameJa: nameJa.trim() || null,
  entryType,
  month,
  ...(entryType === "FIXED" ? { day } : { nthOccurrence, dayOfWeek }),
  ...(specificYear ? { specificYear: Number(specificYear) } : {}),
};
```

**2c: Add input field** (after the name field, after line 132):
```tsx
<div>
  <label htmlFor="he-name-ja" className="block text-sm font-medium text-gray-700 mb-1">
    {t("holidayCalendar.entryNameJa")}
  </label>
  <input
    id="he-name-ja"
    type="text"
    value={nameJa}
    onChange={(e) => setNameJa(e.target.value)}
    className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  />
</div>
```

#### Step 3: Add nameJa display to HolidayCalendarPresetList.tsx

In the entries table (desktop, line 189), show nameJa in parentheses after name:
```tsx
<td className="py-2 px-3">
  {entry.name}
  {entry.nameJa && <span className="text-gray-500 ml-1">({entry.nameJa})</span>}
</td>
```

In the mobile entries (line 250), same pattern:
```tsx
<span className="text-sm font-medium text-gray-900">
  {entry.name}
  {entry.nameJa && <span className="text-gray-500 ml-1 text-xs">({entry.nameJa})</span>}
</span>
```

#### Step 4: Run tests

Run: `npm test -- --run`
Expected: All tests pass

#### Step 5: Commit

```
feat(frontend): add Japanese name field to HolidayEntryForm

- Add nameJa text input to holiday entry create/edit form
- Display nameJa in parentheses in entry list (desktop + mobile)
- Add i18n keys for entryNameJa label

Closes #76
```

---

## Final Verification

After all tasks:
1. Run full test suite: `npm test -- --run`
2. Run type check: `npx tsc --noEmit`
3. Run lint: `npx biome ci .`
4. Verify no regressions
