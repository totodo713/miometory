# Response DTO JSON フィールド名の標準化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Response DTO の JSON フィールド名を統一する（`dates`→`entries`, `total`/`count`→`totalCount`）

**Architecture:** Java record のコンポーネント名変更 → コンパイラ駆動で全参照修正 → TypeScript 型定義変更 → tsc 駆動で全参照修正。モノレポなので Backend/Frontend を同一 PR で更新。

**Tech Stack:** Java 21 records, Kotlin tests, TypeScript/Next.js, Playwright E2E, Biome linter, Spotless formatter

**Closes:** #127

---

## Task 1: Backend DTO records のフィールド名変更

**Files:**
- Modify: `backend/src/main/java/com/worklog/api/dto/MonthlyCalendarResponse.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/WorkLogEntriesResponse.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/AbsencesResponse.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/SubordinatesResponse.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/AssignedProjectsResponse.java`
- Modify: `backend/src/main/java/com/worklog/api/dto/PreviousMonthProjectsResponse.java`

**Step 1: MonthlyCalendarResponse — `dates` → `entries`**

```java
// MonthlyCalendarResponse.java — record component を変更
// Before:
List<DailyCalendarEntry> dates,
// After:
List<DailyCalendarEntry> entries,
```

同ファイル内の backward-compatible constructor があれば、そちらのパラメータ名・呼び出しも `entries` に更新する。

**Step 2: WorkLogEntriesResponse — `total` → `totalCount`**

```java
// Before:
public record WorkLogEntriesResponse(List<WorkLogEntryResponse> entries, int total) {}
// After:
public record WorkLogEntriesResponse(List<WorkLogEntryResponse> entries, int totalCount) {}
```

**Step 3: AbsencesResponse — `total` → `totalCount`**

```java
// Before:
public record AbsencesResponse(List<AbsenceResponse> absences, int total) {}
// After:
public record AbsencesResponse(List<AbsenceResponse> absences, int totalCount) {}
```

**Step 4: SubordinatesResponse — `count` → `totalCount`**

```java
// Before:
public record SubordinatesResponse(List<MemberResponse> subordinates, int count, boolean includesIndirect) {}
// After:
public record SubordinatesResponse(List<MemberResponse> subordinates, int totalCount, boolean includesIndirect) {}
```

**Step 5: AssignedProjectsResponse — `count` → `totalCount`**

```java
// Before:
public record AssignedProjectsResponse(List<AssignedProject> projects, int count) {}
// After:
public record AssignedProjectsResponse(List<AssignedProject> projects, int totalCount) {}
```

**Step 6: PreviousMonthProjectsResponse — `count` → `totalCount`**

```java
// Before:
public record PreviousMonthProjectsResponse(
        List<UUID> projectIds, LocalDate previousMonthStart, LocalDate previousMonthEnd, int count) {}
// After:
public record PreviousMonthProjectsResponse(
        List<UUID> projectIds, LocalDate previousMonthStart, LocalDate previousMonthEnd, int totalCount) {}
```

`@param count` の Javadoc があれば `@param totalCount` に更新。

---

## Task 2: Backend コンパイルエラーの修正（コンパイラ駆動）

**Step 1: コンパイルしてエラー箇所を特定**

Run: `cd backend && ./gradlew compileJava compileKotlin 2>&1 | head -80`
Expected: アクセサ名変更によるコンパイルエラー（`.dates()` → `.entries()`, `.total()` → `.totalCount()`, `.count()` → `.totalCount()`）

**Step 2: 全コンパイルエラーを修正**

Java record のアクセサ名が変わるため、構築箇所・参照箇所すべてを修正する。パターン:

| 旧アクセサ | 新アクセサ | 影響ファイル例 |
|-----------|-----------|---------------|
| `.dates()` | `.entries()` | Controller/Service で MonthlyCalendarResponse を構築する箇所 |
| `.total()` | `.totalCount()` | WorkLogEntriesResponse, AbsencesResponse の構築箇所 |
| `.count()` | `.totalCount()` | SubordinatesResponse, AssignedProjectsResponse, PreviousMonthProjectsResponse の構築箇所 |

**Note:** record の構築は `new XxxResponse(arg1, arg2, ...)` の位置引数なのでコンストラクタ呼び出し自体は壊れない。壊れるのはアクセサ参照（`.count()` 等）のみ。

**Step 3: 再コンパイルして全エラー解消を確認**

Run: `cd backend && ./gradlew compileJava compileKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 3: Backend テストの JSON フィールド名修正

**Files:**
- Modify: `backend/src/test/kotlin/com/worklog/api/CalendarControllerTest.kt`
- Modify: `backend/src/test/java/com/worklog/api/WorkLogControllerCalendarRejectionTest.java`
- Modify: `backend/src/test/kotlin/com/worklog/api/AbsenceControllerTest.kt`
- Modify: `backend/src/test/kotlin/com/worklog/api/ProjectControllerTest.kt`

**Step 1: CalendarControllerTest.kt — `body["dates"]` → `body["entries"]`**

```kotlin
// Line 188:
// Before: val entries = body["dates"] as List<Map<String, Any?>>
// After:  val entries = body["entries"] as List<Map<String, Any?>>

// Line 211:
// Before: val dates = body["dates"] as List<Map<String, Any?>>
// After:  val dates = body["entries"] as List<Map<String, Any?>>
```

**Step 2: WorkLogControllerCalendarRejectionTest.java — `body.get("dates")` → `body.get("entries")`**

```java
// Line 165:
// Before: List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("dates");
// After:  List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("entries");

// Line 218:
// Before: List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("dates");
// After:  List<Map<String, Object>> dates = (List<Map<String, Object>>) body.get("entries");
```

**Step 3: AbsenceControllerTest.kt — `body["total"]` → `body["totalCount"]`**

```kotlin
// Line 751:
// Before: assertEquals(0, body["total"])
// After:  assertEquals(0, body["totalCount"])
```

**Step 4: ProjectControllerTest.kt — `body["count"]` → `body["totalCount"]` (4箇所)**

```kotlin
// Line 42:  assertEquals(0, body["count"])  → assertEquals(0, body["totalCount"])
// Line 75:  assertEquals(2, body["count"])  → assertEquals(2, body["totalCount"])
// Line 131: assertEquals(0, body["count"])  → assertEquals(0, body["totalCount"])
// Line 211: assertEquals(1, body["count"])  → assertEquals(1, body["totalCount"])
```

**Step 5: Backend 全テスト実行**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 全テスト PASS

**Step 6: Spotless フォーマット適用**

Run: `cd backend && ./gradlew spotlessApply`

**Step 7: コミット**

```bash
git add backend/src/main/java/com/worklog/api/dto/ backend/src/test/
git commit -m "refactor(backend): standardize response DTO field names

- MonthlyCalendarResponse: dates → entries
- WorkLogEntriesResponse, AbsencesResponse: total → totalCount
- SubordinatesResponse, AssignedProjectsResponse, PreviousMonthProjectsResponse: count → totalCount

Closes #127 (backend portion)"
```

---

## Task 4: Frontend 型定義の更新

**Files:**
- Modify: `frontend/app/types/worklog.ts`

**Step 1: MonthlyCalendarResponse — `dates` → `entries`**

```typescript
// worklog.ts (around line 83):
// Before: dates: DailyCalendarEntry[];
// After:  entries: DailyCalendarEntry[];
```

**Step 2: WorkLogEntriesResponse — `total` → `totalCount`**

```typescript
// worklog.ts (around line 53):
// Before: total: number;
// After:  totalCount: number;
```

**Step 3: AssignedProjectsResponse — `count` → `totalCount`**

```typescript
// worklog.ts (around line 123):
// Before: count: number;
// After:  totalCount: number;
```

---

## Task 5: Frontend API サービス層の更新

**Files:**
- Modify: `frontend/app/services/api.ts`

**Step 1: `total:` → `totalCount:` (2箇所)**

```typescript
// WorkLogEntries inline type (around line 392):
// Before: total: number;
// After:  totalCount: number;

// Absences inline type (around line 634):
// Before: total: number;
// After:  totalCount: number;
```

**Step 2: `count:` → `totalCount:` (3箇所)**

```typescript
// PreviousMonthProjects inline type (around line 474):
// Before: count: number;
// After:  totalCount: number;

// Subordinates inline type (around line 869):
// Before: count: number;
// After:  totalCount: number;

// AssignedProjects inline type (around line 894):
// Before: count: number;
// After:  totalCount: number;
```

**Note:** `api.ts` の行番号は PR #132 マージにより ±10 程度ズレている可能性あり。フィールド名で grep して特定すること。`total` / `count` は汎用語なので、Response 型の context 内のみ変更し、`totalWorkHours` 等の別フィールドは触らないこと。

---

## Task 6: Frontend コンポーネントの更新

**Files:**
- Modify: `frontend/app/components/worklog/Calendar.tsx`
- Modify: `frontend/app/worklog/page.tsx`
- Modify: `frontend/app/components/worklog/CopyPreviousMonthDialog.tsx`

**Step 1: Calendar.tsx — props `dates` → `entries`**

```typescript
// Calendar.tsx (line 26):
// Before: dates: DailyCalendarEntry[];
// After:  entries: DailyCalendarEntry[];
```

コンポーネント内部の `dates` 参照もすべて `entries` に変更する。`props.dates` → `props.entries` や destructured `{ dates }` → `{ entries }` を確認。

**Step 2: worklog/page.tsx — `calendarData.dates` → `calendarData.entries`**

```typescript
// page.tsx (around line 255):
// Before: <Calendar ... dates={calendarData.dates} />
// After:  <Calendar ... entries={calendarData.entries} />
```

**Step 3: CopyPreviousMonthDialog.tsx — `count` → `totalCount`**

```typescript
// Interface (line 29):
// Before: count: number;
// After:  totalCount: number;

// Usage (line 207):
// Before: data.count === 0
// After:  data.totalCount === 0

// Usage (around line 253):
// Before: {data.count} projects selected
// After:  {data.totalCount} projects selected
```

**Step 4: TypeScript 型チェック**

Run: `cd frontend && npx tsc --noEmit`
Expected: エラーなし（型定義変更により漏れた参照があればここで検出される）

**Step 5: Biome リント**

Run: `cd frontend && npx biome check --write frontend/app/components/worklog/Calendar.tsx frontend/app/worklog/page.tsx frontend/app/components/worklog/CopyPreviousMonthDialog.tsx`

---

## Task 7: Frontend ユニットテストの更新

**Files:**
- Modify: `frontend/tests/unit/components/Calendar.test.tsx`

**Step 1: `dates={...}` → `entries={...}` (約36箇所)**

テストファイル内のすべての `dates=` prop を `entries=` に一括置換する。

```typescript
// Before (example):
<Calendar year={2026} month={1} dates={mockDates} />
// After:
<Calendar year={2026} month={1} entries={mockDates} />
```

**Step 2: テスト実行**

Run: `cd frontend && npx vitest run tests/unit/components/Calendar.test.tsx`
Expected: 全テスト PASS

---

## Task 8: Frontend E2E テストの API モック更新

**Files (8ファイル):**
- Modify: `frontend/tests/e2e/accessibility.spec.ts`
- Modify: `frontend/tests/e2e/absence-entry.spec.ts`
- Modify: `frontend/tests/e2e/approval-workflow.spec.ts`
- Modify: `frontend/tests/e2e/copy-previous-month.spec.ts`
- Modify: `frontend/tests/e2e/csv-operations.spec.ts`
- Modify: `frontend/tests/e2e/daily-entry.spec.ts`
- Modify: `frontend/tests/e2e/multi-project-entry.spec.ts`
- Modify: `frontend/tests/e2e/proxy-entry.spec.ts`

**Step 1: 全 E2E テストの `dates:` → `entries:` を一括置換**

各ファイルの calendar API モック内の `dates:` キーを `entries:` に変更する。

```typescript
// Before (mock response):
dates: Array.from({ length: 31 }, (_, i) => ({ ... })),
// After:
entries: Array.from({ length: 31 }, (_, i) => ({ ... })),
```

**Step 2: copy-previous-month.spec.ts の追加変更**

このファイルには `count:` と `total:` の参照もある:

```typescript
// Previous month projects mock (around line 104):
// Before: count: 2,
// After:  totalCount: 2,

// WorkLog entries mock (around line 72):
// Before: total: 0,
// After:  totalCount: 0,

// Absences mock (around line 88):
// Before: total: 0,
// After:  totalCount: 0,
```

**Step 3: 他の E2E テストで `total:` / `count:` を使っている箇所を grep して修正**

Run: `grep -rn '"total":' frontend/tests/e2e/ --include="*.spec.ts"`
Run: `grep -rn '"count":' frontend/tests/e2e/ --include="*.spec.ts"`
Run: `grep -rn 'total:' frontend/tests/e2e/ --include="*.spec.ts"`
Run: `grep -rn 'count:' frontend/tests/e2e/ --include="*.spec.ts"`

**注意:** `totalWorkHours`, `submittedCount`, `rejectedCount`, `recalledCount`, `totalElements` は変更しない（アクション固有 or Spring Page 規約）。変更するのは Response の直下にある `total` と `count` フィールドのみ。

---

## Task 9: Frontend 全テスト実行 + フォーマット

**Step 1: Biome フォーマット**

Run: `cd frontend && npx biome check --write .`

**Step 2: ユニットテスト**

Run: `cd frontend && npx vitest run`
Expected: 全テスト PASS

**Step 3: TypeScript 型チェック**

Run: `cd frontend && npx tsc --noEmit`
Expected: エラーなし

**Step 4: コミット**

```bash
git add frontend/
git commit -m "refactor(frontend): standardize response DTO field names

- Calendar: dates prop → entries
- WorkLogEntries, Absences: total → totalCount
- Subordinates, AssignedProjects, PreviousMonthProjects: count → totalCount
- Update all unit tests and E2E test mocks"
```

---

## Task 10: E2E テスト実行 + 最終検証

**Step 1: E2E テスト**

Run: `cd frontend && npx playwright test --project=chromium`
Expected: 全テスト PASS

**Step 2: Backend 再ビルド確認**

Run: `cd backend && ./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 3: Spotless 最終確認**

Run: `cd backend && ./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL

**Step 4: Biome 最終確認**

Run: `cd frontend && npx biome ci`
Expected: 全チェック PASS

---

## 検証チェックリスト

- [ ] `cd backend && ./gradlew build` — コンパイル + テスト PASS
- [ ] `cd backend && ./gradlew spotlessCheck` — フォーマット PASS
- [ ] `cd frontend && npx tsc --noEmit` — 型チェック PASS
- [ ] `cd frontend && npx biome ci` — リント PASS
- [ ] `cd frontend && npx vitest run` — ユニットテスト PASS
- [ ] `cd frontend && npx playwright test --project=chromium` — E2E PASS

## リスク

- **Breaking API change**: モノレポなので Backend/Frontend を同一 PR で更新すれば問題なし
- **`total`/`count` の誤置換**: 汎用語なので機械的一括置換は危険。各変更箇所を個別に特定すること。特に `totalWorkHours`, `submittedCount` 等は触らない
- **E2E モック**: `dates:` キーは文字列リテラルなので TypeScript コンパイラでは検出不可。grep で全箇所特定済み
