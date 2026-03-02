# Database Migrations

Flyway migrations for the Miometry database schema.

## Migration Index

| Migration | Description | Feature |
|-----------|-------------|---------|
| `V1__init.sql` | Event store table (`event_store`) | Foundation |
| `V2__foundation.sql` | Snapshot store, event store indexes | Foundation |
| `V3__add_pattern_refs_to_organization.sql` | Organization pattern reference columns | Foundation |
| `V4__work_log_entry_tables.sql` | Work log entry tables | 002-work-log-entry |
| `V5__member_table.sql` | Member table for user profiles | 002-work-log-entry (US7) |
| `V6__performance_indices.sql` | Performance indexes | 002-work-log-entry |
| `V7__fix_member_email_unique_constraint.sql` | Per-tenant email uniqueness | 002-work-log-entry |
| `V8__projects_table.sql` | Projects table | 002-work-log-entry |
| `V9__add_member_version.sql` | Member version column (optimistic locking) | 002-work-log-entry |
| `V10__member_project_assignments.sql` | Member-project assignments | 003-project-selector |
| `V11__user_auth.sql` | Auth tables: users, roles, permissions, sessions, password reset tokens, audit logs | 004-user-login-auth |
| `V12__email_verification_tokens.sql` | Email verification tokens table | 004-user-login-auth |

## Conventions

- **Naming**: `V{number}__{description}.sql` (double underscore separator)
- **Versioning**: Sequential integers (V1, V2, ..., V12)
- **Dev seed data**: `data-dev.sql` loaded automatically via Spring Boot (`spring.sql.init`, dev profile only)
- **Running**: Flyway runs automatically on application startup (`spring.flyway.enabled=true`)

## Running Migrations

```bash
# Applied automatically on bootRun
cd backend && ./gradlew bootRun

# Reset database (delete all data, re-apply all migrations) — inside devcontainer:
docker compose -f .devcontainer/docker-compose.yml down -v db
docker compose -f .devcontainer/docker-compose.yml up -d db
cd backend && ./gradlew bootRun
```
