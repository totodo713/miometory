---
name: worktree-file-guard
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: \.claude/worktrees/|\.git/worktrees/
---

Worktree内のファイル編集をブロックしました。

コード編集はdevcontainerで行ってください。
このworktreeはgit操作（mainブランチ取り込み、/git-deliver）専用です。
