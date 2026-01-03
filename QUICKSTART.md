# Quick Start Guide - Work Log Development

**Last Updated**: 2026-01-03 14:25 JST

## Start Both Backend and Frontend

### Terminal 1: Backend (Spring Boot)
```bash
cd /home/devman/repos/work-log/backend
./gradlew bootRun

# Wait for: "Started BackendApplication in X.xxx seconds"
# Backend runs on: http://localhost:8080
```

### Terminal 2: Frontend (Next.js)
```bash
cd /home/devman/repos/work-log/frontend
npm run dev

# Wait for: "Ready in xxx ms"
# Frontend runs on: http://localhost:3000
# Open browser: http://localhost:3000/worklog
```

---

## Verify Everything Works

### 1. Test Backend Health
```bash
curl http://localhost:8080/api/v1/health
# Expected: {"status":"ok"}
```

### 2. Test Calendar API (with CORS)
```bash
curl "http://localhost:8080/api/v1/worklog/calendar/2026/1?memberId=00000000-0000-0000-0000-000000000001" \
  -H "Origin: http://localhost:3000"

# Expected: JSON response with fiscal year, period dates, and dates array
```

### 3. Test CORS Preflight
```bash
curl -X OPTIONS http://localhost:8080/api/v1/worklog/calendar/2026/1 \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET" \
  -v 2>&1 | grep -i "access-control"

# Expected: Access-Control-Allow-Origin: http://localhost:3000
```

### 4. Create Test Entry
```bash
curl -X POST http://localhost:8080/api/v1/worklog/entries \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:3000" \
  -d '{
    "memberId": "00000000-0000-0000-0000-000000000001",
    "projectId": "00000000-0000-0000-0000-000000000002",
    "date": "2026-01-03",
    "hours": 8.0,
    "comment": "Testing CORS integration"
  }'

# Expected: HTTP 201 with ETag header and entry details
```

---

## Open in Browser

### Main Application
- **URL**: http://localhost:3000/worklog
- **Expected**: Monthly calendar view with Previous/Today/Next buttons

### Check Browser Console
- Press **F12** to open DevTools
- Check **Console** tab for errors
- Check **Network** tab to see API calls
- Look for successful calls to `http://localhost:8080/api/v1/worklog/calendar/...`

---

## Troubleshooting

### Backend Issues

#### Port 8080 already in use
```bash
# Find and kill process on port 8080
lsof -ti:8080 | xargs kill -9

# Or manually
lsof -i:8080
kill -9 <PID>

# Then restart backend
cd backend && ./gradlew bootRun
```

#### Database connection refused
```bash
# Start PostgreSQL via Docker
cd /home/devman/repos/work-log/infra/docker
docker compose -f docker-compose.dev.yml up -d

# Verify
docker ps | grep worklog-db
# Should show container running on port 5432
```

#### Flyway migration errors
```bash
# Reset database (WARNING: deletes all data)
docker compose -f infra/docker/docker-compose.dev.yml down -v
docker compose -f infra/docker/docker-compose.dev.yml up -d

# Wait 5 seconds for PostgreSQL to start, then restart backend
cd backend && ./gradlew bootRun
```

### Frontend Issues

#### Port 3000 already in use
```bash
# Kill all Next.js processes
pkill -f "node.*next"

# Or the frontend will use port 3001 automatically
# Check terminal output for actual port
```

#### Module not found errors
```bash
# Reinstall dependencies
cd frontend
rm -rf node_modules package-lock.json
npm install

# Restart dev server
npm run dev
```

#### API CORS errors (should be fixed now)
- Check that backend is running: `curl http://localhost:8080/api/v1/health`
- Check browser console for specific CORS error
- Verify backend logs show CORS filter is active

### Browser Issues

#### Calendar shows "Loading..." forever
1. **Open DevTools** (F12)
2. **Check Console** for errors
3. **Check Network tab**:
   - Look for request to `/api/v1/worklog/calendar/...`
   - Check if it failed (red) or succeeded (green)
   - Click on request to see details
4. **Common causes**:
   - Backend not running → start backend
   - Wrong API URL → check `frontend/app/services/api.ts`
   - CORS error → backend should have CORS enabled now (commit b666b4c)

#### Calendar shows error message
- Check the error text displayed
- Open DevTools Console for stack trace
- Check backend logs for exceptions

---

## Database Access

### Connect to PostgreSQL
```bash
# Using Docker exec
docker exec -it worklog-db psql -U worklog -d worklog

# Check schema
\dt

# Query event store
SELECT aggregate_type, event_type, COUNT(*) 
FROM event_store 
GROUP BY aggregate_type, event_type;

# Query projection (currently empty)
SELECT * FROM work_log_entries_projection;

# Exit
\q
```

### Check Migrations
```sql
SELECT version, description, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank;

-- Should show V1, V2, V3, V4 all successful
```

---

## Current Status (Commit b666b4c)

### ✅ What's Working
- Backend API on port 8080
- Frontend on port 3000
- CORS enabled (localhost:3000, localhost:3001)
- Database migrations (V1→V4)
- Health endpoint
- Calendar API endpoint
- Work log CRUD endpoints

### 🚧 What's Not Implemented Yet
- Daily entry form (click date → edit entries)
- Project selector dropdown
- Auto-save (60s interval)
- Session timeout warning
- Member/Project management (using test UUIDs for now)

### 📊 Progress
- **28/45 tasks complete (62%)**
- **17 tasks remaining** (forms, auto-save, testing)

---

## Next Development Steps

1. **Daily Entry Form** (Priority 1)
   - File: `frontend/app/worklog/[date]/page.tsx`
   - Component: `frontend/app/components/worklog/DailyEntryForm.tsx`
   - Wire calendar clicks to navigate to daily entry page

2. **Auto-Save** (Priority 2)
   - Hook: `frontend/app/hooks/useAutoSave.ts`
   - Save draft every 60 seconds
   - Show "Saving..." / "Saved" indicators

3. **Testing** (Priority 3)
   - Backend: `backend/src/test/kotlin/com/worklog/`
   - Frontend: `frontend/app/__tests__/`
   - E2E: Playwright tests

---

## Git Status

**Branch**: `002-work-log-entry`

**Recent Commits**:
```
b666b4c feat(cors): Enable CORS for frontend development
a1bcce8 fix(frontend): Update tsconfig path mapping
28a8d84 fix(security): Simplify security config
5851e55 fix(migration): Remove foreign key constraints
2a4f2cb feat(frontend): Implement calendar view
975b416 feat(projection): Implement calendar projection
742551d feat(api): Implement WorkLogController REST endpoints
```

**Files Changed**: 40+ files across backend and frontend

---

## Useful Commands

### Build & Test
```bash
# Backend compile
cd backend && ./gradlew compileKotlin compileJava

# Backend tests (when ready)
cd backend && ./gradlew test

# Frontend lint
cd frontend && npm run lint

# Frontend format
cd frontend && npm run format
```

### Git Operations
```bash
# View status
git status

# View recent commits
git log --oneline -10

# View changes
git diff

# Create new commit
git add <files>
git commit -m "message"
```

### Docker Operations
```bash
# Start database
docker compose -f infra/docker/docker-compose.dev.yml up -d

# Stop database
docker compose -f infra/docker/docker-compose.dev.yml down

# Reset database (deletes data)
docker compose -f infra/docker/docker-compose.dev.yml down -v

# View logs
docker logs worklog-db -f
```

---

## Important Notes

### Test UUIDs
Since Member and Project management isn't implemented yet, use these test UUIDs:
- **Member ID**: `00000000-0000-0000-0000-000000000001`
- **Project ID**: `00000000-0000-0000-0000-000000000002`

### Authentication
Currently disabled for development. All endpoints are public.

### CORS Configuration
Allows requests from:
- `http://localhost:3000` (default Next.js port)
- `http://localhost:3001` (alternative if 3000 is busy)

Production should restrict to actual domain.

---

**Ready to code!** 🚀

Start both servers in separate terminals and open http://localhost:3000/worklog in your browser.
