# Phase 7: Final Testing & Merge to Main

## Current Status
- **Branch**: `001-foundation`
- **Latest Commit**: `235afa1` - Phase 6 complete
- **Code Status**: ✅ All code complete and committed
- **Build Status**: ✅ Compiles successfully (`./gradlew build -x test`)
- **Test Status**: ⏳ Blocked by Docker permissions

## Prerequisites

### Fix Docker Permissions (REQUIRED)

The tests use Testcontainers which requires Docker access. Currently blocked by:
```
permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

**Fix Steps**:
```bash
# Option 1: Add user to docker group (requires sudo)
sudo usermod -aG docker devman
newgrp docker

# Option 2: If you can't use sudo, ask system administrator to:
# - Add 'devman' to 'docker' group
# - Restart Docker daemon
# - Have user logout/login

# Verify Docker access:
docker ps
docker images
```

## Step-by-Step Execution Plan

### Step 1: Verify Environment
```bash
cd /home/devman/repos/work-log
git branch --show-current  # Should show: 001-foundation
git status                 # Should show: nothing to commit, working tree clean
docker ps                  # Should show running containers (or empty, but no permission error)
```

### Step 2: Run Full Test Suite
```bash
cd /home/devman/repos/work-log/backend

# Clean build with all tests
./gradlew clean test --console=plain

# Expected output:
# - 14 test classes
# - 150+ tests executed
# - 0 failures
# - BUILD SUCCESSFUL
```

**If tests fail**: 
- Review failure messages carefully
- Check if Testcontainers PostgreSQL started successfully
- Verify Flyway migrations ran (V1, V2, V3)
- Check application-test.yaml configuration

### Step 3: Review Test Coverage

Verify key test files executed:
```bash
# Should see these tests pass:
# - DateInfoEndpointTest (11 tests) ← NEW in Phase 6
# - OrganizationControllerTest
# - TenantControllerTest
# - FiscalYearPatternControllerTest
# - MonthlyPeriodPatternControllerTest
# - DateInfoServiceTest
# - Domain tests (Tenant, Organization, FiscalYearPattern, MonthlyPeriodPattern)
# - Event sourcing tests (EventStore, AuditLogger, SnapshotStore)
```

### Step 4: Manual Integration Testing (Optional but Recommended)

Start the application and test the full flow:

```bash
# Terminal 1: Start the backend
cd /home/devman/repos/work-log/backend
./gradlew bootRun

# Terminal 2: Run test requests
cd /home/devman/repos/work-log

# 1. Create tenant
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"code":"TEST_TENANT","name":"Test Tenant"}' | jq

# Save tenant ID from response, then:

# 2. Create fiscal year pattern (April 1 start)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/fiscal-year-patterns \
  -H "Content-Type: application/json" \
  -d '{"name":"April Fiscal Year","startMonth":4,"startDay":1}' | jq

# Save fiscalYearPattern ID, then:

# 3. Create monthly period pattern (21st start)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/monthly-period-patterns \
  -H "Content-Type: application/json" \
  -d '{"name":"21st Period","startDay":21}' | jq

# Save monthlyPeriodPattern ID, then:

# 4. Create root organization with patterns
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations \
  -H "Content-Type: application/json" \
  -d '{
    "code":"ORG_ROOT",
    "name":"Root Organization",
    "level":1,
    "parentId":null,
    "fiscalYearPatternId":"{FY_PATTERN_ID}",
    "monthlyPeriodPatternId":"{MP_PATTERN_ID}"
  }' | jq

# Save organization ID, then:

# 5. Test date-info endpoint
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations/{ORG_ID}/date-info \
  -H "Content-Type: application/json" \
  -d '{"date":"2024-01-25"}' | jq

# Expected response:
# {
#   "date": "2024-01-25",
#   "fiscalYear": 2023,
#   "fiscalYearStart": "2023-04-01",
#   "fiscalYearEnd": "2024-03-31",
#   "monthlyPeriodStart": "2024-01-21",
#   "monthlyPeriodEnd": "2024-02-20",
#   "fiscalYearPatternId": "{FY_PATTERN_ID}",
#   "monthlyPeriodPatternId": "{MP_PATTERN_ID}",
#   "organizationId": "{ORG_ID}"
# }

# 6. Test pattern inheritance (create child organization without patterns)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations \
  -H "Content-Type: application/json" \
  -d '{
    "code":"ORG_CHILD",
    "name":"Child Organization",
    "level":2,
    "parentId":"{PARENT_ORG_ID}",
    "fiscalYearPatternId":null,
    "monthlyPeriodPatternId":null
  }' | jq

# Test date-info on child (should inherit parent patterns)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations/{CHILD_ORG_ID}/date-info \
  -H "Content-Type: application/json" \
  -d '{"date":"2024-01-25"}' | jq

# 7. Test error cases
# Non-existent organization (should return 404)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations/00000000-0000-0000-0000-000000000000/date-info \
  -H "Content-Type: application/json" \
  -d '{"date":"2024-01-25"}' -w "\nHTTP Status: %{http_code}\n"

# Missing date in request (should return 400)
curl -X POST http://localhost:8080/api/v1/tenants/{TENANT_ID}/organizations/{ORG_ID}/date-info \
  -H "Content-Type: application/json" \
  -d '{}' -w "\nHTTP Status: %{http_code}\n"
```

### Step 5: Final Review Before Merge

```bash
cd /home/devman/repos/work-log

# Review all commits in feature branch
git log main..001-foundation --oneline

# Expected commits:
# 235afa1 feat(001-foundation): Add date-info endpoint tests and schema fix (T061)
# 77a947c feat(001-foundation): Add REST API controllers for fiscal year and monthly period patterns (T058-T060)
# 60cd3ac feat(001-foundation): Add DateInfoService with pattern hierarchy resolution (T057)
# ... (earlier phase commits)

# Check diff against main
git diff main...001-foundation --stat

# Verify no uncommitted changes
git status
```

### Step 6: Merge to Main

```bash
cd /home/devman/repos/work-log

# Ensure we're on 001-foundation
git checkout 001-foundation

# Update main branch (if working with remote)
git checkout main
git pull origin main

# Merge feature branch
git merge 001-foundation --no-ff -m "Merge branch '001-foundation' - Foundation feature complete

Implements:
- Tenant and Organization aggregates with event sourcing
- Fiscal year and monthly period patterns
- Date info calculation with pattern hierarchy resolution
- REST API controllers for all entities
- Comprehensive test suite (150+ tests)
- Database schema with Flyway migrations (V1, V2, V3)

Phases completed:
- Phase 1-2: Event sourcing infrastructure
- Phase 3: Tenant and Organization domain
- Phase 4: Pattern entities and repositories
- Phase 5: Application services and API controllers
- Phase 6: Date info endpoint tests and schema fix

All acceptance criteria met per specs/001-foundation/spec.md"

# Verify merge
git log --oneline -10
```

### Step 7: Final Verification on Main

```bash
# Run tests again on main branch
cd /home/devman/repos/work-log/backend
./gradlew clean test --console=plain

# Should still pass with 0 failures
```

### Step 8: Tag the Release

```bash
cd /home/devman/repos/work-log

# Create annotated tag
git tag -a v0.1.0-foundation -m "Release v0.1.0 - Foundation Feature Complete

Delivered Features:
- Multi-tenant work log system foundation
- Event-sourced tenant and organization aggregates
- Fiscal year and monthly period pattern management
- Date info calculation endpoint with pattern inheritance
- Full REST API with error handling
- 150+ integration and unit tests
- Production-ready database schema

Tech Stack:
- Spring Boot 3.5.9
- Kotlin 2.3.0 + Java 21
- PostgreSQL 16 with JSONB event store
- Flyway migrations
- Testcontainers for integration testing

Documentation:
- specs/001-foundation/spec.md
- specs/001-foundation/tasks.md
- AGENTS.md (coding conventions)
"

# Verify tag
git tag -l -n9 v0.1.0-foundation

# Push main branch and tag to remote
git push origin main
git push origin v0.1.0-foundation
```

### Step 9: Cleanup (Optional)

```bash
# Delete local feature branch (after successful merge)
git branch -d 001-foundation

# Delete remote feature branch (if it exists)
git push origin --delete 001-foundation

# Verify branches
git branch -a
```

## Success Criteria Checklist

### Pre-Merge Verification
- [ ] Docker permissions fixed
- [ ] `./gradlew clean test` passes with 0 failures
- [ ] All 14 test files executed
- [ ] 150+ tests passed
- [ ] DateInfoEndpointTest (11 tests) all passing
- [ ] Manual integration testing completed (optional)
- [ ] No uncommitted changes on 001-foundation branch

### Merge Verification
- [ ] Feature branch merged to main
- [ ] Tests pass on main branch
- [ ] Git history is clean and readable
- [ ] Merge commit message is descriptive

### Post-Merge Actions
- [ ] Release tagged as v0.1.0-foundation
- [ ] Tag pushed to remote
- [ ] Feature branch deleted (optional)
- [ ] Documentation updated if needed

## Known Issues & Notes

### Docker Permission Issue
**Symptom**: `permission denied while trying to connect to the docker API`
**Resolution**: User must be added to `docker` group
**Impact**: Blocks all test execution (Testcontainers requirement)

### Projection Table Workaround
**Issue**: Event-sourced aggregates (Tenant, Organization) don't automatically populate projection tables
**Current**: Tests manually insert into projection tables after creating entities via API
**Future**: Implement `@TransactionalEventListener` for automatic projection updates
**Scope**: Out of scope for 001-foundation, tracked for future work

### Organization Code Validation
**Rule**: Organization codes must use underscores (`_`), not hyphens (`-`)
**Example**: `ORG_ROOT_001` ✅ | `ORG-ROOT-001` ❌
**Rationale**: Domain validation in `Code` value object (alphanumeric + underscores only)

## Troubleshooting

### If Tests Fail

1. **Check Docker**:
   ```bash
   docker ps
   docker logs <container_id>
   ```

2. **Check Testcontainers**:
   ```bash
   cat ~/.testcontainers.properties
   # Should contain: testcontainers.reuse.enable=true
   ```

3. **Check Database**:
   ```bash
   # Connect to test database (if container is running)
   docker exec -it <postgres_container> psql -U test
   \dt  # List tables
   \d organization  # Check organization table has FK columns
   ```

4. **Check Migrations**:
   ```bash
   # Verify all 3 migrations exist
   ls -la backend/src/main/resources/db/migration/
   # Should show: V1__init.sql, V2__foundation.sql, V3__add_pattern_refs_to_organization.sql
   ```

5. **Check Logs**:
   ```bash
   # Run tests with debug output
   cd backend
   ./gradlew test --info 2>&1 | tee test-output.log
   ```

### If Build Fails

```bash
# Clean and rebuild
cd backend
./gradlew clean
./gradlew build -x test

# If compilation errors, check:
# - Java version: java -version (should be 21)
# - Kotlin version in build.gradle.kts (should be 2.3.0)
# - Gradle version: ./gradlew --version (should be 8.x)
```

### If Merge Has Conflicts

```bash
# Abort merge
git merge --abort

# Update main and rebase feature branch
git checkout main
git pull origin main
git checkout 001-foundation
git rebase main

# Resolve any conflicts, then:
git add .
git rebase --continue

# Try merge again
git checkout main
git merge 001-foundation --no-ff
```

## Additional Resources

- **Specification**: `specs/001-foundation/spec.md`
- **Task Breakdown**: `specs/001-foundation/tasks.md`
- **Coding Guidelines**: `AGENTS.md`
- **OpenAPI Contract**: `specs/001-foundation/contracts/openapi.yaml`

## Contact/Support

If you encounter issues:
1. Review error messages carefully
2. Check this instruction file's troubleshooting section
3. Review git history: `git log --oneline --graph --all`
4. Check test output: `./gradlew test --info`

---

**Last Updated**: 2026-01-02
**Phase**: 7 (Final Testing & Merge)
**Status**: Ready for execution pending Docker permissions fix
