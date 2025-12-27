# SmartPresence Deployment Guide

## Backend (Render)

**Live URL:** `https://smartid-backend.onrender.com`

### Endpoints
| Endpoint | Purpose |
|----------|---------|
| `/healthz` | Health check (returns `ok`) |
| `/readyz` | Readiness check (verifies DB connection) |
| `/api/v1/health` | API health with metadata |

### Cold Start Mitigation (Render Free Tier)

Render Free tier hibernates after 15 min of inactivity. First request after hibernation takes 30-60s.

#### Option 1: UptimeRobot (Free) ✅ Recommended
1. Create account at https://uptimerobot.com
2. Add **HTTP(s)** monitor:
   - URL: `https://smartid-backend.onrender.com/healthz`
   - Interval: **5 minutes**
3. Done - service stays warm 24/7

#### Option 2: GitHub Actions Cron (Free)
Create `.github/workflows/keep-warm.yml`:
```yaml
name: Keep Warm
on:
  schedule:
    - cron: '*/14 * * * *'  # Every 14 minutes
jobs:
  ping:
    runs-on: ubuntu-latest
    steps:
      - run: curl -sf https://smartid-backend.onrender.com/healthz
```

#### Option 3: Render Starter Plan ($7/month)
Upgrade for always-on instances with zero cold starts.

---

## Android App

### Build Types
| Type | API URL | Cleartext |
|------|---------|-----------|
| Debug | `https://smartid-backend.onrender.com/api/v1/` | Blocked (HTTPS only) |
| Release | `https://smartid-backend.onrender.com/api/v1/` | Blocked (HTTPS only) |

### Verification Commands
```bash
# Clean and rebuild
cd android
./gradlew clean assembleDebug

# Verify BuildConfig (check generated source)
cat app/build/generated/source/buildConfig/debug/com/smartpresence/idukay/BuildConfig.java | grep API_BASE_URL
# Expected: public static final String API_BASE_URL = "https://smartid-backend.onrender.com/api/v1/";
```

### QA Checklist
- [ ] Uninstall previous APK from device
- [ ] Build → Clean Project → Rebuild Project
- [ ] Install debug APK on physical device
- [ ] **Test 1:** WiFi off, mobile data on → Login works
- [ ] **Test 2:** Different WiFi network → App works
- [ ] **Test 3:** Interactive screen/tablet → Camera + attendance works
- [ ] Check Logcat for `https://smartid-backend.onrender.com` (no `10.0.2.2`)

---

## curl Verification (from any network)
```bash
# Health check
curl -s https://smartid-backend.onrender.com/healthz
# Expected: ok

# API health
curl -s https://smartid-backend.onrender.com/api/v1/health
# Expected: {"status":"ok","timestamp":"...","service":"SmartPresence Backend","version":"1.0.0"}

# Root (service info)
curl -s https://smartid-backend.onrender.com/
# Expected: {"service":"smartpresence-backend","apiPrefix":"/api/v1","status":"ok"}
```
