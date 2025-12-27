# SmartPresence - Master Deployment Guide

## Quick Start

### 1. Apply Supabase Migrations
```bash
# In Supabase Dashboard → SQL Editor
# Copy/paste content of: apply_all.sql
# Click "Run"
```

### 2. Verify Setup
```bash
# In Supabase Dashboard → SQL Editor
# Copy/paste content of: verification.sql
# Click "Run"
# Check output: "✅ ALL CHECKS PASSED"
```

### 3. Run Smoke Tests
```bash
# In Supabase Dashboard → SQL Editor
# Copy/paste content of: smoke_test.sql
# Click "Run"
# Verify no errors
```

---

## Environment Variables

### Backend (NestJS) - `.env.production`
```env
# Supabase
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# CRITICAL: Use SERVICE_ROLE_KEY for all write operations
# NEVER expose SERVICE_ROLE_KEY to clients

# JWT (if using custom auth)
JWT_SECRET=your-super-secret-jwt-key-min-32-chars
JWT_EXPIRES_IN=7d

# Server
PORT=3000
NODE_ENV=production

# Database (TypeORM config)
DATABASE_URL=postgresql://postgres:[password]@db.your-project.supabase.co:5432/postgres
```

**How to get keys**:
1. Supabase Dashboard → Settings → API
2. Copy `URL` → `SUPABASE_URL`
3. Copy `anon public` → `SUPABASE_ANON_KEY`
4. Copy `service_role` (secret) → `SUPABASE_SERVICE_ROLE_KEY`

---

## Deployment Steps

### STEP 1: Supabase Setup

#### 1.1 Create Project (if new)
- Go to https://supabase.com
- Create new project
- Wait for provisioning (~2 minutes)

#### 1.2 Run Migrations
```bash
# Option A: Dashboard (Recommended)
1. Dashboard → SQL Editor
2. New Query
3. Copy entire content of apply_all.sql
4. Click "Run" or Ctrl+Enter
5. Wait for success message

# Option B: CLI (if configured)
supabase db push --file backend/supabase/migrations/apply_all.sql
```

#### 1.3 Verify
```bash
# Run verification.sql in SQL Editor
# Expected output: "✅ ALL CHECKS PASSED"
```

#### 1.4 Smoke Test
```bash
# Run smoke_test.sql in SQL Editor
# Expected: All inserts succeed, duplicates rejected
```

---

### STEP 2: NestJS Deployment

#### 2.1 Install Dependencies
```bash
cd backend
npm install --production
```

#### 2.2 Build
```bash
npm run build
```

#### 2.3 Deploy

**Option A: PM2**
```bash
pm2 start dist/main.js --name smartpresence-api
pm2 save
pm2 startup
```

**Option B: Docker**
```bash
docker build -t smartpresence-api .
docker run -d -p 3000:3000 --env-file .env.production smartpresence-api
```

**Option C: Cloud Platform**
- Heroku: `git push heroku main`
- Railway: Connect GitHub repo
- Render: Connect GitHub repo

#### 2.4 Verify
```bash
curl http://localhost:3000/health
# Expected: {"status":"ok"}
```

---

## HTTP Smoke Tests

### Test 1: Device Bind (First Time)
```bash
curl -X POST http://localhost:3000/api/v1/devices/bind \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "teacherId": "teacher-001",
    "deviceId": "device-android-001",
    "metadata": {
      "model": "Samsung Galaxy S21",
      "manufacturer": "Samsung",
      "osVersion": "13",
      "sdkInt": "33"
    }
  }'
```

**Expected Response (200 OK)**:
```json
{
  "success": true,
  "message": "Device bound successfully",
  "data": {
    "id": "uuid-here",
    "teacherId": "teacher-001",
    "deviceId": "device-android-001",
    "boundAt": "2024-01-15T10:00:00Z",
    "lastSeenAt": "2024-01-15T10:00:00Z"
  }
}
```

---

### Test 2: Device Bind (Mismatch - 409)
```bash
curl -X POST http://localhost:3000/api/v1/devices/bind \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "teacherId": "teacher-001",
    "deviceId": "device-android-002",
    "metadata": {"model": "Different Device"}
  }'
```

**Expected Response (409 Conflict)**:
```json
{
  "statusCode": 409,
  "message": "Teacher is already bound to a different device",
  "error": "Conflict",
  "code": "DEVICE_MISMATCH",
  "boundDeviceId": "****-001"
}
```

---

### Test 3: Batch Events (3 Events with Idempotency)
```bash
curl -X POST http://localhost:3000/api/v1/attendance/events/batch \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-uuid-123",
    "events": [
      {
        "studentId": "student-001",
        "occurredAt": "2024-01-15T10:35:00Z",
        "confidence": 0.95,
        "idempotencyKey": "local-uuid-001",
        "source": "edge"
      },
      {
        "studentId": "student-002",
        "occurredAt": "2024-01-15T10:36:30Z",
        "confidence": 0.92,
        "idempotencyKey": "local-uuid-002",
        "source": "edge"
      },
      {
        "studentId": "student-003",
        "occurredAt": "2024-01-15T10:38:15Z",
        "confidence": 0.88,
        "idempotencyKey": "local-uuid-003",
        "source": "edge"
      }
    ]
  }'
```

**Expected Response (200 OK - First Time)**:
```json
{
  "success": true,
  "message": "Processed 3 events: 3 inserted, 0 ignored (duplicates)",
  "data": {
    "inserted": 3,
    "ignored": 0,
    "total": 3
  }
}
```

**Expected Response (200 OK - Retry)**:
```json
{
  "success": true,
  "message": "Processed 3 events: 0 inserted, 3 ignored (duplicates)",
  "data": {
    "inserted": 0,
    "ignored": 3,
    "total": 3
  }
}
```

---

### Test 4: Session Finalize (with courseId)
```bash
curl -X POST http://localhost:3000/api/v1/attendance/sessions/finalize \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-uuid-123",
    "teacherId": "teacher-001",
    "courseId": "course-math-101",
    "recordsJson": [
      {
        "studentId": "student-001",
        "status": "present",
        "confidence": 0.95,
        "confirmedBy": "system",
        "detectedAt": "2024-01-15T10:35:00Z"
      },
      {
        "studentId": "student-002",
        "status": "present",
        "confidence": 0.92,
        "confirmedBy": "system",
        "detectedAt": "2024-01-15T10:36:30Z"
      },
      {
        "studentId": "student-003",
        "status": "present",
        "confidence": 0.88,
        "confirmedBy": "system",
        "detectedAt": "2024-01-15T10:38:15Z"
      }
    ],
    "idempotencyKey": "pending-session-update-uuid-001"
  }'
```

**Expected Response (200 OK - First Time)**:
```json
{
  "success": true,
  "message": "Session finalized successfully",
  "data": {
    "success": true,
    "status": "applied",
    "finalizationId": "finalization-uuid-123"
  }
}
```

**Expected Response (200 OK - Retry)**:
```json
{
  "success": true,
  "message": "Session already finalized",
  "data": {
    "success": true,
    "status": "already_applied",
    "finalizationId": "finalization-uuid-123"
  }
}
```

---

## Verification Queries

### After HTTP Tests, verify in Supabase:

```sql
-- Check device bindings
SELECT teacher_id, device_id, status 
FROM device_bindings 
WHERE teacher_id = 'teacher-001';

-- Check attendance events
SELECT COUNT(*) as total_events
FROM attendance_events
WHERE session_id = 'session-uuid-123';
-- Expected: 3

-- Check no duplicates
SELECT session_id, idempotency_key, COUNT(*)
FROM attendance_events
WHERE session_id = 'session-uuid-123'
GROUP BY session_id, idempotency_key
HAVING COUNT(*) > 1;
-- Expected: 0 rows

-- Check session finalization
SELECT session_id, course_id, status, applied_at
FROM session_finalizations
WHERE session_id = 'session-uuid-123';
-- Expected: 1 row with status='applied', course_id='course-math-101'
```

---

## Success Criteria

- [ ] ✅ apply_all.sql executed without errors
- [ ] ✅ verification.sql shows "ALL CHECKS PASSED"
- [ ] ✅ smoke_test.sql completes successfully
- [ ] ✅ NestJS backend responds to /health
- [ ] ✅ Device bind returns 200 OK
- [ ] ✅ Device mismatch returns 409
- [ ] ✅ Batch events inserts 3, retry ignores 3
- [ ] ✅ Session finalize works, retry returns already_applied
- [ ] ✅ No duplicates in database
- [ ] ✅ courseId present in session_finalizations

---

## Troubleshooting

### Issue: "relation already exists"
**Solution**: Migrations are idempotent, safe to re-run

### Issue: "permission denied"
**Solution**: Use SERVICE_ROLE_KEY in NestJS, not ANON_KEY

### Issue: 409 on first bind
**Solution**: Check for existing active binding, revoke if needed

### Issue: Duplicates in DB
**Solution**: Verify unique indexes exist, check verification.sql output

---

## Rollback

If needed to rollback:

```sql
DROP TABLE IF EXISTS session_finalizations CASCADE;
DROP TABLE IF EXISTS attendance_events CASCADE;
DROP TABLE IF EXISTS device_bindings CASCADE;
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
```

Then restore from Supabase backup (Settings → Database → Backups).

---

## Support

- Supabase Docs: https://supabase.com/docs
- NestJS Docs: https://docs.nestjs.com
- Project Issues: [your-repo]/issues
