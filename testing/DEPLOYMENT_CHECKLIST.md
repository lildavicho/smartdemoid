# SmartPresence - Deployment Checklist

## Environment Variables

### Backend
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=eyJ...
JWT_SECRET=your-secret-key
PORT=3000
NODE_ENV=production
```

### Android
```properties
API_BASE_URL=https://your-backend-api.com
SUPABASE_URL=https://your-project.supabase.co
```

## Deployment Steps

### 1. Supabase Migrations
```bash
# Dashboard → SQL Editor → Run block10_complete.sql
```
Verify: 3 tables created, indexes present, RLS enabled

### 2. Deploy Backend
```bash
npm install --production
npm run build
pm2 start dist/main.js --name smartpresence-api
```

### 3. Smoke Tests
- Device bind: 200 OK
- Batch events: 200 OK, inserted: 1
- Finalize session: 200 OK, status: applied
- Idempotency: ignored: 1, no duplicates

### 4. Deploy Android
```bash
./gradlew assembleRelease
adb install app-release.apk
```

## Success Criteria
- ✅ Uptime > 99%
- ✅ 0 duplicados en DB
- ✅ Device binding funciona
- ✅ Sync worker sin crashes
