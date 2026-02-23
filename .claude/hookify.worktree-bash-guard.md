---
name: worktree-bash-guard
enabled: false
event: bash
action: block
conditions:
  - field: command
    operator: regex_match
    pattern: ^(?!(git\b|gh\b))
---

Worktreeでのbashコマンド実行をブロックしました。

許可されているコマンド: git, gh（/git-deliver用）
ビルド・テスト・サーバー起動はdevcontainerで行ってください。

このルールを無効化するには:
  /hookify configure → worktree-bash-guard を無効化
