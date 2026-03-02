# Monthly Timesheet (月次勤務表) Feature Plan

## Context

現在のシステムでは、WorkLogEntryで「プロジェクト × 日付 × 稼働時間」は管理されているが、**勤務開始時間・終了時間**は記録されていない。ユーザーは、プロジェクト単位で日本の一般的な勤務表形式（日次一覧テーブル）を表示し、インライン編集もできるようにしたい。

**ゴール:** プロジェクト単位の月次勤務表ページを新設し、日次の開始時間・終了時間・稼働時間・備考を閲覧・編集できるようにする。

## Design Decisions

### 1. DailyAttendance: CRUDエンティティ（イベントソーシングなし）
- DailyAttendanceはステータス遷移や承認ワークフローを持たないシンプルなエンティティ
- `MemberProjectAssignment` と同じCRUDパターンで実装（`work_log_events` テーブルは使わない）
- 理由: 複雑なドメインイベントが不要で、CRUD projectionテーブルだけで十分

### 2. 単一のTimesheetエンドポイント（CQRS読み取りモデル）
- `GET /api/v1/worklog/timesheet/{year}/{month}` で DailyAttendance + WorkLogEntry hours + デフォルト時間を結合して返す
- CalendarControllerの既存パターンに倣う

### 3. インライン編集: 行単位の明示的Save
- 各行（各日）にSaveボタンを表示し、明示的に保存
- 楽観的ロック（version）を使用

---

## Backend Changes

### Migration V31: `daily_attendance` テーブル
**File:** `backend/src/main/resources/db/migration/V31__daily_attendance.sql`

```sql
CREATE TABLE IF NOT EXISTS daily_attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    member_id UUID NOT NULL REFERENCES members(id),
    attendance_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    remarks TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_attendance_member_date UNIQUE (member_id, attendance_date)
);

CREATE INDEX idx_daily_attendance_member_date ON daily_attendance(member_id, attendance_date);
CREATE INDEX idx_daily_attendance_tenant ON daily_attendance(tenant_id);
```

### Migration V32: `member_project_assignments` にデフォルト時間カラム追加
**File:** `backend/src/main/resources/db/migration/V32__assignment_default_times.sql`

```sql
ALTER TABLE member_project_assignments
    ADD COLUMN default_start_time TIME,
    ADD COLUMN default_end_time TIME;
```

### Domain Model
**新規:** `backend/src/main/java/com/worklog/domain/attendance/`
- `DailyAttendanceId.java` - UUID wrapper
- `DailyAttendance.java` - エンティティ（id, memberId, tenantId, date, startTime, endTime, remarks, version）
- バリデーション: endTime > startTime（両方指定時）、remarks max 500文字

### Repository
**新規:** `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepository.java`
- 既存パターン参考: `JdbcMemberProjectAssignmentRepository.java`
- `save(DailyAttendance)` - UPSERT（ON CONFLICT DO UPDATE）
- `findByMemberAndDate(memberId, date)` - 単一取得
- `findByMemberAndDateRange(memberId, startDate, endDate)` - 月間データ取得
- `deleteByMemberAndDate(memberId, date)` - 削除

### Service
**新規:** `backend/src/main/java/com/worklog/application/service/DailyAttendanceService.java`
- `saveAttendance(command)` - 作成/更新（楽観的ロック）
- `deleteAttendance(memberId, date)` - 削除
- `getTimesheetData(memberId, projectId, startDate, endDate)` - 結合クエリ

### MemberProjectAssignment 変更
**変更:** `JdbcMemberProjectAssignmentRepository.java`, `MemberProjectAssignment.java`
- `defaultStartTime`, `defaultEndTime` フィールド追加
- 既存のCRUD操作にカラム追加

### Controller
**新規:** `backend/src/main/java/com/worklog/api/TimesheetController.java`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/worklog/timesheet/{year}/{month}` | 月次勤務表データ取得 |
| PUT | `/api/v1/worklog/timesheet/attendance` | 日次出勤記録の保存 |
| DELETE | `/api/v1/worklog/timesheet/attendance/{date}` | 日次出勤記録の削除 |

**Query Parameters (GET):**
- `memberId` - 対象メンバー（省略時は自分）
- `projectId` - 対象プロジェクト（必須）
- `periodType` - `calendar`（1日〜月末）or `fiscal`（21日〜20日）

**Response: `TimesheetResponse`**
```json
{
  "memberId": "uuid",
  "memberName": "string",
  "projectId": "uuid",
  "projectName": "string",
  "periodType": "calendar|fiscal",
  "periodStart": "2026-03-01",
  "periodEnd": "2026-03-31",
  "rows": [
    {
      "date": "2026-03-02",
      "dayOfWeek": "MONDAY",
      "isWeekend": false,
      "isHoliday": false,
      "holidayName": null,
      "startTime": "09:00",
      "endTime": "18:00",
      "workingHours": 8.00,
      "remarks": "",
      "defaultStartTime": "09:00",
      "defaultEndTime": "18:00",
      "hasAttendanceRecord": true,
      "attendanceId": "uuid",
      "attendanceVersion": 0
    }
  ],
  "summary": {
    "totalWorkingHours": 176.0,
    "totalWorkingDays": 22,
    "totalBusinessDays": 22
  }
}
```

### アクセス制御
- 自分のデータ: 閲覧・編集可
- マネージャー: 部下のデータ閲覧可（既存の `managerId` 関係を利用）
- 管理者: 全メンバーのデータ閲覧可

---

## Frontend Changes

### Route
**新規:** `frontend/app/worklog/timesheet/page.tsx`

### Components
**新規:** `frontend/app/components/worklog/timesheet/`

| Component | Description |
|-----------|-------------|
| `TimesheetTable.tsx` | メインテーブル。日付行 × 列（日付/曜日/開始/終了/稼働/備考）。フッターに合計行 |
| `TimesheetRow.tsx` | 1日分の行。`<input type="time">` でインライン編集。Saveボタン |
| `TimesheetPeriodToggle.tsx` | 暦月/締月の切り替えトグル |
| `TimesheetSummary.tsx` | 合計稼働時間、稼働日数、営業日数 |

### レイアウトイメージ
```
┌─────────────────────────────────────────────────────┐
│ 月次勤務表  [Project ▼]  [◀ 2月] [3月] [4月 ▶]     │
│ [暦月 | 締月]  [Member ▼ (manager/admin only)]      │
├──────┬────┬─────┬─────┬──────┬──────────────┬──────┤
│ 日付 │曜日│始業  │終業  │稼働   │備考          │     │
├──────┼────┼─────┼─────┼──────┼──────────────┼──────┤
│ 3/1  │ 日 │     │     │      │              │      │  ← 土日はグレーアウト
│ 3/2  │ 月 │09:00│18:00│ 8.00h│              │ Save │
│ 3/3  │ 火 │09:00│18:00│ 8.00h│定例MTG       │ Save │
│ ...  │    │     │     │      │              │      │
├──────┴────┴─────┴─────┼──────┼──────────────┴──────┤
│ 合計                   │176.0h│ 22日 / 22営業日     │
└────────────────────────┴──────┴─────────────────────┘
```

- デフォルト時間（未保存）: 薄いグレー・イタリック表示
- 保存済み: 通常表示
- 土日: `bg-gray-50` 背景
- 祝日: `bg-amber-50` 背景 + 祝日名表示
- マネージャー/管理者閲覧時: 全フィールド read-only

### Types
**新規:** `frontend/app/types/timesheet.ts`
- `TimesheetResponse`, `TimesheetRow`, `TimesheetSummary`, `SaveAttendanceRequest`

### API Client
**変更:** `frontend/app/services/api.ts`
- `api.worklog.timesheet.get(params)`, `.saveAttendance(req)`, `.deleteAttendance(date)`

### Zustand Store
**変更:** `frontend/app/services/worklogStore.ts`
- `timesheetProjectId`, `timesheetPeriodType` 追加（localStorage永続化）

### i18n
**変更:** `frontend/messages/en.json`, `frontend/messages/ja.json`
- `worklog.timesheet.*` namespace追加

### Navigation
**変更:** `frontend/app/worklog/page.tsx` or Header
- 既存のworklogページヘッダーに「勤務表」リンク追加

---

## Admin: デフォルト時間管理

**変更:** `frontend/app/admin/assignments/` 関連コンポーネント
- アサインメント管理画面にデフォルト開始/終了時間の入力フィールドを追加
- 既存の `AdminAssignmentController` に `PATCH /api/v1/admin/assignments/{id}` を更新

---

## Verification

### Backend Tests
- `DailyAttendanceService` のユニットテスト（CRUD + バリデーション）
- `TimesheetController` のインテグレーションテスト（`IntegrationTestBase` 継承）
- アクセス制御テスト（自分/マネージャー/管理者）

### Frontend Tests
- `TimesheetTable` コンポーネントテスト（Vitest + RTL）
- インライン編集のインタラクションテスト

### E2E Tests (Playwright)
- `/worklog/timesheet` ページ表示
- プロジェクト切り替え
- 暦月/締月切り替え
- インライン編集 → Save → データ反映確認
- マネージャーが部下の勤務表を閲覧（read-only確認）

### Manual Verification
1. `cd backend && ./gradlew test jacocoTestReport`
2. `cd frontend && npm test -- --run`
3. `cd frontend && npx playwright test --project=chromium`

---

## Implementation Sequence

1. **Backend DB:** V31 (daily_attendance) + V32 (assignment defaults) migrations
2. **Backend Domain:** DailyAttendance entity + repository + service
3. **Backend API:** TimesheetController + DTOs + MemberProjectAssignment変更
4. **Backend Tests:** Integration tests
5. **Frontend Types + API:** timesheet.ts + api.ts additions
6. **Frontend Components:** TimesheetTable, TimesheetRow, etc.
7. **Frontend Page:** `/worklog/timesheet/page.tsx`
8. **Frontend i18n + Navigation:** メッセージ追加 + ナビリンク
9. **Admin UI:** アサインメント画面にデフォルト時間フィールド追加
10. **Frontend Tests + E2E:** テスト作成

## Key Files to Modify/Create

### Create
- `backend/src/main/resources/db/migration/V31__daily_attendance.sql`
- `backend/src/main/resources/db/migration/V32__assignment_default_times.sql`
- `backend/src/main/java/com/worklog/domain/attendance/DailyAttendance.java`
- `backend/src/main/java/com/worklog/domain/attendance/DailyAttendanceId.java`
- `backend/src/main/java/com/worklog/infrastructure/repository/JdbcDailyAttendanceRepository.java`
- `backend/src/main/java/com/worklog/application/service/DailyAttendanceService.java`
- `backend/src/main/java/com/worklog/api/TimesheetController.java`
- `backend/src/main/java/com/worklog/api/dto/TimesheetResponse.java` (+ related DTOs)
- `frontend/app/types/timesheet.ts`
- `frontend/app/worklog/timesheet/page.tsx`
- `frontend/app/components/worklog/timesheet/TimesheetTable.tsx`
- `frontend/app/components/worklog/timesheet/TimesheetRow.tsx`
- `frontend/app/components/worklog/timesheet/TimesheetPeriodToggle.tsx`
- `frontend/app/components/worklog/timesheet/TimesheetSummary.tsx`

### Modify
- `backend/src/main/java/com/worklog/domain/project/MemberProjectAssignment.java` (add defaults)
- `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberProjectAssignmentRepository.java`
- `frontend/app/services/api.ts` (add timesheet endpoints)
- `frontend/app/services/worklogStore.ts` (add timesheet state)
- `frontend/messages/en.json`, `frontend/messages/ja.json` (add i18n keys)
- `frontend/app/worklog/page.tsx` or Header (add navigation link)
- Admin assignment components (add default time fields)
