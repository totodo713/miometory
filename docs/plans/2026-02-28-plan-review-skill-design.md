# Plan Review: Hook → Skill + Command Migration

## Background

`require-plan-review.sh` PostToolUse hook は `docs/plan/` 配下へのファイル書き込みを検知し、3つのレビューエージェント並列実行を強制する仕組みだった。しかし以下の問題があった:

- すべての Write/Edit 操作でJSON解析が実行される不要なオーバーヘッド
- hookの適用条件が明確でなく、期待した効果が出なかった

## Design

### Approach: Skill + Command Hybrid

| Component | Path | Role |
|-----------|------|------|
| Skill | `.claude/skills/review-plan/SKILL.md` | レビューロジック本体。自動トリガー対応 |
| Command | `.claude/commands/review-plan.md` | `/review-plan` コマンド。手動実行インターフェース |

### Flow

```
[Auto] Plan file detected → Claude suggests review-plan skill
[Manual] /review-plan docs/plan/feature-x.md
         ↓
  SKILL.md: Parse target, invoke 3 agents in parallel
         ↓
  chief-product-officer | security-reviewer | ux-design-advisor
         ↓
  Synthesize: ALL APPROVED → proceed | ANY REJECTED → revise
```

### Skill Design

- `disable-model-invocation` は設定しない（デフォルトで自動トリガー可能）
- description に `docs/plan/` ファイル編集時のトリガー条件を明記
- `$ARGUMENTS` でファイルパスを受け取る。省略時は `docs/plan/` 全体を対象
- 3エージェントを Task tool で並列実行
- 結果統合: 全 APPROVED で実装許可、1つでも REJECTED なら修正指示

### Command Design

- `.claude/commands/review-plan.md` は薄いラッパー
- `$ARGUMENTS` をスキルに渡すだけ

### Cleanup

1. `.claude/hooks/require-plan-review.sh` を削除
2. `.claude/settings.json` の PostToolUse から該当 hook エントリを削除
3. `CLAUDE.md` の Plan Review Workflow セクションを更新
4. `AGENTS.md` の関連記述があれば更新

### Trade-offs

- **hookの強制力がなくなる**: スキルは提案的。Claudeが文脈判断でスキップする可能性あり
- **Write/Edit のオーバーヘッドがゼロに**: plan以外のファイル編集時の無駄がなくなる
- **手動実行の柔軟性**: `/review-plan` でユーザーの任意タイミングで実行可能
