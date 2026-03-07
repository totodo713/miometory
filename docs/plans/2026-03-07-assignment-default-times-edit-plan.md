# アサインのデフォルト始業・終業時間の編集機能

## Context

`member_project_assignments` テーブルには V33 マイグレーションで `default_start_time` / `default_end_time` カラムが追加済み。アサイン作成時にこれらを設定できるが、既存アサインの値を後から変更する API・UI が存在しない。本変更でインライン編集による更新機能を追加する。

## 変更対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `backend/.../service/AdminAssignmentService.java` | `updateDefaultTimes()` + `findMemberIdByAssignment()` メソッド追加 |
| `backend/.../api/AdminAssignmentController.java` | `PATCH /{id}/default-times` エンドポイント + `UpdateDefaultTimesRequest` DTO 追加 |
| `frontend/app/services/api.ts` | `assignments.updateDefaultTimes()` メソッド追加 |
| `frontend/app/components/admin/AssignmentManager.tsx` | インライン編集 UI（状態管理 + 条件付きレンダリング） |
| `backend/.../api/AdminAssignmentControllerTest.kt` | 新エンドポイントの統合テスト追加 |
| `backend/.../resources/static/api-docs/openapi.yaml` | PATCH エンドポイント定義追加 |

## Step 1: Backend Service — `AdminAssignmentService.java`

**ファイル**: `backend/src/main/java/com/worklog/application/service/AdminAssignmentService.java`

### 1a. `findMemberIdByAssignment` メソッド追加

Supervisor 権限チェック用。Controller から呼び出す。

```java
public UUID findMemberIdByAssignment(UUID assignmentId, UUID tenantId) {
    List<UUID> results = jdbcTemplate.queryForList(
        "SELECT member_id FROM member_project_assignments WHERE id = ? AND tenant_id = ?",
        UUID.class, assignmentId, tenantId);
    if (results.isEmpty()) {
        throw new DomainException("ASSIGNMENT_NOT_FOUND", "Assignment not found");
    }
    return results.get(0);
}
```

パターン: `queryForList` + empty check（`queryForObject` は `EmptyResultDataAccessException` を投げるため避ける）

### 1b. `updateDefaultTimes` メソッド追加

既存の `deactivateAssignment` / `activateAssignment` と同じパターン。

```java
public void updateDefaultTimes(UUID assignmentId, UUID tenantId, LocalTime startTime, LocalTime endTime) {
    int rows = jdbcTemplate.update(
        "UPDATE member_project_assignments SET default_start_time = ?, default_end_time = ? WHERE id = ? AND tenant_id = ?",
        startTime != null ? Time.valueOf(startTime) : null,
        endTime != null ? Time.valueOf(endTime) : null,
        assignmentId, tenantId);
    if (rows == 0) {
        throw new DomainException("ASSIGNMENT_NOT_FOUND", "Assignment not found");
    }
}
```

## Step 2: Backend Controller — `AdminAssignmentController.java`

**ファイル**: `backend/src/main/java/com/worklog/api/AdminAssignmentController.java`

### 2a. Request DTO 追加

Controller 内に inner record を追加（`CreateAssignmentRequest` の隣）:

```java
public record UpdateDefaultTimesRequest(LocalTime defaultStartTime, LocalTime defaultEndTime) {}
```

両フィールドとも nullable（クリア = null 送信）。

### 2b. PATCH エンドポイント追加

`activateAssignment` メソッドの後に追加:

```java
@PatchMapping("/{id}/default-times")
@PreAuthorize("hasPermission(null, 'assignment.create')")
public void updateDefaultTimes(
        @PathVariable UUID id,
        @RequestBody @Valid UpdateDefaultTimesRequest request,
        Authentication authentication) {
    UUID tenantId = userContextService.resolveUserTenantId(authentication.getName());
    UUID memberId = adminAssignmentService.findMemberIdByAssignment(id, tenantId);
    enforceDirectReportIfSupervisor(authentication.getName(), memberId);
    adminAssignmentService.updateDefaultTimes(id, tenantId, request.defaultStartTime(), request.defaultEndTime());
}
```

- **権限**: `assignment.create` を再利用（新権限追加はマイグレーション不要）
- **Supervisor**: `enforceDirectReportIfSupervisor` で直属部下チェック（create と同一パターン）
- **戻り値**: `void`（200 OK）— deactivate/activate と同じ

## Step 3: Frontend API Client — `api.ts`

**ファイル**: `frontend/app/services/api.ts` (L1026 の `activate` の後)

```typescript
updateDefaultTimes: (id: string, data: {
  defaultStartTime: string | null;
  defaultEndTime: string | null;
}) => apiClient.patch<void>(`/api/v1/admin/assignments/${id}/default-times`, data),
```

## Step 4: Frontend Component — `AssignmentManager.tsx`

**ファイル**: `frontend/app/components/admin/AssignmentManager.tsx`

### 4a. 状態追加（L54 付近）

```typescript
const [editingId, setEditingId] = useState<string | null>(null);
const [editStartTime, setEditStartTime] = useState("");
const [editEndTime, setEditEndTime] = useState("");
const [isSaving, setIsSaving] = useState(false);
```

### 4b. ハンドラー追加（`handleToggle` の後）

- `handleEditStart(assignment)`: `editingId` 設定 + 現在値でフォーム初期化
- `handleEditCancel()`: `editingId` を null にリセット
- `handleEditSave()`: API 呼び出し → 成功で `onRefresh()` + 編集終了、失敗で `setError()`

### 4c. テーブル行の条件付きレンダリング

各行で `editingId === a.id` を判定:

**通常モード**: 現在と同じ（時間テキスト表示 + Enable/Disable + **Edit ボタン追加**）
**編集モード**: 時間セルを `<input type="time">` に差し替え + Save/Cancel ボタン表示

```
┌────────────┬───────────┬──────────┬────────┬──────────┐
│ Project    │ Start     │ End      │ Status │ Actions  │
├────────────┼───────────┼──────────┼────────┼──────────┤
│ PRJ-001    │ 09:00     │ 18:00    │ Active │ Edit  ×  │  ← 通常モード
│ PRJ-002    │ [09:00▼]  │ [17:30▼] │ Active │ Save  ✕  │  ← 編集モード
│ PRJ-003    │ —         │ —        │ Active │ Edit  ×  │
└────────────┴───────────┴──────────┴────────┴──────────┘
```

**アクセシビリティ**:
- `<input type="time">` に `aria-label={t("form.defaultStartTime")}` / `{t("form.defaultEndTime")}`
- `focus:outline-none focus:ring-2 focus:ring-blue-500`（CLAUDE.md パターン準拠）

### 4d. i18n

新規キー不要。使用する既存キー:
- `common`: `edit`, `save`, `saving`, `cancel`
- `admin.assignments`: `updated`, `updateError`, `form.defaultStartTime`, `form.defaultEndTime`

## Step 5: Backend テスト — `AdminAssignmentControllerTest.kt`

**ファイル**: `backend/src/test/kotlin/com/worklog/api/AdminAssignmentControllerTest.kt`

追加テストケース:

1. **`update default times returns 200`** — 作成後に PATCH → 200、GET で変更値を確認
2. **`clear default times to null returns 200`** — 時間設定済みを PATCH null → 200、GET で null 確認
3. **`update non-existent assignment returns 404`** — ランダム UUID で PATCH → 404 + `ASSIGNMENT_NOT_FOUND`
4. **`supervisor can update for direct report`** — 部下のアサインを PATCH → 200
5. **`supervisor cannot update for non-direct-report`** — 非部下のアサインを PATCH → 400 + `NOT_DIRECT_REPORT`

## Step 6: OpenAPI Spec 更新

**ファイル**: `backend/src/main/resources/static/api-docs/openapi.yaml`

`PATCH /api/v1/admin/assignments/{id}/default-times` のパス定義を追加。request body に `defaultStartTime`（nullable time）と `defaultEndTime`（nullable time）。

## 検証手順

### Backend
```bash
cd backend && ./gradlew spotlessApply
cd backend && ./gradlew test --tests "com.worklog.api.AdminAssignmentControllerTest"
```

### Frontend
```bash
cd frontend && npx biome check --write app/components/admin/AssignmentManager.tsx app/services/api.ts
cd frontend && npx tsc --noEmit
```

### E2E 手動確認
1. 管理画面 > アサイン > メンバー選択 > テーブル表示
2. Edit ボタン押下 → 時間 input に切り替わることを確認
3. 時間変更 → Save → テーブルに反映
4. 再度 Edit → 時間をクリア → Save → 「—」表示に戻る
5. Cancel → 元の値に戻る
