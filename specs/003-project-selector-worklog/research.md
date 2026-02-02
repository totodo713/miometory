# Research: 工数入力画面のプロジェクト選択機能

**Feature**: 003-project-selector-worklog  
**Date**: 2025-01-20

## Research Questions

### 1. プロジェクトアサインメントのデータモデル

**Question**: メンバーとプロジェクトの関連をどのように管理するか？

**Decision**: 新規テーブル `member_project_assignments` を作成

**Rationale**: 
- 現在のシステムにはメンバー・プロジェクト間の直接的な関連テーブルが存在しない
- 過去のWorkLogエントリから推測する方法は、新規ユーザーやプロジェクト開始時に対応できない
- 明示的なアサインメント管理により、管理者がプロジェクトへのアクセス権を制御可能
- DDDのベストプラクティスに従い、独立した集約として管理

**Alternatives considered**:
1. **WorkLogエントリから推測**: 実装は簡単だが、新規ユーザー・新規プロジェクトに対応不可
2. **Projectに直接member_idsを持たせる**: スケーラビリティの問題、プロジェクトの集約境界違反
3. **外部システム連携**: 追加の複雑さ、現時点では不要

---

### 2. APIエンドポイント設計

**Question**: プロジェクト一覧取得のAPIエンドポイントをどのように設計するか？

**Decision**: `GET /api/v1/members/{memberId}/projects` - メンバーにアサインされたプロジェクト一覧取得

**Rationale**:
- RESTfulなリソース設計（メンバーのサブリソースとしてプロジェクト）
- 既存の`MemberController`パターンに一貫性がある
- `?activeOnly=true`パラメータでアクティブなプロジェクトのみフィルタリング可能
- Spring Securityと連携して、ログインユーザーのみが自分のプロジェクトを取得可能

**Alternatives considered**:
1. `GET /api/v1/projects?memberId=xxx`: グローバルプロジェクトリソースへのフィルタリング。メンバー中心の操作には不自然
2. `GET /api/v1/worklog/projects/assigned`: 既存のProjectControllerに追加。パス構造が不整合

---

### 3. フロントエンドコンポーネント設計

**Question**: プロジェクト選択UIをどのように実装するか？

**Decision**: 検索可能なドロップダウン（Combobox）コンポーネント

**Rationale**:
- 多数のプロジェクト（20件以上）がある場合でも使いやすい
- キーボードナビゲーション対応でアクセシビリティ確保
- 既存のDailyEntryFormのスタイルに合わせたカスタムコンポーネント
- プロジェクト名とコードの両方で検索可能

**Alternatives considered**:
1. **シンプルなselect要素**: 検索機能がなく、多数のプロジェクトで使いにくい
2. **オートコンプリート**: ドロップダウンよりも一覧性が低い
3. **モーダルピッカー**: 追加のクリックが必要で、フローが複雑

---

### 4. キャッシュ戦略

**Question**: プロジェクト一覧のパフォーマンスをどう確保するか？

**Decision**: クライアントサイドでのセッション中キャッシュ + サーバーサイドでのRedisキャッシュ

**Rationale**:
- プロジェクトアサインメントは頻繁に変更されない
- 2秒以内のレスポンス目標（SC-004）を達成
- 既存のRedisインフラを活用（CacheConfig.kt参照）
- フォーム開閉時に毎回APIコールを避ける

**Alternatives considered**:
1. **キャッシュなし**: パフォーマンス目標達成が困難
2. **LocalStorageキャッシュ**: 永続化によるstale data問題

---

### 5. 既存エントリの編集時の動作

**Question**: 既存の工数エントリを編集する際、プロジェクト選択はどうするか？

**Decision**: 既存エントリのプロジェクトは読み取り専用で表示、変更不可

**Rationale**:
- 現在の動作を維持（FR-008）
- プロジェクト変更は監査・承認フローに影響
- ユーザーは削除→再作成で対応可能
- UI上は選択ドロップダウンをdisabled状態で表示

**Alternatives considered**:
1. **プロジェクト変更を許可**: 監査証跡の複雑化、承認済みエントリの問題
2. **編集時は非表示**: 一貫性のないUI

---

## Technology Decisions Summary

| Area | Decision | Rationale |
|------|----------|-----------|
| Data Model | 新規テーブル `member_project_assignments` | 明示的なアサイン管理、新規ユーザー対応 |
| API Endpoint | `GET /api/v1/members/{memberId}/projects` | RESTful設計、既存パターンとの一貫性 |
| UI Component | 検索可能Combobox | 多数プロジェクト対応、アクセシビリティ |
| Caching | クライアント + Redis | パフォーマンス目標達成 |
| Existing Entries | 読み取り専用 | 現行動作維持、監査要件 |

## Open Questions (Resolved)

すべての重要な技術的決定が解決されました。Phase 1に進む準備ができています。
