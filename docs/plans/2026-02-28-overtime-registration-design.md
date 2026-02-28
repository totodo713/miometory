# 残業時間登録機能 — Phase 1 実装計画

## Context

現在のwork-logアプリケーションには `WorkLogEntry` で日々の作業時間を記録する機能があるが、「残業」の概念が存在しない。所定労働時間を超えた分を自動的に残業として算出し、日次・月次で表示できるようにする。

**Phase 1 スコープ**: 所定労働時間の階層設定 + 通常残業の自動計算・表示
**Phase 2+ (将来)**: 深夜残業(22:00-5:00)、休日勤務、残業種別の手動指定、プロジェクト別所定労働時間

---

## 設計方針

### 所定労働時間の階層的解決 (Resolution Chain)

既存の `DateInfoService.resolveFiscalYearPattern()` パターンに準拠:

```
Member individual → Organization hierarchy walk (子→親→...→root) → Tenant default → System default (8.0h)
```

- 各レベルで `standard_daily_hours` が `NULL` なら次のレベルへフォールバック
- System default は `system_default_settings` テーブルに `setting_key = 'standard_daily_hours'` として格納

### 残業計算ロジック

- **日次残業**: `max(0, 日合計作業時間 - 所定労働時間/日)`
- **月次残業**: 月内の日次残業の合計
- **月次所定労働時間**: `所定労働時間/日 × 営業日数`
- 残業は **導出値** であり、独自のエンティティ/テーブルは持たない（プロジェクション層で計算）

---

## 実装ステップ

### Step 1: DB マイグレーション (`V29__standard_working_hours.sql`)

```sql
ALTER TABLE members ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE organization ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

ALTER TABLE tenant ADD COLUMN standard_daily_hours DECIMAL(4,2)
  CHECK (standard_daily_hours IS NULL OR (standard_daily_hours >= 0.25 AND standard_daily_hours <= 24.00));

INSERT INTO system_default_settings (setting_key, setting_value) VALUES
  ('standard_daily_hours', '{"hours": 8.0}')
ON CONFLICT (setting_key) DO NOTHING;
```

**ファイル**: `backend/src/main/resources/db/migration/V29__standard_working_hours.sql`

### Step 2: ドメインモデル変更

**変更ファイル**:
- `backend/src/main/java/com/worklog/domain/member/Member.java` — `standardDailyHours` (BigDecimal, nullable) フィールド追加
- `backend/src/main/java/com/worklog/domain/organization/Organization.java` — `standardDailyHours` フィールド追加
- `backend/src/main/java/com/worklog/domain/tenant/Tenant.java` — `standardDailyHours` フィールド追加

イベントソーシングの整合性:
- 各エンティティに `StandardDailyHoursUpdated` イベントを追加
- 既存のリポジトリに投影ロジック追加

### Step 3: 所定労働時間解決サービス

**新規ファイル**: `backend/src/main/java/com/worklog/application/service/StandardWorkingHoursService.java`

```java
@Service
public class StandardWorkingHoursService {
    // DateInfoService のパターンを踏襲
    public StandardHoursResolution resolveStandardDailyHours(UUID memberId) {
        // 1. Member の standard_daily_hours を確認
        // 2. Member の Organization → 親 Organization → root まで walk
        // 3. Tenant default
        // 4. System default (8.0h)
        // → StandardHoursResolution(hours, source) を返す
    }
}
```

**参照**: `DateInfoService.java` (L132-155) の `resolveFiscalYearPattern()` パターン

**新規レコード**: `StandardHoursResolution(BigDecimal hours, String source)`

### Step 4: MonthlySummary プロジェクション拡張

**変更ファイル**: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java`

`getMonthlySummary()` メソッドに `StandardWorkingHoursService` を注入し:
1. メンバーの所定労働時間を解決
2. 日次残業を計算（日ごとの合計作業時間 - 所定労働時間）
3. 月次残業 = 日次残業の合計
4. 月次所定労働時間 = 所定労働時間/日 × 営業日数

**変更ファイル**: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryData.java`

フィールド追加:
```java
public record MonthlySummaryData(
    // ... 既存フィールド
    BigDecimal standardDailyHours,      // 所定労働時間/日 (解決済み)
    BigDecimal standardMonthlyHours,    // 所定労働時間/月 (日×営業日数)
    BigDecimal overtimeHours,           // 月次残業時間合計
    String standardHoursSource          // 設定元 ("member", "organization:xxx", "tenant", "system")
) { ... }
```

### Step 5: カレンダープロジェクション拡張

**変更ファイル**: `backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java`

日次データに残業情報を追加:
- 各日の `totalHours` と `standardDailyHours` から `overtimeHours` を算出
- `calendar_data` JSONB に `overtimeHours` フィールドを含める

### Step 6: API レスポンス更新

**変更ファイル**: `backend/src/main/java/com/worklog/api/CalendarController.java`

既存エンドポイントのレスポンスに新フィールドが自動的に含まれる（`MonthlySummaryData` レコードの変更による）。

フロントエンド用の所定労働時間取得:
- `GET /api/v1/worklog/calendar/{year}/{month}/summary` のレスポンスに `standardDailyHours`, `standardMonthlyHours`, `overtimeHours`, `standardHoursSource` を追加

### Step 7: フロントエンド型定義更新

**変更ファイル**: `frontend/app/components/worklog/MonthlySummary.tsx`

`MonthlySummaryData` interface に追加:
```typescript
standardDailyHours: number;
standardMonthlyHours: number;
overtimeHours: number;
standardHoursSource: string;
```

### Step 8: MonthlySummary コンポーネント更新

**変更ファイル**: `frontend/app/components/worklog/MonthlySummary.tsx`

3カード → 4カードグリッドに変更:
1. Total Hours (既存)
2. Absence Days (既存)
3. Working Days (既存)
4. **Overtime Hours** (新規) — オレンジ系カラーで残業時間を表示

既存i18nキー活用: `worklog.monthlySummary.overtimeHours`, `worklog.monthlySummary.requiredHours`

### Step 9: DailyEntryForm コンポーネント更新

**変更ファイル**: `frontend/app/components/worklog/DailyEntryForm.tsx`

合計時間表示ブロックに残業インジケーターを追加:
- `totalWorkHours > standardDailyHours` の場合、残業時間をオレンジ色で表示
- 既存i18nキー活用: `worklog.dailyEntry.overtime`, `worklog.dailyEntry.requiredHours`
- 所定労働時間は月次サマリーAPIレスポンスから取得（親コンポーネント経由でprop渡し）

### Step 10: テスト

**バックエンド**:
- `StandardWorkingHoursServiceTest` — 解決チェーンの全パターン（Member設定あり、Organization階層、Tenant、System default）
- `MonthlySummaryProjectionTest` — 残業計算の正確性（境界値: 0h残業、部分残業、複数日）
- `CalendarControllerTest` — APIレスポンスに新フィールドが含まれること

**フロントエンド**:
- `MonthlySummary.test.tsx` — 残業カードの表示/非表示
- `DailyEntryForm.test.tsx` — 残業インジケーターの表示

---

## 重要ファイル一覧

| ファイル | 変更種別 |
|---------|---------|
| `backend/src/main/resources/db/migration/V29__standard_working_hours.sql` | 新規 |
| `backend/src/main/java/com/worklog/domain/member/Member.java` | 修正 |
| `backend/src/main/java/com/worklog/domain/organization/Organization.java` | 修正 |
| `backend/src/main/java/com/worklog/domain/tenant/Tenant.java` | 修正 |
| `backend/src/main/java/com/worklog/application/service/StandardWorkingHoursService.java` | 新規 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryProjection.java` | 修正 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlySummaryData.java` | 修正 |
| `backend/src/main/java/com/worklog/infrastructure/projection/MonthlyCalendarProjection.java` | 修正 |
| `backend/src/main/java/com/worklog/api/CalendarController.java` | 修正 |
| `frontend/app/components/worklog/MonthlySummary.tsx` | 修正 |
| `frontend/app/components/worklog/DailyEntryForm.tsx` | 修正 |

## 再利用する既存パターン

- **`DateInfoService.resolveFiscalYearPattern()`** (L132-155): 階層的解決のリファレンス実装
- **`SystemDefaultSettingsRepository`**: system_default_settings テーブルアクセス
- **`TimeAmount` 値オブジェクト**: 0.25h刻み制約の再利用
- **既存i18nキー**: `worklog.dailyEntry.overtime`, `worklog.monthlySummary.overtimeHours` 等

## 検証方法

1. **バックエンドテスト**: `cd backend && ./gradlew test` — StandardWorkingHoursService と MonthlySummaryProjection のテスト通過
2. **フロントエンドテスト**: `cd frontend && npm test -- --run` — コンポーネントテスト通過
3. **手動検証**:
   - system_default_settings に 8.0h が設定されていることを確認
   - 8h超の作業を記録 → 日次画面に残業表示
   - 月次サマリーに残業カードが表示される
4. **E2Eテスト**: `cd frontend && npx playwright test --project=chromium` — 残業表示のE2Eシナリオ
