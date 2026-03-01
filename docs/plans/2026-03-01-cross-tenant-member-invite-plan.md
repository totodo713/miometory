# Cross-Tenant Member Invite Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 他テナントに既に登録済みのユーザーを新しいテナントのメンバーとして招待できるようにする

**Architecture:** `AdminMemberService.inviteMember()` に既存ユーザー検出ロジックを追加し、存在する場合は User 作成をスキップして Member のみ作成する。`MemberCsvValidationService` からは既存ユーザーメールのエラー判定を除外する。結果 CSV の status 列で `CREATED` / `EXISTING` を区別する。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito

**影響範囲の整理:**

`inviteMember()` の呼び出し元は3箇所:
- `AdminMemberCsvService.executeImport()` — CSV一括インポート
- `AdminMemberController.inviteMember()` — 個別メンバー招待API
- `AdminTenantService.bootstrapTenant()` — テナント初期セットアップ

`InviteMemberResult` の変更により全3箇所で `temporaryPassword` が null になりうる。
既存の `result.temporaryPassword()` アクセスは null 安全（record accessor は null を返すだけ）なので、
コンパイルは壊れないが、APIレスポンスの意味が変わる点に注意。

`existsByEmail()` は `AuthServiceImpl.register()` でも使用中のため削除不可。

---

### Task 1: `MemberCsvValidationService` — 既存ユーザーメールのエラー判定を除外

**Files:**
- Modify: `backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvValidationService.java`
- Modify: `backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvValidationServiceTest.java`

**Step 1: テスト修正 — `duplicateInUsersTable` を「既存ユーザーでもvalid」に変更**

`MemberCsvValidationServiceTest.java` の `DbDuplicateCheck.duplicateInUsersTable()` テスト（行199-212）を以下に書き換える:

```java
@Test
@DisplayName("should accept emails that exist in users table (cross-tenant invite)")
void existingUserEmailIsValid() {
    List<MemberCsvRow> rows = List.of(new MemberCsvRow(1, "existing@example.com", "Exists"));
    when(userRepository.findExistingEmails(anyCollection())).thenReturn(Set.of("existing@example.com"));
    when(memberRepository.findExistingEmailsInTenant(any(), anyCollection()))
            .thenReturn(Set.of());

    MemberCsvResult result = service.validate(rows, TENANT_ID);

    assertEquals(1, result.validRows().size());
    assertFalse(result.hasErrors());
}
```

**Step 2: テスト実行 — 失敗を確認**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvValidationServiceTest" -x checkFormat -x detekt`
Expected: FAIL — `existingUserEmailIsValid` が失敗する（現コードはエラーとして弾くため）

**Step 3: `validate()` を修正 — Phase 2 から User テーブルチェックを除外**

`MemberCsvValidationService.java` の Phase 2（行57-78）を以下に書き換える:

```java
// Phase 2: Batch DB duplicate check (same-tenant members only)
if (!formatValidRows.isEmpty()) {
    Set<String> emailsToCheck = formatValidRows.stream()
            .map(r -> r.email().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    Set<String> existingMemberEmails = memberRepository.findExistingEmailsInTenant(tenantId, emailsToCheck);

    for (MemberCsvRow row : formatValidRows) {
        String lowerEmail = row.email().toLowerCase(Locale.ROOT);
        if (existingMemberEmails.contains(lowerEmail)) {
            errors.add(new CsvValidationError(
                    row.rowNumber(), "email", "Email already exists as a member in this tenant"));
        } else {
            validRows.add(row);
        }
    }
}
```

変更ポイント:
- `userRepository.findExistingEmails()` の呼び出しを削除
- `existingUserEmails.contains()` による分岐を削除
- `existingMemberEmails`（同一テナント内重複）チェックは維持

**Step 4: `userRepository` 依存の除去**

`validate()` から `userRepository` の使用がなくなったため、クラスから依存を除去する。

`MemberCsvValidationService.java` を修正:
- import `com.worklog.infrastructure.persistence.JdbcUserRepository;`（行4）を削除
- フィールド `private final JdbcUserRepository userRepository;`（行23）を削除
- コンストラクタの `JdbcUserRepository userRepository` パラメータと代入（行26-28）を削除

修正後:
```java
private final JdbcMemberRepository memberRepository;

public MemberCsvValidationService(JdbcMemberRepository memberRepository) {
    this.memberRepository = memberRepository;
}
```

テストファイルも対応修正:
- `@Mock private JdbcUserRepository userRepository;`（行28-29）を削除
- `setUp()` を `service = new MemberCsvValidationService(memberRepository);` に変更（行40）
- `when(userRepository.findExistingEmails(...))` の行を以下4テストから削除:
  - `validRows()`（行53）
  - `duplicateEmailInCsv()`（行165）
  - `caseInsensitiveDuplicate()`（行183）
  - `duplicateInMembersTable()`（行218）
- `existingUserEmailIsValid()` テストから `when(userRepository.findExistingEmails(...))` 行も削除（Step 1 で追加したもの）
- import `com.worklog.infrastructure.persistence.JdbcUserRepository;`（行9）を削除

**Step 5: テスト実行 — 全テスト通過を確認**

Run: `cd backend && ./gradlew test --tests "com.worklog.infrastructure.csv.MemberCsvValidationServiceTest" -x checkFormat -x detekt`
Expected: PASS

**Step 6: コミット**

```bash
git add backend/src/main/java/com/worklog/infrastructure/csv/MemberCsvValidationService.java \
       backend/src/test/java/com/worklog/infrastructure/csv/MemberCsvValidationServiceTest.java
git commit -m "fix: allow existing user emails in CSV validation for cross-tenant invite"
```

---

### Task 2: `inviteMember()` に既存ユーザー分岐を追加

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/service/AdminMemberService.java` (行128-157)
- Create: `backend/src/test/java/com/worklog/application/service/AdminMemberServiceTest.java`

**Step 1: テスト作成**

`AdminMemberServiceTest.java` を新規作成。

注意点:
- `AdminMemberService` のコンストラクタは5引数: `(JdbcMemberRepository, JdbcUserRepository, RoleRepository, JdbcTemplate, PasswordEncoder)`
- `RoleRepository` のパッケージは `com.worklog.infrastructure.persistence`（NOT `repository`）
- テストでは `JdbcTemplate` も `@Mock` で用意する

```java
package com.worklog.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.worklog.application.command.InviteMemberCommand;
import com.worklog.domain.member.Member;
import com.worklog.domain.role.Role;
import com.worklog.domain.role.RoleId;
import com.worklog.domain.user.User;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.persistence.RoleRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("AdminMemberService")
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private JdbcMemberRepository memberRepository;

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminMemberService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID INVITED_BY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminMemberService(memberRepository, userRepository, roleRepository, jdbcTemplate, passwordEncoder);
    }

    @Nested
    @DisplayName("inviteMember")
    class InviteMember {

        @Test
        @DisplayName("should create user and member for new email")
        void newUser() {
            var command = new InviteMemberCommand("new@example.com", "New User", ORG_ID, null, INVITED_BY);
            when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
            var role = mock(Role.class);
            when(role.getId()).thenReturn(RoleId.of(UUID.randomUUID()));
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
            when(passwordEncoder.encode(any())).thenReturn("hashed");

            var result = service.inviteMember(command, TENANT_ID);

            assertNotNull(result.memberId());
            assertNotNull(result.temporaryPassword());
            assertFalse(result.isExistingUser());
            verify(userRepository).save(any(User.class));
            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("should skip user creation for existing email and create member only")
        void existingUser() {
            var command = new InviteMemberCommand("existing@example.com", "Existing", ORG_ID, null, INVITED_BY);
            var existingUser = mock(User.class);
            when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

            var result = service.inviteMember(command, TENANT_ID);

            assertNotNull(result.memberId());
            assertNull(result.temporaryPassword());
            assertTrue(result.isExistingUser());
            verify(userRepository, never()).save(any(User.class));
            verify(memberRepository).save(any(Member.class));
        }
    }
}
```

**Step 2: `InviteMemberResult` を拡張 + `inviteMember()` を修正**

`AdminMemberService.java` — `InviteMemberResult`（行157）を変更:

```java
public record InviteMemberResult(UUID memberId, String temporaryPassword, boolean isExistingUser) {}
```

`inviteMember()` メソッド（行128-155）を以下に書き換え:

```java
public InviteMemberResult inviteMember(InviteMemberCommand command, UUID tenantId) {
    var existingUser = userRepository.findByEmail(command.email());

    if (existingUser.isPresent()) {
        // Existing user: skip user creation, create member only
        var member = Member.create(
                TenantId.of(tenantId),
                OrganizationId.of(command.organizationId()),
                command.email(),
                command.displayName(),
                command.managerId() != null ? MemberId.of(command.managerId()) : null);
        memberRepository.save(member);

        return new InviteMemberResult(member.getId().value(), null, true);
    }

    // New user: create both user and member
    var userRole = roleRepository
            .findByName("USER")
            .orElseThrow(() -> new DomainException("ROLE_NOT_FOUND", "USER role not found"));

    String tempPassword = UUID.randomUUID().toString().substring(0, 12);
    String hashedPassword = passwordEncoder.encode(tempPassword);

    var user = User.create(command.email(), command.displayName(), hashedPassword, userRole.getId());
    user.verifyEmail();
    userRepository.save(user);

    var member = Member.create(
            TenantId.of(tenantId),
            OrganizationId.of(command.organizationId()),
            command.email(),
            command.displayName(),
            command.managerId() != null ? MemberId.of(command.managerId()) : null);
    memberRepository.save(member);

    return new InviteMemberResult(member.getId().value(), tempPassword, false);
}
```

**Step 3: テスト実行**

Run: `cd backend && ./gradlew test --tests "com.worklog.application.service.AdminMemberServiceTest" -x checkFormat -x detekt`
Expected: PASS

**Step 4: コンパイル全体確認**

`InviteMemberResult` の引数が2→3に変わるが、既存の呼び出し元（`AdminMemberCsvService`, `AdminMemberController`, `AdminTenantService`）は `result.temporaryPassword()` / `result.memberId()` のアクセサのみ使用しており、record を直接構築している箇所は `inviteMember()` 内部のみなのでコンパイルは通るはず。

Run: `cd backend && ./gradlew compileJava compileTestJava -x checkFormat -x detekt`
Expected: PASS

もしコンパイルエラーが出た場合: エラーメッセージから `InviteMemberResult` を構築している箇所を特定し、第3引数 `false` を追加する。

**Step 5: コミット**

```bash
git add backend/src/main/java/com/worklog/application/service/AdminMemberService.java \
       backend/src/test/java/com/worklog/application/service/AdminMemberServiceTest.java
git commit -m "fix: support cross-tenant member invite for existing users"
```

---

### Task 3: 結果 CSV の status 列で `CREATED` / `EXISTING` を区別

**Files:**
- Modify: `backend/src/main/java/com/worklog/application/service/AdminMemberCsvService.java` (行117, 213-217, 271)

**Step 1: `ImportedRow` に `isExistingUser` フラグ追加**

行271を変更:
```java
// Before
private record ImportedRow(String email, String displayName, String temporaryPassword) {}

// After
private record ImportedRow(String email, String displayName, String temporaryPassword, boolean isExistingUser) {}
```

**Step 2: `executeImport()` の ImportedRow 生成を修正**

行117を変更:
```java
// Before
importedRows.add(new ImportedRow(row.email(), row.displayName(), result.temporaryPassword()));

// After
importedRows.add(new ImportedRow(row.email(), row.displayName(), result.temporaryPassword(), result.isExistingUser()));
```

**Step 3: `buildResultCsv()` の status を動的に出力**

行213-217を変更:
```java
// Before
for (ImportedRow row : rows) {
    writer.write(escapeCsvField(row.email()) + ","
            + escapeCsvField(row.displayName()) + ","
            + "SUCCESS,"
            + escapeCsvField(row.temporaryPassword()) + "\n");
}

// After
for (ImportedRow row : rows) {
    String status = row.isExistingUser() ? "EXISTING" : "CREATED";
    writer.write(escapeCsvField(row.email()) + ","
            + escapeCsvField(row.displayName()) + ","
            + status + ","
            + escapeCsvField(row.temporaryPassword()) + "\n");
}
```

**Step 4: コンパイル確認**

Run: `cd backend && ./gradlew compileJava -x checkFormat -x detekt`
Expected: PASS

**Step 5: コミット**

```bash
git add backend/src/main/java/com/worklog/application/service/AdminMemberCsvService.java
git commit -m "feat: distinguish CREATED/EXISTING status in import result CSV"
```

---

### Task 4: 全テスト実行 + lint/format チェック

**Step 1: バックエンド全テスト実行**

Run: `cd backend && ./gradlew test -x checkFormat -x detekt`
Expected: PASS

失敗するテストがあれば修正する。特に注意:
- `AdminTenantService` のテストで `inviteMember()` をモックしている場合、`InviteMemberResult` の3引数コンストラクタに合わせる
- `AdminMemberController` のテストで同様

**Step 2: フォーマット + lint チェック**

Run: `cd backend && ./gradlew checkFormat && ./gradlew detekt`
Expected: PASS

フォーマット違反があれば: `cd backend && ./gradlew formatApply` で修正。

**Step 3: コミット（修正がある場合のみ）**

```bash
git add -u
git commit -m "fix: update tests and formatting for cross-tenant invite changes"
```

---

### 変更サマリー

| ファイル | 変更種別 | 内容 |
|---------|---------|------|
| `MemberCsvValidationService.java` | Modify | `userRepository` 依存削除、既存ユーザーメールをエラーにしない |
| `MemberCsvValidationServiceTest.java` | Modify | 既存ユーザーテストを「valid」に変更、`userRepository` モック削除 |
| `AdminMemberService.java` | Modify | `inviteMember()` に既存ユーザー分岐追加、`InviteMemberResult` 拡張 |
| `AdminMemberServiceTest.java` | Create | 新規・既存ユーザーの inviteMember テスト |
| `AdminMemberCsvService.java` | Modify | `ImportedRow` 拡張、status列で `CREATED`/`EXISTING` 出力 |

注意: `existsByEmail()` は `AuthServiceImpl` で使用中のため `JdbcUserRepository` からは削除しない。
