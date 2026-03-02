# Plan: HTTP Response DTO JSON フィールド名の標準化

## Context

Response DTOのJSONフィールド名に不統一がある。具体的には:
- `MonthlyCalendarResponse`のコレクションフィールドが`dates`（内容は`DailyCalendarEntry`のリスト）
- カウントフィールドが`total` / `count` / `totalCount`と混在

これを統一して、APIの一貫性と開発者体験を改善する。

## 命名規則

| ルール | 方針 | 理由 |
|--------|------|------|
| **コレクションフィールド** | ドメイン固有の複数形名詞を維持 | `absences`, `subordinates`, `projects`等は意味が明確 |
| **`dates` → `entries`** | 変更する | DailyCalendarEntryのリストなのに`dates`は誤解を招く |
| **汎用カウント** | `totalCount`に統一 | `total`は"total hours"等と紛らわしい。`totalCount`が最も明示的 |
| **アクション固有カウント** | 維持 | `submittedCount`, `rejectedCount`, `recalledCount`はアクション結果の意味を持つ |
| **Page DTO** | Spring Page規約を維持 | `content`/`totalElements`/`totalPages`は業界標準 |
| **カウント未設置DTO** | 追加しない | `.length`/.size()`で十分 |

## 変更一覧

| DTO | 変更前 | 変更後 | 変更種別 |
|-----|--------|--------|----------|
| `MonthlyCalendarResponse` | `dates` | `entries` | コレクション名変更 |
| `WorkLogEntriesResponse` | `total` | `totalCount` | カウント名変更 |
| `AbsencesResponse` | `total` | `totalCount` | カウント名変更 |
| `SubordinatesResponse` | `count` | `totalCount` | カウント名変更 |
| `AssignedProjectsResponse` | `count` | `totalCount` | カウント名変更 |
| `PreviousMonthProjectsResponse` | `count` | `totalCount` | カウント名変更 |

変更なし: `ApprovalQueueResponse`(既に`totalCount`)、Submit/Reject/Recall系(アクション固有)、Page DTO系(Spring規約)

## 実装ステップ

### Step 1: Backend DTO record リネーム (6ファイル)

| ファイル | 変更内容 |
|----------|----------|
| `backend/src/main/java/com/worklog/api/dto/MonthlyCalendarResponse.java` | `dates` → `entries`（recordコンポーネント + 後方互換コンストラクタ） |
| `backend/src/main/java/com/worklog/api/dto/WorkLogEntriesResponse.java` | `total` → `totalCount` |
| `backend/src/main/java/com/worklog/api/dto/AbsencesResponse.java` | `total` → `totalCount` |
| `backend/src/main/java/com/worklog/api/dto/SubordinatesResponse.java` | `count` → `totalCount` |
| `backend/src/main/java/com/worklog/api/dto/AssignedProjectsResponse.java` | `count` → `totalCount` |
| `backend/src/main/java/com/worklog/api/dto/PreviousMonthProjectsResponse.java` | `count` → `totalCount`（@param javadocも更新） |

### Step 2: Backend コンパイル確認 + アクセサ参照の修正

recordリネームによりアクセサ名（`.count()` → `.totalCount()`等）が変わる。コンパイルエラーで検出できる。
- Javaコンパイラが壊れた参照をすべて報告 → 修正

### Step 3: Backend テスト修正

| テストファイル | 変更内容 |
|----------------|----------|
| `backend/src/test/kotlin/com/worklog/api/CalendarControllerTest.kt` | `body["dates"]` → `body["entries"]` (L188, L211) |
| `backend/src/test/java/com/worklog/api/WorkLogControllerCalendarRejectionTest.java` | `body.get("dates")` → `body.get("entries")` (L165, L218) |
| `backend/src/test/kotlin/com/worklog/api/AbsenceControllerTest.kt` | `body["total"]` → `body["totalCount"]` (L751) |
| `backend/src/test/kotlin/com/worklog/api/ProjectControllerTest.kt` | `body["count"]` → `body["totalCount"]` (L42, L75, L131, L211) |

### Step 4: Frontend 型定義の修正

| ファイル | 変更内容 |
|----------|----------|
| `frontend/app/types/worklog.ts` | `MonthlyCalendarResponse.dates` → `entries` (L79)、`WorkLogEntriesResponse.total` → `totalCount` (L53)、`AssignedProjectsResponse.count` → `totalCount` (L119) |
| `frontend/app/services/api.ts` | inline型の`total:` → `totalCount:` (L391, L596)、`count:` → `totalCount:` (L469, L814, L839) |
| `frontend/app/components/worklog/CopyPreviousMonthDialog.tsx` | `PreviousMonthData.count` → `totalCount` (L29)、`data.count` → `data.totalCount` (L207, L253) |

### Step 5: Frontend コンポーネント修正

| ファイル | 変更内容 |
|----------|----------|
| `frontend/app/components/worklog/Calendar.tsx` | prop `dates` → `entries` (L26)、内部参照すべて |
| `frontend/app/worklog/page.tsx` | `calendarData.dates` → `calendarData.entries` (L255) |

### Step 6: Frontend テスト修正

| ファイル | 変更内容 |
|----------|----------|
| `frontend/tests/unit/components/Calendar.test.tsx` | prop `dates={...}` → `entries={...}` (30+箇所) |
| E2Eテスト (9ファイル) のAPIモックで `dates:` → `entries:` |
| - `accessibility.spec.ts` (L36) | |
| - `absence-entry.spec.ts` (L53, L517, L588) | |
| - `approval-workflow.spec.ts` (L51) | |
| - `copy-previous-month.spec.ts` (L34) | |
| - `csv-operations.spec.ts` (L50) | |
| - `daily-entry.spec.ts` (L35) | |
| - `multi-project-entry.spec.ts` (L30) | |
| - `proxy-entry.spec.ts` (L124) | |

## 検証

```bash
# 1. Backend コンパイル + テスト
./gradlew test

# 2. Backend フォーマット
./gradlew spotlessApply

# 3. Frontend 型チェック (最も重要な安全網)
cd frontend && npx tsc --noEmit

# 4. Frontend lint
cd frontend && npx biome ci

# 5. Frontend ユニットテスト
cd frontend && npm test -- --run

# 6. E2Eテスト
cd frontend && npx playwright test --project=chromium
```

TypeScript コンパイラが最強の安全網 — 型定義を先に変更すれば、フロントエンド側の全参照漏れを検出できる。

## リスク

- **Breaking API change**: モノレポなのでBackend/Frontendを同一PRで更新すれば問題なし
- **`total`/`count`の誤検出**: 汎用語なので機械的置換は危険。各変更箇所を個別に特定済み
- **E2E flakiness**: `dates:` キーがモック内にあり文字列リテラルなのでコンパイラ検出不可 → grepで全箇所特定済み
