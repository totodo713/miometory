# Feature Proposal: Work-Log Entry System

**Feature Branch**: `002-work-log-entry`  
**Created**: 2026-01-02  
**Status**: Draft  
**Type**: Core Feature - 稼働時間入力システム

---

## 1. 機能概要 (Feature Overview)

### 1.1 目的 (Purpose)

エンジニアが自分の稼働時間を日単位で入力・管理するシステムを構築する。
月度単位でカレンダー表示し、複数案件への時間配分を柔軟に記録できる。

**主要ユースケース**:
- メンバーが年度・月度を選択して1ヶ月分の稼働時間を入力
- 複数案件への時間配分（15分単位 = 0.25時間刻み）
- CSV一括入力/出力による効率化
- 前月案件コピーによる入力負担軽減
- 承認フロー（承認/差し戻し）

### 1.2 スコープ (Scope)

**Phase 1 (MVP) に含むもの**:
- ✅ 稼働時間入力（日単位、0.25h刻み）
- ✅ 複数案件の同時管理
- ✅ 休暇入力（年休、有休、病欠など）
- ✅ CSV一括入力/出力
- ✅ 前月案件コピー
- ✅ 承認フロー（承認/差し戻し）
- ✅ 祝日マスタ管理（バックエンド）
- ✅ レスポンシブUI（PC/スマホ/タブレット共通）

**Phase 2 以降に延期**:
- ❌ 通知機能（メール/Slack）
- ❌ ダッシュボード・分析画面
- ❌ 案件予算管理・進捗アラート
- ❌ エクスポート（Excel/PDF）
- ❌ 承認履歴・コメント機能（簡易版のみ実装）

---

## 2. ユーザーストーリー (User Stories)

### US-001: 稼働時間入力（基本）
**As a** メンバー  
**I want to** 年度・月度を選択してカレンダーで1ヶ月分の稼働時間を入力したい  
**So that** 自分の稼働実績を正確に記録できる

**Acceptance Criteria**:
- [ ] 年度・月度セレクタで対象月を選択できる
- [ ] カレンダーで月全体を俯瞰できる（土日祝日が識別可能）
- [ ] 日付をクリックすると詳細入力モーダルが開く
- [ ] 案件ごとに時間を入力（0.25h刻み、最大24h/日）
- [ ] 入力後、カレンダーに合計時間が表示される

**Priority**: P0 (必須)

---

### US-002: 複数案件の同時管理
**As a** メンバー  
**I want to** 1日に複数の案件への稼働時間を記録したい  
**So that** 実際の作業配分を正確に反映できる

**Acceptance Criteria**:
- [ ] 1日に複数の案件を追加できる（上限なし）
- [ ] 各案件に0.25h刻みで時間を割り当てられる
- [ ] 合計時間が自動計算される
- [ ] 合計が24hを超えた場合は警告を表示

**Priority**: P0 (必須)

---

### US-003: 休暇入力
**As a** メンバー  
**I want to** 年休・有休・病欠などの休暇を入力したい  
**So that** 稼働時間とは別に休暇実績を記録できる

**Acceptance Criteria**:
- [ ] 休暇タイプを選択できる（年休/有休/病欠/その他）
- [ ] 0.25h刻みで入力可能（半休・時間休対応）
- [ ] 休暇と稼働は別エンティティで管理
- [ ] カレンダーで休暇日が視覚的に識別できる

**Priority**: P0 (必須)

---

### US-004: CSV一括入力/出力
**As a** メンバー  
**I want to** CSV形式で稼働時間をまとめて入力・出力したい  
**So that** Excel等での編集や他システムとの連携ができる

**Acceptance Criteria**:
- [ ] CSVテンプレートをダウンロードできる
- [ ] CSVファイルをアップロードして一括登録できる
- [ ] 現在の月度データをCSVでエクスポートできる
- [ ] バリデーションエラー時は詳細なエラーメッセージを表示

**Priority**: P1 (重要)

---

### US-005: 前月案件コピー
**As a** メンバー  
**I want to** 前月に入力した案件一覧をコピーしたい  
**So that** 毎月同じ案件への入力作業を省力化できる

**Acceptance Criteria**:
- [ ] ワンクリックで前月の案件リストをコピーできる
- [ ] 案件名のみコピー（時間はコピーしない）
- [ ] コピー後、個別に時間を入力できる

**Priority**: P1 (重要)

---

### US-006: 承認フロー
**As a** 承認者  
**I want to** メンバーの稼働時間を承認・差し戻ししたい  
**So that** 正確な稼働実績を確定できる

**Acceptance Criteria**:
- [ ] 承認対象の月度一覧を表示できる
- [ ] 月度単位で承認・差し戻しができる
- [ ] 差し戻し時にコメントを残せる（簡易版）
- [ ] 承認済みは編集不可（ステータス管理）

**Priority**: P0 (必須)

---

### US-007: マネージャー代理入力
**As a** マネージャー  
**I want to** 配下メンバーの稼働時間を代理入力したい  
**So that** 入力漏れを防止できる

**Acceptance Criteria**:
- [ ] 配下メンバーの一覧を表示できる
- [ ] メンバーを選択して代理入力モードに切り替え
- [ ] 代理入力したデータは入力者情報を記録
- [ ] 配下メンバー以外は代理入力不可（権限制御）

**Priority**: P1 (重要)

---

## 3. UI設計 (UI Design)

### 3.1 画面構成

**採用案: カレンダー + 案件サマリ統合型**

- PC・スマホ・タブレット完全共通UI（スマホファースト設計）
- カレンダーで月全体俯瞰 + 日付クリックでモーダル詳細入力
- 土曜=薄青、日曜=薄ピンク、祝日=オレンジ表示

### 3.2 ワイヤーフレーム (PC版)

```
┌──────────────────────────────────────────────────────────────────┐
│ Work-Log Entry                        [年度: 2026▼] [月度: 1▼]  │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │           2026年度 1月度 (12/21 - 1/20)                     │   │
│ │                                                              │   │
│ │  日   月   火   水   木   金   土                            │   │
│ │ ┌───┬───┬───┬───┬───┬───┬───┐                          │   │
│ │ │21 │22 │23 │24 │25 │26 │27 │  ← 前月                   │   │
│ │ │   │8h │8h │0h │8h │8h │   │                            │   │
│ │ └───┴───┴───┴───┴───┴───┴───┘                          │   │
│ │ ┌───┬───┬───┬───┬───┬───┬───┐                          │   │
│ │ │28 │29 │30 │31 │ 1 │ 2 │ 3 │  ← 当月開始               │   │
│ │ │   │8h │8h │8h │祝 │   │   │                            │   │
│ │ └───┴───┴───┴───┴───┴───┴───┘                          │   │
│ │ ┌───┬───┬───┬───┬───┬───┬───┐                          │   │
│ │ │ 4 │ 5 │ 6 │ 7 │ 8 │ 9 │10 │                            │   │
│ │ │   │8h │8h │8h │8h │8h │   │                            │   │
│ │ └───┴───┴───┴───┴───┴───┴───┘                          │   │
│ │ ┌───┬───┬───┬───┬───┬───┬───┐                          │   │
│ │ │11 │12 │13 │14 │15 │16 │17 │                            │   │
│ │ │祝 │   │8h │8h │8h │8h │8h │                            │   │
│ │ └───┴───┴───┴───┴───┴───┴───┘                          │   │
│ │ ┌───┬───┬───┬───┬───┬───┬───┐                          │   │
│ │ │18 │19 │20 │21 │22 │23 │24 │  ← 当月終了               │   │
│ │ │   │   │8h │   │   │   │   │                            │   │
│ │ └───┴───┴───┴───┴───┴───┴───┘                          │   │
│ │                                                              │   │
│ │  合計稼働: 160h / 予定: 160h (100%)                         │   │
│ └──────────────────────────────────────────────────────────┘   │
│                                                                    │
│ 案件サマリ                                                         │
│ ┌────────────────────────────────────────────────────────────┐   │
│ │ 案件名              │ 合計時間 │ 割合  │                    │   │
│ ├────────────────────┼──────────┼───────┤                    │   │
│ │ Project Alpha       │   80h    │ 50%   │                    │   │
│ │ Project Beta        │   60h    │ 37.5% │                    │   │
│ │ 内部業務            │   20h    │ 12.5% │                    │   │
│ └────────────────────────────────────────────────────────────┘   │
│                                                                    │
│ [前月案件コピー]  [CSV入力]  [CSV出力]  [承認申請]               │
└──────────────────────────────────────────────────────────────────┘
```

### 3.3 詳細入力モーダル

日付をクリックすると以下のモーダルが開く:

```
┌──────────────────────────────────────────┐
│ 稼働時間入力 - 2026/1/15 (水)      [×] │
├──────────────────────────────────────────┤
│                                            │
│ 案件                                       │
│ ┌──────────────────────────────────────┐ │
│ │ [Project Alpha ▼]        [6.5] 時間  │ │
│ │ [Project Beta  ▼]        [1.5] 時間  │ │
│ │ [+ 案件を追加]                        │ │
│ └──────────────────────────────────────┘ │
│                                            │
│ 休暇                                       │
│ ┌──────────────────────────────────────┐ │
│ │ タイプ: [選択なし ▼]                  │ │
│ │ 時間: [0] 時間                         │ │
│ └──────────────────────────────────────┘ │
│                                            │
│ コメント (任意)                            │
│ ┌──────────────────────────────────────┐ │
│ │                                        │ │
│ └──────────────────────────────────────┘ │
│                                            │
│ 合計: 8.0 時間                             │
│                                            │
│          [キャンセル]  [保存]              │
└──────────────────────────────────────────┘
```

### 3.4 スマホ版レイアウト

```
┌─────────────────────────┐
│ Work-Log Entry      ☰  │
├─────────────────────────┤
│ 年度: [2026 ▼]          │
│ 月度: [  1  ▼]          │
├─────────────────────────┤
│ 2026年度 1月度          │
│ (12/21 - 1/20)          │
│                         │
│ 日 月 火 水 木 金 土    │
│ 21 22 23 24 25 26 27    │
│    8h 8h 0h 8h 8h       │
│                         │
│ 28 29 30 31  1  2  3    │
│    8h 8h 8h 祝          │
│                         │
│  4  5  6  7  8  9 10    │
│    8h 8h 8h 8h 8h       │
│                         │
│ 11 12 13 14 15 16 17    │
│ 祝    8h 8h 8h 8h 8h    │
│                         │
│ 18 19 20 21 22 23 24    │
│       8h                │
│                         │
│ 合計: 160h / 160h       │
├─────────────────────────┤
│ 案件サマリ              │
│ Project Alpha   80h     │
│ Project Beta    60h     │
│ 内部業務        20h     │
├─────────────────────────┤
│ [前月コピー] [CSV]      │
│ [承認申請]              │
└─────────────────────────┘
```

---

## 4. バックエンド設計 (Backend Design)

### 4.1 新規ドメインエンティティ

#### 4.1.1 Project (案件)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | 案件ID |
| organizationId | UUID | FK(Organization) | 所属組織 |
| code | String | UNIQUE per org | 案件コード |
| name | String | NOT NULL | 案件名 |
| isActive | Boolean | DEFAULT true | 有効フラグ |
| createdAt | Instant | NOT NULL | 作成日時 |

**Events**:
- `ProjectCreated(projectId, organizationId, code, name)`
- `ProjectUpdated(projectId, name)`
- `ProjectDeactivated(projectId)`

---

#### 4.1.2 Member (メンバー)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | メンバーID |
| organizationId | UUID | FK(Organization) | 所属組織 |
| employeeNumber | String | UNIQUE | 社員番号 |
| name | String | NOT NULL | 氏名 |
| email | String | NOT NULL, UNIQUE | メールアドレス |
| managerId | UUID | FK(Member), NULL | 上司ID |
| roles | List<String> | NOT NULL | ロール（MEMBER/MANAGER/APPROVER） |
| isActive | Boolean | DEFAULT true | 有効フラグ |
| createdAt | Instant | NOT NULL | 作成日時 |

**Events**:
- `MemberCreated(memberId, organizationId, employeeNumber, name, email)`
- `MemberRolesUpdated(memberId, roles)`
- `MemberManagerAssigned(memberId, managerId)`
- `MemberDeactivated(memberId)`

**Roles**:
- `MEMBER`: 通常メンバー（自分の稼働入力のみ）
- `MANAGER`: マネージャー（配下メンバーの代理入力可能）
- `APPROVER`: 承認者（配下メンバーの承認可能）

---

#### 4.1.3 WorkLog (稼働時間)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | 稼働ログID |
| memberId | UUID | FK(Member) | メンバーID |
| projectId | UUID | FK(Project) | 案件ID |
| workDate | LocalDate | NOT NULL | 稼働日 |
| hours | BigDecimal | NOT NULL, >= 0.25 | 稼働時間（0.25刻み） |
| comment | String | NULL | コメント |
| inputBy | UUID | FK(Member) | 入力者（代理入力対応） |
| status | Enum | NOT NULL | ステータス（DRAFT/SUBMITTED/APPROVED） |
| createdAt | Instant | NOT NULL | 作成日時 |
| updatedAt | Instant | NOT NULL | 更新日時 |

**Events**:
- `WorkLogCreated(workLogId, memberId, projectId, workDate, hours)`
- `WorkLogUpdated(workLogId, hours, comment)`
- `WorkLogSubmitted(workLogId, submittedAt)`
- `WorkLogApproved(workLogId, approvedBy, approvedAt)`
- `WorkLogRejected(workLogId, rejectedBy, reason)`

**Constraints**:
- UNIQUE(memberId, projectId, workDate) - 1日1案件につき1レコード
- Daily total per member <= 24h (application-level validation)

---

#### 4.1.4 Absence (休暇)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | 休暇ID |
| memberId | UUID | FK(Member) | メンバーID |
| absenceDate | LocalDate | NOT NULL | 休暇日 |
| absenceType | Enum | NOT NULL | 休暇タイプ（PAID_LEAVE/SICK_LEAVE/OTHER） |
| hours | BigDecimal | NOT NULL, >= 0.25 | 休暇時間（0.25刻み） |
| comment | String | NULL | コメント |
| createdAt | Instant | NOT NULL | 作成日時 |

**Events**:
- `AbsenceCreated(absenceId, memberId, absenceDate, absenceType, hours)`
- `AbsenceUpdated(absenceId, hours, comment)`
- `AbsenceDeleted(absenceId)`

**Absence Types**:
- `PAID_LEAVE`: 年次有給休暇
- `SICK_LEAVE`: 病気休暇
- `SPECIAL_LEAVE`: 特別休暇
- `OTHER`: その他

---

#### 4.1.5 Holiday (祝日マスタ)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PK | 祝日ID |
| date | LocalDate | NOT NULL, UNIQUE | 祝日 |
| name | String | NOT NULL | 祝日名 |
| year | Integer | NOT NULL | 年度（検索最適化用） |
| createdAt | Instant | NOT NULL | 作成日時 |

**Note**: 祝日はバックエンドで管理し、APIで配信（年度単位）

---

### 4.2 データモデル (ER図)

```
┌─────────────┐
│   Tenant    │
└──────┬──────┘
       │ 1:N
       ▼
┌─────────────────┐
│  Organization   │ (既存)
│  - FY Pattern   │
│  - Month Pattern│
└──────┬──────────┘
       │ 1:N
       ├──────────────────┐
       │                  │
       ▼ 1:N              ▼ 1:N
┌─────────────┐    ┌────────────┐
│   Project   │    │   Member   │
│             │    │ - managerId│ (self-ref)
└──────┬──────┘    └──────┬─────┘
       │                  │
       │ N:1           N:1│
       │  ┌───────────────┘
       │  │
       ▼  ▼
┌─────────────────┐
│    WorkLog      │
│ - memberId      │
│ - projectId     │
│ - workDate      │
│ - hours         │
│ - status        │
└─────────────────┘

┌─────────────────┐
│    Absence      │
│ - memberId      │
│ - absenceDate   │
│ - absenceType   │
│ - hours         │
└─────────────────┘

┌─────────────────┐
│    Holiday      │
│ - date          │
│ - name          │
└─────────────────┘
```

---

### 4.3 API設計 (RESTful Endpoints)

#### 4.3.1 WorkLog APIs

**1. GET /api/worklogs**  
月度単位の稼働時間一覧取得

**Request**:
```
GET /api/worklogs?memberId={memberId}&fiscalYear={year}&monthlyPeriod={period}
```

**Response**:
```json
{
  "period": {
    "fiscalYear": 2026,
    "monthlyPeriod": 1,
    "startDate": "2025-12-21",
    "endDate": "2026-01-20"
  },
  "worklogs": [
    {
      "id": "uuid",
      "memberId": "uuid",
      "projectId": "uuid",
      "projectName": "Project Alpha",
      "workDate": "2026-01-15",
      "hours": 6.5,
      "comment": "Feature development",
      "status": "DRAFT"
    }
  ],
  "absences": [
    {
      "id": "uuid",
      "absenceDate": "2026-01-10",
      "absenceType": "PAID_LEAVE",
      "hours": 8.0
    }
  ],
  "holidays": [
    {
      "date": "2026-01-01",
      "name": "元日"
    }
  ],
  "summary": {
    "totalHours": 160,
    "expectedHours": 160,
    "completionRate": 100.0,
    "projectBreakdown": [
      {
        "projectId": "uuid",
        "projectName": "Project Alpha",
        "totalHours": 80,
        "percentage": 50.0
      }
    ]
  }
}
```

---

**2. POST /api/worklogs**  
稼働時間登録（日単位）

**Request**:
```json
{
  "memberId": "uuid",
  "workDate": "2026-01-15",
  "entries": [
    {
      "projectId": "uuid",
      "hours": 6.5,
      "comment": "Feature development"
    },
    {
      "projectId": "uuid",
      "hours": 1.5,
      "comment": "Code review"
    }
  ]
}
```

**Response**:
```json
{
  "success": true,
  "worklogIds": ["uuid1", "uuid2"]
}
```

**Validation**:
- Total hours per day <= 24h
- Hours in 0.25 increments
- Projects must exist and be active

---

**3. PUT /api/worklogs/{id}**  
稼働時間更新

**Request**:
```json
{
  "hours": 7.0,
  "comment": "Updated hours"
}
```

---

**4. DELETE /api/worklogs/{id}**  
稼働時間削除

---

**5. POST /api/worklogs/bulk-import**  
CSV一括インポート

**Request**:
```
Content-Type: multipart/form-data
file: <CSV file>
```

**CSV Format**:
```csv
workDate,projectCode,hours,comment
2026-01-15,PROJ-001,6.5,Feature development
2026-01-15,PROJ-002,1.5,Code review
2026-01-16,PROJ-001,8.0,Testing
```

**Response**:
```json
{
  "success": true,
  "imported": 3,
  "errors": [
    {
      "row": 5,
      "message": "Invalid project code: PROJ-999"
    }
  ]
}
```

---

**6. GET /api/worklogs/export**  
CSV一括エクスポート

**Request**:
```
GET /api/worklogs/export?memberId={memberId}&fiscalYear={year}&monthlyPeriod={period}
```

**Response**:
```
Content-Type: text/csv
Content-Disposition: attachment; filename="worklogs_2026_01.csv"

workDate,projectCode,projectName,hours,comment
2026-01-15,PROJ-001,Project Alpha,6.5,Feature development
...
```

---

**7. POST /api/worklogs/copy-from-previous-month**  
前月案件コピー

**Request**:
```json
{
  "memberId": "uuid",
  "targetFiscalYear": 2026,
  "targetMonthlyPeriod": 2
}
```

**Response**:
```json
{
  "success": true,
  "copiedProjects": [
    {
      "projectId": "uuid",
      "projectName": "Project Alpha"
    },
    {
      "projectId": "uuid",
      "projectName": "Project Beta"
    }
  ]
}
```

**Logic**:
- Copy project list from previous month (hours = 0)
- User fills in hours manually

---

**8. POST /api/worklogs/submit**  
承認申請

**Request**:
```json
{
  "memberId": "uuid",
  "fiscalYear": 2026,
  "monthlyPeriod": 1
}
```

**Response**:
```json
{
  "success": true,
  "status": "SUBMITTED",
  "submittedAt": "2026-01-21T10:00:00Z"
}
```

---

**9. POST /api/worklogs/approve**  
承認・差し戻し

**Request**:
```json
{
  "memberId": "uuid",
  "fiscalYear": 2026,
  "monthlyPeriod": 1,
  "action": "APPROVE",
  "comment": "Approved"
}
```

**Actions**: `APPROVE` | `REJECT`

**Response**:
```json
{
  "success": true,
  "status": "APPROVED",
  "approvedBy": "uuid",
  "approvedAt": "2026-01-22T14:00:00Z"
}
```

---

#### 4.3.2 Absence APIs

**1. POST /api/absences**  
休暇登録

**Request**:
```json
{
  "memberId": "uuid",
  "absenceDate": "2026-01-10",
  "absenceType": "PAID_LEAVE",
  "hours": 8.0,
  "comment": "Annual leave"
}
```

---

**2. PUT /api/absences/{id}**  
休暇更新

---

**3. DELETE /api/absences/{id}**  
休暇削除

---

#### 4.3.3 Holiday APIs

**1. GET /api/holidays**  
祝日一覧取得（年度単位）

**Request**:
```
GET /api/holidays?year=2026
```

**Response**:
```json
{
  "year": 2026,
  "holidays": [
    {
      "date": "2026-01-01",
      "name": "元日"
    },
    {
      "date": "2026-01-12",
      "name": "成人の日"
    }
  ]
}
```

---

**2. POST /api/holidays**  
祝日登録（管理者のみ）

---

#### 4.3.4 Project APIs

**1. GET /api/projects**  
案件一覧取得（組織単位）

**Request**:
```
GET /api/projects?organizationId={organizationId}&isActive=true
```

**Response**:
```json
{
  "projects": [
    {
      "id": "uuid",
      "code": "PROJ-001",
      "name": "Project Alpha",
      "isActive": true
    }
  ]
}
```

---

**2. POST /api/projects**  
案件登録

---

**3. PUT /api/projects/{id}**  
案件更新

---

**4. DELETE /api/projects/{id}**  
案件削除（非アクティブ化）

---

#### 4.3.5 Member APIs

**1. GET /api/members**  
メンバー一覧取得

---

**2. GET /api/members/{id}/subordinates**  
配下メンバー一覧取得（代理入力用）

**Response**:
```json
{
  "subordinates": [
    {
      "id": "uuid",
      "name": "John Doe",
      "employeeNumber": "E001"
    }
  ]
}
```

---

**3. POST /api/members**  
メンバー登録

---

**4. PUT /api/members/{id}**  
メンバー更新

---

### 4.4 権限制御 (Authorization)

| Role | 自分の稼働入力 | 配下メンバー代理入力 | 承認 |
|------|--------------|------------------|------|
| MEMBER | ✅ | ❌ | ❌ |
| MANAGER | ✅ | ✅ (配下のみ) | ❌ |
| APPROVER | ✅ | ✅ (配下のみ) | ✅ (配下のみ) |

**デフォルト付与**:
- 組織管理者（Organization Admin）: MANAGER + APPROVER
- サブ組織管理者: APPROVER

---

## 5. 技術スタック (Tech Stack)

### 5.1 フロントエンド

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| Framework | Next.js | 15.1.1 | React framework |
| UI Library | React | 19.x | Component library |
| State Management | **Zustand** | 5.x | Lightweight state management |
| Calendar UI | **react-day-picker** | 9.x | Calendar component |
| Table UI | **TanStack Table** | 8.x | Headless table (CSV export) |
| Date Utils | **date-fns** | 4.x | Date calculation |
| CSV Parser | **papaparse** | 5.x | CSV import/export |
| Validation | **zod** | 3.x | Schema validation |
| UI Components | **shadcn/ui** | latest | Radix UI + Tailwind |
| HTTP Client | Fetch API | - | Native |

**選定理由**:
- **Zustand**: Redux より軽量、React Context より高性能
- **react-day-picker**: 8kB、カスタマイズ性高、モバイル対応
- **TanStack Table**: ヘッドレスUI、仮想スクロール、CSV対応
- **date-fns**: モダン、Tree-shakable、immutable

---

### 5.2 バックエンド

| Category | Technology | Version |
|----------|------------|---------|
| Framework | Spring Boot | 3.5.9 |
| Language | Kotlin | 2.3.0 |
| Java Version | Java 21 | - |
| Database | PostgreSQL | 17.x |
| Migration | Flyway | 10.x |
| Testing | JUnit 5 + Testcontainers | - |
| Event Store | Custom (JSONB) | - |

---

## 6. データベース設計 (Database Schema)

### 6.1 Flyway Migration

**V4__work_log_entry_tables.sql**

```sql
-- Project
CREATE TABLE project (
    id                  UUID        PRIMARY KEY,
    organization_id     UUID        NOT NULL REFERENCES organization(id),
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, code)
);
CREATE INDEX idx_project_org ON project(organization_id);
CREATE INDEX idx_project_active ON project(is_active);

-- Member
CREATE TABLE member (
    id                  UUID        PRIMARY KEY,
    organization_id     UUID        NOT NULL REFERENCES organization(id),
    employee_number     VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    email               VARCHAR(200) NOT NULL UNIQUE,
    manager_id          UUID        REFERENCES member(id),
    roles               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_member_org ON member(organization_id);
CREATE INDEX idx_member_manager ON member(manager_id);
CREATE INDEX idx_member_active ON member(is_active);

-- WorkLog
CREATE TABLE work_log (
    id                  UUID        PRIMARY KEY,
    member_id           UUID        NOT NULL REFERENCES member(id),
    project_id          UUID        NOT NULL REFERENCES project(id),
    work_date           DATE        NOT NULL,
    hours               DECIMAL(4,2) NOT NULL CHECK (hours >= 0.25 AND hours <= 24),
    comment             TEXT,
    input_by            UUID        NOT NULL REFERENCES member(id),
    status              VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, project_id, work_date)
);
CREATE INDEX idx_worklog_member_date ON work_log(member_id, work_date);
CREATE INDEX idx_worklog_project ON work_log(project_id);
CREATE INDEX idx_worklog_status ON work_log(status);

-- Absence
CREATE TABLE absence (
    id                  UUID        PRIMARY KEY,
    member_id           UUID        NOT NULL REFERENCES member(id),
    absence_date        DATE        NOT NULL,
    absence_type        VARCHAR(20) NOT NULL CHECK (absence_type IN ('PAID_LEAVE', 'SICK_LEAVE', 'SPECIAL_LEAVE', 'OTHER')),
    hours               DECIMAL(4,2) NOT NULL CHECK (hours >= 0.25 AND hours <= 24),
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, absence_date)
);
CREATE INDEX idx_absence_member_date ON absence(member_id, absence_date);

-- Holiday
CREATE TABLE holiday (
    id                  UUID        PRIMARY KEY,
    date                DATE        NOT NULL UNIQUE,
    name                VARCHAR(100) NOT NULL,
    year                INTEGER     NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_holiday_date ON holiday(date);
CREATE INDEX idx_holiday_year ON holiday(year);
```

---

### 6.2 インデックス戦略

**高頻度クエリ**:
1. 月度単位の稼働取得: `(member_id, work_date)` - Composite Index
2. 案件別集計: `project_id` - Single Index
3. 承認対象取得: `status` - Single Index
4. 祝日取得: `year` - Single Index

**パフォーマンス目標**:
- 月度データ取得: < 100ms (p95)
- CSV出力: < 500ms (1ヶ月分)
- 一括インポート: < 1秒/100件

---

## 7. 実装計画 (Implementation Plan)

### Phase 1: バックエンド - ドメインモデル (3-4日)

**タスク**:
- [ ] T001: Project エンティティ + イベント実装
- [ ] T002: Member エンティティ + イベント実装
- [ ] T003: WorkLog エンティティ + イベント実装
- [ ] T004: Absence エンティティ + イベント実装
- [ ] T005: Holiday エンティティ実装
- [ ] T006-T010: リポジトリ実装（Event Store + Projection）
- [ ] T011-T013: Flyway マイグレーション (V4__work_log_entry_tables.sql)

**成果物**:
- ドメインモデル（5エンティティ）
- イベント定義（15イベント）
- リポジトリ層（5クラス）
- DBマイグレーション（1ファイル）

---

### Phase 2: バックエンド - API実装 (4-5日)

**タスク**:
- [ ] T014-T020: RESTful API実装（9エンドポイント）
- [ ] T021-T025: CSV インポート/エクスポート実装
- [ ] T026-T030: 承認フロー実装
- [ ] T031-T035: 単体テスト・統合テスト
- [ ] T036-T040: API ドキュメント（OpenAPI）

**成果物**:
- RESTful API（全エンドポイント）
- CSV処理機能
- 承認フロー機能
- テストカバレッジ 85%以上

---

### Phase 3: フロントエンド - 基盤 (5-6日)

**タスク**:
- [ ] T041-T045: ライブラリセットアップ（Zustand, react-day-picker等）
- [ ] T046-T050: ダッシュボード画面（年度・月度選択）
- [ ] T051-T055: カレンダーコンポーネント実装
- [ ] T056-T060: 案件サマリコンポーネント実装
- [ ] T061-T065: 祝日表示・土日色分け実装

**成果物**:
- カレンダーUI（レスポンシブ）
- 案件サマリ表示
- 祝日・土日表示

---

### Phase 4: フロントエンド - 機能実装 (4-5日)

**タスク**:
- [ ] T066-T070: 詳細入力モーダル実装
- [ ] T071-T075: CSV一括入力/出力実装
- [ ] T076-T080: 前月案件コピー実装
- [ ] T081-T085: 承認機能実装
- [ ] T086-T090: 代理入力機能実装

**成果物**:
- 全機能の実装完了
- レスポンシブUI完成

---

### Phase 5: テスト・品質保証 (3-4日)

**タスク**:
- [ ] T091-T095: E2Eテスト（Playwright）
- [ ] T096-T100: パフォーマンステスト
- [ ] T101-T105: アクセシビリティチェック
- [ ] T106-T110: ブラウザ互換性テスト

**成果物**:
- E2Eテストスイート
- パフォーマンスレポート
- 品質保証完了

---

### Phase 6: ドキュメント・デプロイ準備 (3日)

**タスク**:
- [ ] T111-T115: ユーザーマニュアル作成
- [ ] T116-T120: API仕様書完成（OpenAPI）
- [ ] T121-T125: Docker設定更新
- [ ] T126-T130: リリースノート作成

**成果物**:
- ユーザーマニュアル
- API仕様書
- デプロイ準備完了

---

### 総実装期間

**合計: 22-27営業日（4.5-5.5週間）**

---

## 8. リスク・制約 (Risks & Constraints)

### 8.1 技術リスク

| Risk | Impact | Mitigation |
|------|--------|------------|
| CSV大容量（10,000行+）でタイムアウト | High | バッチ処理（1,000件ずつ） |
| react-day-pickerのカスタマイズ限界 | Medium | shadcn/ui Calendar へのフォールバック |
| 月度またぎのデータ不整合 | High | Transaction管理の徹底 |
| モバイルでのカレンダー操作性 | Medium | タッチ最適化、スワイプ対応 |

---

### 8.2 制約条件

**Phase 1 制約**:
- 通知機能なし（メール/Slack）
- ダッシュボード・分析画面なし
- Excel/PDF出力なし（CSVのみ）
- 承認履歴・詳細コメント機能は簡易版

**パフォーマンス制約**:
- API応答時間 < 100ms (p95) - FR-SC-001に準拠
- CSV出力 < 500ms (1ヶ月分)
- 画面初期表示 < 1秒

---

## 9. 受け入れ基準 (Acceptance Criteria)

### 9.1 機能要件

- [ ] US-001〜US-007 の全 Acceptance Criteria を満たす
- [ ] すべてのAPIが正常動作する
- [ ] CSVインポート/エクスポートが動作する
- [ ] 承認フローが動作する
- [ ] 代理入力機能が動作する

### 9.2 非機能要件

- [ ] テストカバレッジ 85%以上
- [ ] API応答時間 < 100ms (p95)
- [ ] E2Eテスト成功率 100%
- [ ] モバイル・タブレット対応完了
- [ ] ブラウザ互換性（Chrome/Safari/Edge最新版）

### 9.3 ドキュメント

- [ ] ユーザーマニュアル完成
- [ ] API仕様書（OpenAPI）完成
- [ ] ER図・アーキテクチャ図完成

---

## 10. 次のステップ (Next Steps)

### 10.1 即座に実行

1. **このファイル（proposal.md）をコミット**
   ```bash
   git add specs/002-work-log-entry/
   git commit -m "docs: Add work-log entry feature proposal (Feature 002)"
   ```

2. **tasks.md の作成**
   - Phase 1-6 を詳細タスクに分解（70-80タスク）
   - 優先順位付け、依存関係整理

### 10.2 実装開始

3. **Phase 1 開始: バックエンド - ドメインモデル**
   - T001: Project エンティティ実装
   - T002: Member エンティティ実装
   - ...

---

**作成日**: 2026-01-02  
**最終更新**: 2026-01-02  
**ステータス**: Draft → Ready for Implementation
