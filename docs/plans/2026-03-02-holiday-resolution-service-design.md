# HolidayResolutionService 設計

## Issue
#70 — テナントの祝日カレンダーエントリを指定期間の実際の日付に解決する `HolidayResolutionService` を新規作成

## スコープ
- HolidayResolutionService + HolidayCalendarEntryRepository + HolidayInfo の新規作成
- ユニットテスト作成
- MonthlyCalendarProjection への統合は #72 で対応（本 issue のスコープ外）

## アプローチ
Repository 分離パターン: データアクセスを `HolidayCalendarEntryRepository` に分離し、テスタビリティを向上

## ファイル構成

```
backend/src/main/java/com/worklog/
├── application/service/
│   └── HolidayResolutionService.java        # 日付解決ロジック
├── domain/model/
│   └── HolidayInfo.java                     # record(name, nameJa, date)
└── infrastructure/repository/
    └── HolidayCalendarEntryRepository.java  # JDBC データアクセス

backend/src/test/java/com/worklog/application/service/
    └── HolidayResolutionServiceTest.java    # ユニットテスト
```

## コンポーネント設計

### HolidayInfo
```java
public record HolidayInfo(String name, String nameJa, LocalDate date) {}
```

### HolidayCalendarEntryRepository
- `@Repository` + `JdbcTemplate`
- `findActiveEntriesByTenantId(UUID tenantId)` → `List<HolidayCalendarEntryRow>`
- SQL: issue 記載の JOIN クエリ

### HolidayResolutionService
- `@Service` + コンストラクタ注入
- `resolveHolidays(UUID tenantId, LocalDate start, LocalDate end)` → `Map<LocalDate, HolidayInfo>`
- `@Cacheable("calendar:holidays")` でキャッシュ

### 日付解決アルゴリズム

**FIXED:**
- `LocalDate.of(year, entry.month, entry.day)` — 期間内の各年に展開

**NTH_WEEKDAY:**
1. 対象月の1日を取得
2. `TemporalAdjusters.firstInMonth(dayOfWeek)` で最初の出現取得
3. `(nthOccurrence - 1) * 7` 日加算
4. 結果が同月内でなければスキップ（5回目が存在しない場合）

**specific_year フィルタ:**
- `specific_year != null` → その年のみ
- `specific_year == null` → 期間内の全年に展開

## テストケース
1. FIXED 祝日解決（元日 = 1月1日）
2. NTH_WEEKDAY 祝日解決（成人の日 = 1月第2月曜日）
3. 複数月・複数年にまたがる期間
4. specific_year フィルタリング
5. 存在しない NTH_WEEKDAY（5回目が月外）→ スキップ
6. アクティブでないカレンダーは除外

## 依存関係
- ブロック元: #69 (DB マイグレーション — 完了済み前提)
- ブロック: #72 (CalendarController 統合)
