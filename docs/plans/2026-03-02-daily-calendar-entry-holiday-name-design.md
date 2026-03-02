# DailyCalendarEntry DTO: holidayName/holidayNameJa 追加

**Issue:** #71
**Date:** 2026-03-02

## 概要

カレンダーAPI レスポンス DTO `DailyCalendarEntry` に祝日名フィールドを追加する。

## 変更内容

### フィールド追加

`isHoliday` の直後に以下を追加（#72 CalendarController統合のコード例と整合）:

- `String holidayName` — 英語の祝日名（nullable）
- `String holidayNameJa` — 日本語の祝日名（nullable）

### フィールド順序

```
date, totalWorkHours, totalAbsenceHours, status,
isWeekend, isHoliday, holidayName, holidayNameJa,
hasProxyEntries, rejectionSource, rejectionReason
```

### 後方互換コンストラクタ

1. **9引数版** — rejection付き、holiday name なし（既存 CalendarController 向け）
2. **7引数版** — rejection・holiday name なし（最小コンストラクタ）

## 影響範囲

- `CalendarController.java` — 9引数コンストラクタにバインドされるため変更不要
- `CalendarControllerTest.kt` — HTTP統合テストのため変更不要
- `DailyEntryProjection.java` — 変更不要（issue備考通り）

## 依存関係

- ブロック元: #69 (DB マイグレーション) — CLOSED
- ブロック: #72 (CalendarController 統合) — OPEN
