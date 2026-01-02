# 用語集 (Glossary) - Feature 002: Work-Log Entry System

**Last Updated**: 2026-01-02  
**Purpose**: Define canonical terminology for code, specifications, and documentation

---

## 正規用語 (Canonical Terms)

### Core Domain Entities

- **WorkLogEntry** (正規名)
  - **日本語**: 稼働時間エントリ
  - **English**: Work log entry / time entry
  - **Usage**: Domain entity name, code identifiers, database tables
  - **Context**: Represents hours worked by a person on a specific project on a specific date
  - **Replaces**: "Time Entry" (deprecated in technical documentation)

- **Absence** (正規名)
  - **日本語**: 休暇エントリ / 欠勤記録
  - **English**: Absence entry
  - **Usage**: Domain entity for non-working time (vacation, sick leave, etc.)
  - **Context**: Separate from WorkLogEntry, tracked independently

- **Project** (正規名)
  - **日本語**: 案件 / プロジェクト
  - **English**: Project
  - **Usage**: Billable or trackable work initiative that time can be charged against
  - **Context**: Has unique code and name

- **Member** (正規名)
  - **日本語**: メンバー / 組織メンバー
  - **English**: Organization member
  - **Usage**: Person who enters time, including their reporting structure
  - **Context**: Formerly "Organization Member" in long form

- **FiscalMonthPeriod** (正規名)
  - **日本語**: 会計月度期間
  - **English**: Fiscal month period
  - **Usage**: Organization's definition of monthly periods (e.g., 21st to 20th)
  - **Context**: May not align with calendar months

- **MonthlyApproval** (正規名)
  - **日本語**: 月次承認
  - **English**: Monthly approval
  - **Usage**: Approval workflow for submitted time entries
  - **Context**: Status progression: Draft → Submitted → Approved/Rejected

---

## Status Values (稼働エントリのステータス)

- **Draft** (下書き): Initial state, editable by owner
- **Submitted** (提出済み): Awaiting approval, read-only to submitter
- **Approved** (承認済み): Approved by manager, permanently read-only
- **Rejected** (差戻し): Rejected with feedback, returned to Draft state

---

## Absence Types (休暇種別)

- **Paid Leave** (有給休暇): `PAID_LEAVE`
- **Sick Leave** (病欠): `SICK_LEAVE`
- **Special Leave** (特別休暇): `SPECIAL_LEAVE`
- **Other** (その他): `OTHER`

---

## User Roles (ユーザーロール)

- **Member** (メンバー): Regular user who enters their own time
- **Manager** (マネージャー): Can approve direct reports' time + proxy entry
- **Approver** (承認者): Designated person with approval authority
- **Admin** (管理者): System administrator with full access

---

## Technical Terms

### Frontend

- **Calendar View** (カレンダービュー): Monthly calendar display showing fiscal periods
- **Entry Form** (入力フォーム): Detailed form for entering time for a specific date
- **Auto-save** (自動保存): Draft entries saved every 60 seconds
- **Session Timeout** (セッションタイムアウト): 30-minute idle timeout

### Backend

- **Event Sourcing** (イベントソーシング): Event-driven architecture pattern
- **Aggregate** (集約): Domain-driven design aggregate root
- **Command** (コマンド): Write operation (e.g., CreateWorkLogEntry)
- **Query** (クエリ): Read operation (e.g., GetMonthlyEntries)
- **Projection** (プロジェクション): Read model for queries

### Integration

- **OAuth2**: MUST-support authentication protocol (RFC 6749)
- **SAML2**: SHOULD-support authentication protocol (optional)
- **SSO**: Single Sign-On (organizational identity provider)

---

## Usage Guidelines

### In Code
- **Class/Entity names**: Use PascalCase canonical terms (e.g., `WorkLogEntry`, `FiscalMonthPeriod`)
- **Database tables**: Use snake_case (e.g., `work_log_entries`, `fiscal_month_periods`)
- **API endpoints**: Use kebab-case (e.g., `/api/work-log-entries`, `/api/monthly-approvals`)

### In Documentation
- **Specifications**: Use canonical English terms (e.g., "WorkLogEntry")
- **User stories**: Use natural language (e.g., "daily time entry" is acceptable)
- **Requirements**: Use canonical terms for precision (e.g., "FR-001: WorkLogEntry must...")

### In Japanese Tasks
- **tasks.md**: Use Japanese natural language (e.g., "稼働時間エントリ", "休暇記録")
- **Code comments**: Use English canonical terms even in Japanese files

---

## Deprecated Terms (使用禁止)

❌ **"Time Entry"** → ✅ Use **"WorkLogEntry"** in technical contexts  
❌ **"Time Log"** → ✅ Use **"WorkLogEntry"** or **"Work Log Entry"**  
❌ **"Hour Entry"** → ✅ Use **"WorkLogEntry"**  
❌ **"Organization Member"** → ✅ Use **"Member"** (short form acceptable)

---

## References

- **Specification**: `specs/002-work-log-entry/spec.md`
- **Plan**: `specs/002-work-log-entry/plan.md`
- **Tasks**: `specs/002-work-log-entry/tasks.md`
- **Constitution**: `.specify/memory/constitution.md`

---

*This glossary is a living document. Update when new domain terms are introduced or existing terms are refined.*
