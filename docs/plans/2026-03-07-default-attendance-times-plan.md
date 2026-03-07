# Plan: デフォルト出退勤時間の階層設定

## Context

勤務表（タイムシート）に初期値として表示する開始・終了時間を、組織の階層構造に沿って設定できるようにする。現在、デフォルト時間はプロジェクトアサインメント単位でのみ設定可能だが、テナント全体や組織単位でのデフォルト値が設定できない。

**解決チェーン（優先順位）**:
```
実際の打刻 > アサインメント個別設定 > Member > Organization階層 > Tenant > System(09:00/18:00)
```

`standard_daily_hours`（V31）と完全に同じパターンを踏襲する。

---

## Step 1: DB マイグレーション (V34)

**新規**: `backend/src/main/resources/db/migration/V34__default_attendance_times.sql`

```sql
ALTER TABLE members ADD COLUMN default_start_time TIME;
ALTER TABLE members ADD COLUMN default_end_time TIME;

ALTER TABLE organization ADD COLUMN default_start_time TIME;
ALTER TABLE organization ADD COLUMN default_end_time TIME;

ALTER TABLE tenant ADD COLUMN default_start_time TIME;
ALTER TABLE tenant ADD COLUMN default_end_time TIME;

INSERT INTO system_default_settings (setting_key, setting_value) VALUES
  ('default_attendance_times', '{"startTime": "09:00", "endTime": "18:00"}')
ON CONFLICT (setting_key) DO NOTHING;
```

**参考**: V31 (`V31__standard_working_hours.sql`) — 同じ3テーブル + system_default_settings パターン

---

## Step 2: Backend ドメイン層

### 2a. Tenant 集約（イベントソーシング）

**変更**: `backend/src/main/java/com/worklog/domain/tenant/Tenant.java`
- フィールド追加: `private LocalTime defaultStartTime; private LocalTime defaultEndTime;`
- コマンドメソッド: `assignDefaultAttendanceTimes(LocalTime startTime, LocalTime endTime)`
- `apply()` に `TenantDefaultAttendanceTimesAssigned` ケース追加
- ゲッター追加

**新規**: `backend/src/main/java/com/worklog/domain/tenant/TenantDefaultAttendanceTimesAssigned.java`
- `TenantStandardDailyHoursAssigned.java` を雛形に作成
- フィールド: `UUID eventId, Instant occurredAt, UUID aggregateId, LocalTime defaultStartTime, LocalTime defaultEndTime`

### 2b. Organization 集約（イベントソーシング）

**変更**: `backend/src/main/java/com/worklog/domain/organization/Organization.java`
- 同様にフィールド、コマンド、apply、ゲッター追加

**新規**: `backend/src/main/java/com/worklog/domain/organization/OrganizationDefaultAttendanceTimesAssigned.java`
- `OrganizationStandardDailyHoursAssigned.java` を雛形に作成

### 2c. Member エンティティ（CRUD、非イベントソーシング）

**変更**: `backend/src/main/java/com/worklog/domain/member/Member.java`
- フィールド追加 + ゲッター + `updateDefaultAttendanceTimes()` メソッド
- 全引数コンストラクタに `LocalTime defaultStartTime, LocalTime defaultEndTime` 追加

---

## Step 3: Backend インフラ層

### 3a. リポジトリ更新

**変更**: `backend/src/main/java/com/worklog/infrastructure/repository/TenantRepository.java`
- `updateProjection()`: INSERT/UPSERT に `default_start_time, default_end_time` 追加
- `deserializeEvent()`: `TenantDefaultAttendanceTimesAssigned` ケース追加

**変更**: `backend/src/main/java/com/worklog/infrastructure/repository/OrganizationRepository.java`
- 同様に projection と event deserialization を更新

**変更**: `backend/src/main/java/com/worklog/infrastructure/repository/JdbcMemberRepository.java`
- 全SQL（SELECT/INSERT/UPSERT）に2カラム追加（複数箇所あり）
- `MemberRowMapper` で `default_start_time`, `default_end_time` を読み取り

**変更**: `backend/src/main/java/com/worklog/infrastructure/repository/SystemDefaultSettingsRepository.java`
- `KEY_DEFAULT_ATTENDANCE_TIMES = "default_attendance_times"` 定数追加
- `getDefaultAttendanceTimes()` / `updateDefaultAttendanceTimes()` メソッド追加

### 3b. 解決サービス

**新規**: `backend/src/main/java/com/worklog/application/service/DefaultAttendanceTimesService.java`
- `StandardWorkingHoursService.java` を雛形に完全踏襲
- メソッド: `resolveDefaultAttendanceTimes(UUID memberId)` → `AttendanceTimesResolution`
- 解決チェーン: Member → Org hierarchy → Tenant → System(09:00/18:00)

**新規**: `backend/src/main/java/com/worklog/application/service/AttendanceTimesResolution.java`
- `public record AttendanceTimesResolution(LocalTime startTime, LocalTime endTime, String source) {}`
- `StandardHoursResolution.java` と同パターン

---

## Step 4: Backend API 層

### 4a. System Settings

**変更**: `backend/src/main/java/com/worklog/api/SystemSettingsController.java`
- `GET /api/v1/admin/system/settings/attendance-times` (権限: `system_settings.view`)
- `PUT /api/v1/admin/system/settings/attendance-times` (権限: `system_settings.update`)

**変更**: `backend/src/main/java/com/worklog/application/service/SystemSettingsService.java`
- `getDefaultAttendanceTimes()` / `updateDefaultAttendanceTimes()` 追加

### 4b. Tenant Settings

**変更**: `backend/src/main/java/com/worklog/api/TenantSettingsController.java`
- `GET /api/v1/tenant-settings/attendance-times` (権限: `tenant_settings.view`)
- `PUT /api/v1/tenant-settings/attendance-times` (権限: `tenant_settings.manage`)

### 4c. Timesheet 統合

**変更**: `backend/src/main/java/com/worklog/api/TimesheetController.java`
- `DefaultAttendanceTimesService` を注入
- `getMonthlyTimesheet()` 141-142行目を変更:

```java
// 現在:
assignment != null ? assignment.getDefaultStartTime() : null,
assignment != null ? assignment.getDefaultEndTime() : null,

// 変更後: アサインメント未設定時に階層チェーンへフォールバック
LocalTime effectiveDefaultStart = assignment != null ? assignment.getDefaultStartTime() : null;
LocalTime effectiveDefaultEnd = assignment != null ? assignment.getDefaultEndTime() : null;
if (effectiveDefaultStart == null && effectiveDefaultEnd == null) {
    AttendanceTimesResolution resolution = defaultAttendanceTimesService.resolveDefaultAttendanceTimes(memberId);
    effectiveDefaultStart = resolution.startTime();
    effectiveDefaultEnd = resolution.endTime();
}
```

**注**: `TimesheetProjection.java`, `TimesheetResponse.java`, `TimesheetRow.tsx` は変更不要。既に `defaultStartTime` / `defaultEndTime` フィールドが存在する。

---

## Step 5: Frontend

### 5a. API クライアント

**変更**: `frontend/app/services/api.ts`
- `system` に `getAttendanceTimes()` / `updateAttendanceTimes()` 追加
- `tenantSettings` に `getAttendanceTimes()` / `updateAttendanceTimes()` 追加

### 5b. System Settings UI

**変更**: `frontend/app/components/admin/SystemSettingsSection.tsx`
- デフォルト出退勤時間セクション追加（`<input type="time">` × 2）
- 取得: `api.admin.system.getAttendanceTimes()`
- 保存: `api.admin.system.updateAttendanceTimes()`

### 5c. Tenant Settings UI

**変更**: `frontend/app/components/admin/TenantSettingsSection.tsx`
- デフォルト出退勤時間セクション追加
- 「なし（システムから継承）」クリアオプション付き

### 5d. i18n

**変更**: `frontend/messages/en.json` / `frontend/messages/ja.json`
- `admin.systemSettings.attendanceTimes` キー追加
- `admin.tenantSettings.attendanceTimes` キー追加

---

## Step 6: OpenAPI Spec

**変更**: `backend/src/main/resources/static/api-docs/openapi.yaml`
- 4エンドポイント追加 + `DefaultAttendanceTimes` スキーマ追加

---

## Step 7: テスト

### Backend

**新規**: `backend/src/test/kotlin/com/worklog/application/service/DefaultAttendanceTimesServiceTest.kt`
- `StandardWorkingHoursServiceTest.kt` と同パターン
- テストケース: member設定あり、org階層ウォーク、tenant設定、systemフォールバック

**変更**: 既存の Tenant / Organization ドメインテストにイベントテスト追加
**変更**: Controller テストに新エンドポイントのテスト追加

### Frontend

- Settings UI コンポーネントテスト
- E2E: 設定画面で出退勤時間を設定→保存→反映確認

---

## 実装順序

1. DB マイグレーション (V34)
2. ドメインイベント (Tenant + Organization)
3. ドメインエンティティ変更 (Tenant, Organization, Member)
4. リポジトリ更新 (4ファイル)
5. 解決サービス (DefaultAttendanceTimesService + record)
6. API コントローラ (System + Tenant Settings)
7. Timesheet 統合 (TimesheetController)
8. Backend テスト
9. Frontend API クライアント
10. Frontend UI (Settings + i18n)
11. OpenAPI spec
12. Frontend テスト

---

## 検証方法

1. **Backend ビルド**: `cd backend && ./gradlew build` — マイグレーション・コンパイル・テスト一括確認
2. **解決チェーンのテスト**: `DefaultAttendanceTimesServiceTest` で 4 階層全パターン確認
3. **API 手動テスト**: devcontainer 起動 → curl で GET/PUT エンドポイント確認
4. **Frontend UI**: Settings 画面で時間設定 → タイムシート画面で初期値が反映されることを確認
5. **Spotless**: `cd backend && ./gradlew spotlessApply` を必ず実行

---

## 注意事項

- **start/end は対 (pair) として扱う**: いずれかが non-null ならそのレベルの値を使用
- **JdbcMemberRepository の SQL 多数**: 10箇所以上のSQLにカラム追加が必要 — 漏れに注意
- **Testcontainers キャッシュ**: V34 追加後、既存テストコンテナを `docker stop/rm` する必要あり
- **Spotless**: Java ファイル編集後は必ず `./gradlew spotlessApply` 実行
