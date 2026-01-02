# Feature 002: Work-Log Entry - Quick Start Guide

**Status**: Ready to Start  
**Current Phase**: Phase 1 - Backend Domain Model  
**Next Task**: T001 - Project エンティティ実装

---

## What We Completed

### ✅ SC-007 Code Coverage Improvement
- **Achievement**: All domain packages now have 85%+ coverage
- **Commit**: `9171fb4` - "test: Improve domain layer coverage to meet SC-007"
- **Documentation**: `backend/COVERAGE.md`

### ✅ Feature 002 Planning
- **Proposal**: `specs/002-work-log-entry/proposal.md` (36KB, 1,187 lines)
- **Tasks**: `specs/002-work-log-entry/tasks.md` (29KB, 1,194 lines)
- **Commits**: 
  - `c11f681` - Proposal document
  - `d34928d` - Task breakdown (130 tasks)

---

## Feature 002 Overview

### Core Functionality
- 稼働時間入力（日単位、0.25h刻み）
- 複数案件の同時管理
- 休暇入力（年休、有休、病欠など）
- CSV一括入力/出力
- 前月案件コピー
- 承認フロー（承認/差し戻し）
- レスポンシブUI（PC/スマホ/タブレット共通）

### Technical Stack
**Backend**:
- Spring Boot 3.5.9
- Kotlin 2.3.0
- Java 21
- PostgreSQL (Event Store + JSONB)

**Frontend**:
- Next.js 15.1.1
- React 19.x
- Zustand (state management)
- react-day-picker (calendar)
- TanStack Table (CSV export)
- shadcn/ui (components)

### Implementation Timeline
- **Total**: 130 tasks, 22-27 business days
- **Phase 1**: Backend Domain Model (3-4 days)
- **Phase 2**: Backend API (4-5 days)
- **Phase 3**: Frontend Foundation (5-6 days)
- **Phase 4**: Frontend Features (4-5 days)
- **Phase 5**: Testing & QA (3-4 days)
- **Phase 6**: Documentation & Deployment (3 days)

---

## How to Start Phase 1

### Step 1: Read the Proposal
```bash
cd /home/devman/repos/work-log
cat specs/002-work-log-entry/proposal.md
```

**Key Sections**:
- Section 4.1: 新規ドメインエンティティ (5 entities)
- Section 4.2: データモデル (ER図)
- Section 6: データベース設計 (Flyway migration)

### Step 2: Review Phase 1 Tasks
```bash
cat specs/002-work-log-entry/tasks.md | grep -A 20 "^## Phase 1"
```

**Tasks T001-T013**:
- T001-T002: Project エンティティ + イベント
- T003-T004: Member エンティティ + イベント
- T005-T006: WorkLog エンティティ + イベント
- T007-T008: Absence エンティティ + イベント
- T009: Holiday エンティティ
- T010-T012: Repository 実装
- T013: Flyway Migration (V4)

### Step 3: Start with T001
**Task**: Project エンティティ実装  
**File**: `backend/src/main/java/com/worklog/domain/project/Project.kt`  
**Reference**: `backend/src/main/java/com/worklog/domain/tenant/Tenant.kt` (既存の実装例)

**Implementation Checklist**:
- [ ] Create `Project.kt` (Aggregate Root)
- [ ] Fields: id, organizationId, code, name, isActive, createdAt
- [ ] Validation: code unique per organization
- [ ] Unit tests (3+ tests)

**Command to Run Tests**:
```bash
cd backend
./gradlew test --tests "com.worklog.domain.project.*"
```

---

## Useful Commands

### Backend
```bash
# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests "com.worklog.domain.project.ProjectTest"

# Build
./gradlew build

# Run application
./gradlew bootRun

# Check coverage
./gradlew jacocoTestReport
# Open: backend/build/reports/jacoco/test/html/index.html
```

### Frontend
```bash
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev

# Lint
npm run lint

# Format
npm run format
```

### Docker
```bash
cd infra/docker

# Start all services
docker-compose -f docker-compose.dev.yml up -d

# Stop all services
docker-compose -f docker-compose.dev.yml down

# View logs
docker-compose -f docker-compose.dev.yml logs -f
```

---

## Architecture Patterns to Follow

### 1. Event Sourcing Pattern
**Example**: See `backend/src/main/java/com/worklog/domain/tenant/Tenant.kt`

```kotlin
class Project private constructor(
    val id: ProjectId,
    private var state: ProjectState
) : AggregateRoot() {
    
    companion object {
        fun create(command: CreateProjectCommand): Project {
            // Validation
            val event = ProjectCreated(...)
            return Project(id, state).apply { applyEvent(event) }
        }
    }
    
    private fun applyEvent(event: ProjectCreated) {
        // Update state
        addEvent(event)
    }
}
```

### 2. Repository Pattern
**Example**: See `backend/src/main/java/com/worklog/infrastructure/repository/JdbcTenantRepository.kt`

```kotlin
@Repository
class JdbcProjectRepository(
    private val eventStore: EventStore
) : ProjectRepository {
    
    override fun save(project: Project) {
        eventStore.append(project.id, project.getUncommittedEvents())
    }
    
    override fun findById(id: ProjectId): Project? {
        val events = eventStore.load(id)
        return if (events.isNotEmpty()) Project.fromEvents(events) else null
    }
}
```

### 3. API Controller Pattern
**Example**: See `backend/src/main/java/com/worklog/api/TenantController.java`

```java
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    
    private final ProjectService projectService;
    
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
        @RequestBody @Valid CreateProjectRequest request
    ) {
        var project = projectService.createProject(request);
        return ResponseEntity.ok(toResponse(project));
    }
}
```

---

## Next Session Prompt

When you're ready to continue, use this prompt:

```
I'm ready to implement Feature 002 (Work-Log Entry System).

Current status:
- Proposal completed: specs/002-work-log-entry/proposal.md
- Tasks defined: specs/002-work-log-entry/tasks.md (130 tasks)
- Branch: main
- Latest commit: d34928d (Task breakdown)

Next steps:
1. Start Phase 1: Backend Domain Model
2. Implement T001: Project エンティティ

Please guide me through implementing T001 step by step.
```

---

## Reference Documents

### Feature 002 Documentation
- **Proposal**: `specs/002-work-log-entry/proposal.md`
- **Tasks**: `specs/002-work-log-entry/tasks.md`

### Existing Code References
- **Domain Model**: `backend/src/main/java/com/worklog/domain/`
- **Event Store**: `backend/src/main/java/com/worklog/eventsourcing/`
- **API Controllers**: `backend/src/main/java/com/worklog/api/`
- **Tests**: `backend/src/test/kotlin/com/worklog/`

### Migration References
- **V1**: `backend/src/main/resources/db/migration/V1__init.sql`
- **V2**: `backend/src/main/resources/db/migration/V2__foundation.sql`

---

## Success Criteria

### Phase 1 Complete When:
- [ ] All 5 entities implemented (Project, Member, WorkLog, Absence, Holiday)
- [ ] All events implemented (15 events total)
- [ ] All repositories implemented (5 repositories)
- [ ] Flyway migration (V4) created and tested
- [ ] Unit tests: 50+ tests
- [ ] Integration tests: 20+ tests
- [ ] Code coverage: 85%+ for domain layer
- [ ] All tests passing

---

**Ready to start?** Begin with T001: Project エンティティ実装

**Good luck!**
