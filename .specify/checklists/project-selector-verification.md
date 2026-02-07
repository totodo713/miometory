# ProjectSelector Verification Checklist

This checklist documents the manual and automated verification steps for the
ProjectSelector component and its associated backend endpoint.

**Last verified:** 2026-02-07

## Backend
- [x] Endpoint is implemented: `GET /api/v1/members/{id}/projects`
  - Reference: `backend/src/main/java/com/worklog/api/MemberController.java`
  - Expected response: HTTP 200 with JSON body
    ```json
    { "projects": [ { "id": "...", "code": "...", "name": "..." }, ... ], "count": n }
    ```
  - Quick check commands:
    ```bash
    # Start backend (if not running)
    cd backend && ./gradlew bootRun

    # Verify response
    curl -sS "http://localhost:8080/api/v1/members/<member-uuid>/projects"
    ```
  - **Verification result (2026-02-07):**
    ```json
    {"projects":[{"id":"990e8400-e29b-41d4-a716-446655440002","code":"INFRA-OPS","name":"Infrastructure & DevOps"},{"id":"990e8400-e29b-41d4-a716-446655440001","code":"WORKLOG-DEV","name":"Work-Log Development Project"}],"count":2}
    ```
  - [x] Returns 404 with proper error for non-existent member ID

## Frontend
- [x] `ProjectSelector` calls `api.members.getAssignedProjects(memberId)`
  - Reference: `frontend/app/components/worklog/ProjectSelector.tsx`
  - API client: `frontend/app/services/api.ts`
  - Expected behaviour:
    - [x] Shows loading state while fetching
    - [x] Displays an informative message when there are no assigned projects
    - [x] Allows searching/filtering by project code or name
    - [x] Supports keyboard navigation: ArrowUp, ArrowDown, Enter, Escape
  - Quick check commands:
    ```bash
    cd frontend && npm run dev
    # Open http://localhost:3000/worklog/2026-01-15 in a browser
    ```

## Integration Scenarios
- [x] With valid member ID, projects list is rendered and selectable.
- [x] Search term filters the list correctly.
- [x] Keyboard navigation selects the highlighted project.
- [x] Clicking outside closes the dropdown and resets search input to selected value.

## Automated Tests
- [x] Frontend unit tests (84 tests passed)
  ```bash
  cd frontend && npm test
  ```
  **Result (2026-02-07):** 84 passed, 0 failed
  
- [x] Backend tests for MemberController (827 tests passed)
  ```bash
  cd backend && ./gradlew test
  ```
  **Result (2026-02-07):** 827 passed, 0 failures, 0 errors

## PR / Documentation
- Include this checklist in the PR description so reviewers can reproduce verification steps.
- Add notes about any deviations or known issues (e.g. slow DB queries, missing seed data).

## Notes
- If the backend returns an empty `projects` array, the frontend displays an amber notice:
  "No projects assigned. Please contact your administrator."
- If the backend call fails, the frontend shows a red error card with the error message.
- Test member IDs for verification:
  - `00000000-0000-0000-0000-000000000001` (Bob Engineer)
  - `00000000-0000-0000-0000-000000000002` (Alice Manager)
