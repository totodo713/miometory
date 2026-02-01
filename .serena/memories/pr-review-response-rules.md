# PR Review Response Rules

## レビューコメント対応時のルール

### 1. 個別コメントへの返信
- レビューでindividual comment（スレッド形式のコメント）がある場合は、**必ずそのスレッドに対応内容を返信する**
- 返信には修正したコミットハッシュと、具体的にどう修正したかを簡潔に記載する
- 例: `Fixed in commit \`abc1234\`. Changed to use \`indexOf("=")\` to split only on the first \`=\`.`

### 2. サマリコメントは不要
- PRに対する一般的なサマリコメント（スレッド以外のコメント）は**投稿しない**
- 各スレッドへの個別返信で十分

### 3. PRのルートコメント（Description）の更新
- レビュー対応が完了したら、PRのbody（ルートコメント/Description）を更新する
- 対応内容を反映した統括的な変更内容としてPR Descriptionを更新する
- 更新には `github_update_pull_request` ツールの `body` パラメータを使用する

### 4. 対応の流れ
1. レビューコメントを確認（`github_pull_request_read` で `get_review_comments`）
2. コードを修正してコミット・プッシュ
3. 各レビュースレッドに返信（`gh api` で `in_reply_to` を使用）
4. PR Descriptionを更新して変更内容の全体像を反映

### 5. コメント返信のAPIコマンド
```bash
gh api repos/{owner}/{repo}/pulls/{pr_number}/comments \
  -X POST \
  -f body="Fixed in commit \`{commit_hash}\`. {description}" \
  -F in_reply_to={comment_id}
```

### 6. PR Description更新
```
github_update_pull_request(
  owner="...",
  repo="...",
  pullNumber=...,
  body="## Summary\n..."
)
```
