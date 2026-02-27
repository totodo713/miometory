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
