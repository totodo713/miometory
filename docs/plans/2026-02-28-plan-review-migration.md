# Plan Review Hook → Skill + Command Migration

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** `require-plan-review.sh` PostToolUse hook を削除し、スキル + スラッシュコマンドのハイブリッドに置き換える

**Architecture:** スキル（`.claude/skills/review-plan/SKILL.md`）にレビューロジックを集約し、コマンド（`.claude/commands/review-plan.md`）を手動実行インターフェースとする。hookの自動強制ではなく、スキルのdescriptionベースの自動提案 + `/review-plan` 手動実行の二本立て。

**Tech Stack:** Claude Code Skills, Claude Code Commands, Shell (cleanup)

---

### Task 1: Create review-plan skill

**Files:**
- Create: `.claude/skills/review-plan/SKILL.md`

**Step 1: Create the skill directory**

```bash
mkdir -p .claude/skills/review-plan
```

**Step 2: Write SKILL.md**

Create `.claude/skills/review-plan/SKILL.md` with:

```markdown
---
name: review-plan
description: >
  docs/plan/ 配下のplanファイルが作成・編集された後に、multi-agent plan review を実行する。
  planファイルの書き込みや、plan作成ワークフローの完了を検知したら、このスキルを使ってレビューを実施する。
  手動で /review-plan コマンドから実行することもできる。
---

# Plan Review

docs/plan/ 配下のplanファイルに対して、3つの専門エージェントによる並列レビューを実施する。

## Target

`$ARGUMENTS` にファイルパスが指定されている場合はそのファイルをレビュー対象とする。
指定がない場合は、現在のコンテキストにあるplanファイル、または `docs/plan/` 配下の最新ファイルを対象とする。

## Review Process

以下の3つのレビューエージェントを **Task tool で並列に** 起動する:

1. **chief-product-officer**: planファイルと AGENTS.md を読み、product feasibility、spec alignment、feature completeness を評価。最後に APPROVED または REJECTED（理由付き）を出力。
2. **security-reviewer**: planファイルと AGENTS.md を読み、security risks（authentication, authorization, tenant isolation, injection, data exposure）を評価。最後に APPROVED または REJECTED（理由付き）を出力。
3. **ux-design-advisor**: planファイルと AGENTS.md を読み、UX quality（user flow, accessibility, information architecture, responsive design）を評価。最後に APPROVED または REJECTED（理由付き）を出力。

各エージェントに渡すプロンプトには、レビュー対象のplanファイルパスを含めること。

## Result Synthesis

3つのエージェントの結果を統合する:

- **全員 APPROVED** → 「PLAN APPROVED」と宣言し、実装に進んで良い
- **1つでも REJECTED** → 「PLAN REJECTED」と宣言し、REJECTEDの理由を一覧表示。planを修正した後、REJECTEDだったレビューアーのみ再実行する

## Important

- 実装コード（source files）への Write/Edit は、全レビューアーが APPROVE するまで行わないこと
- レビュー結果はユーザーに明示的に報告すること
```

**Step 3: Verify the skill file exists**

```bash
cat .claude/skills/review-plan/SKILL.md | head -5
```

Expected: frontmatter with `name: review-plan`

**Step 4: Commit**

```bash
git add .claude/skills/review-plan/SKILL.md
git commit -m "feat: add review-plan skill for multi-agent plan review"
```

---

### Task 2: Create review-plan command

**Files:**
- Create: `.claude/commands/review-plan.md`

**Step 1: Write the command file**

Create `.claude/commands/review-plan.md` with:

```markdown
---
description: Plan files multi-agent review (CPO + Security + UX)
---

## User Input

\`\`\`text
$ARGUMENTS
\`\`\`

## Outline

Invoke the `review-plan` skill to perform multi-agent review on plan files.

### Step 1: Determine review target

From `$ARGUMENTS`:

| Pattern | Example | Action |
|---------|---------|--------|
| File path specified | `/review-plan docs/plan/feature-x.md` | Review that specific file |
| No arguments | `/review-plan` | Review the most recently modified file in `docs/plan/`, or plan files in current context |

### Step 2: Execute review

Invoke the `review-plan` skill with the determined target file path as argument.

### Step 3: Report results

Display the synthesized review results:
- List each reviewer's verdict (APPROVED / REJECTED)
- If any REJECTED, list the specific issues
- State whether implementation may proceed
```

**Step 2: Verify**

```bash
head -3 .claude/commands/review-plan.md
```

Expected: frontmatter with `description:`

**Step 3: Commit**

```bash
git add .claude/commands/review-plan.md
git commit -m "feat: add /review-plan slash command"
```

---

### Task 3: Remove hook from settings.json

**Files:**
- Modify: `.claude/settings.json` (PostToolUse section, lines ~178-182)

**Step 1: Remove the hook entry**

In `.claude/settings.json`, remove the `require-plan-review.sh` hook object from the PostToolUse `Write|Edit` matcher's hooks array:

```json
{
  "type": "command",
  "command": ".claude/hooks/require-plan-review.sh",
  "timeout": 5
}
```

This is the last entry in the PostToolUse `Write|Edit` hooks array (after `typecheck-on-edit.sh`).

**Step 2: Verify settings.json is valid JSON**

```bash
python3 -c "import json; json.load(open('.claude/settings.json'))"
```

Expected: no output (valid JSON)

**Step 3: Commit**

```bash
git add .claude/settings.json
git commit -m "chore: remove require-plan-review hook from settings.json"
```

---

### Task 4: Delete the hook file

**Files:**
- Delete: `.claude/hooks/require-plan-review.sh`

**Step 1: Delete the file**

```bash
rm .claude/hooks/require-plan-review.sh
```

**Step 2: Verify deletion**

```bash
ls .claude/hooks/require-plan-review.sh 2>&1
```

Expected: `No such file or directory`

**Step 3: Commit**

```bash
git add -u .claude/hooks/require-plan-review.sh
git commit -m "chore: delete require-plan-review.sh hook script"
```

---

### Task 5: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (lines 11, 16-28)

**Step 1: Remove hook from Hooks section**

In the `## Hooks` section, remove the line:
```
- `require-plan-review.sh` (PostToolUse): Detects `docs/plan/` 配下へのファイル書き込みを検知し、multi-agent review を強制 (CPO + security + UX)
```

**Step 2: Rewrite Plan Review Workflow section**

Replace the entire `## Plan Review Workflow (MANDATORY)` section with:

```markdown
## Plan Review Workflow

`docs/plan/` 配下のplanファイルのレビューには `/review-plan` コマンド（またはreview-planスキル）を使用する:

1. `/review-plan docs/plan/feature-x.md` でレビュー対象を指定して実行（引数省略で最新ファイルを対象）
2. 3つのレビューエージェントが **並列** で起動される:
   - `chief-product-officer` — product feasibility, spec alignment, feature completeness
   - `security-reviewer` — security risks, auth/authz, tenant isolation, injection
   - `ux-design-advisor` — UX quality, accessibility, user flow, responsive design
3. 全員 APPROVED → 実装に進んで良い。1つでも REJECTED → plan修正後、REJECTEDのレビューアーのみ再実行
4. planファイル編集時にreview-planスキルが自動提案されることもある
```

**Step 3: Verify CLAUDE.md structure**

```bash
grep -n "## " CLAUDE.md
```

Expected: sections are intact and no broken formatting

**Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for plan review skill migration"
```

---

### Task 6: Verify the full migration

**Step 1: Confirm hook is gone**

```bash
ls .claude/hooks/ | grep require
```

Expected: no output

**Step 2: Confirm settings.json has no reference**

```bash
grep -c "require-plan-review" .claude/settings.json
```

Expected: `0`

**Step 3: Confirm skill exists**

```bash
cat .claude/skills/review-plan/SKILL.md | head -5
```

Expected: frontmatter with `name: review-plan`

**Step 4: Confirm command exists**

```bash
cat .claude/commands/review-plan.md | head -3
```

Expected: frontmatter with `description:`

**Step 5: Confirm CLAUDE.md is updated**

```bash
grep "require-plan-review.sh" CLAUDE.md
```

Expected: no output (reference removed)
