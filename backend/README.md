# SmartPresence Backend

API NestJS para SmartPresence (asistencia con reconocimiento facial).

## Requisitos

- Node.js 18+
- PostgreSQL (Supabase/Render u otro)

## Variables de entorno

El backend lee variables desde `backend/.env` o `../.env` (solo para desarrollo local). En Render se configuran desde el panel.

Minimas para operacion completa:

- `DATABASE_URL` (PostgreSQL)
- `JWT_SECRET`
- `JWT_EXPIRES_IN` (ej: `7d`)

Opcionales:

- `API_PREFIX` (default `api/v1`)
- `CORS_ORIGINS` (coma-separado, solo si hay panel web)
- `CORS_CREDENTIALS` (`true|false`, default `false`)
- `RATE_LIMIT_WINDOW_MS` (default `60000`)
- `RATE_LIMIT_MAX` (default `300`)
- `TYPEORM_SYNCHRONIZE` (default `false` en prod)
- `TYPEORM_LOGGING` (default `false` en prod)

## Local

```bash
cd backend
npm ci
npm run start:dev
```

Health:

```bash
curl http://localhost:3000/healthz
curl http://localhost:3000/readyz
```

## Endpoints

Con `API_PREFIX=api/v1`:

- `POST /api/v1/auth/device/login`

Ejemplo:

```bash
curl -X POST http://localhost:3000/api/v1/auth/device/login ^
  -H "Content-Type: application/json" ^
  -d "{\"serial_number\":\"DEMO-001\",\"pin_code\":\"1234\"}"
```

## Render (produccion)

Este repo incluye `render.yaml` en la raiz.

En el panel de Render:

- Root Directory: `backend`
- Build Command: `npm ci && npm run build`
- Start Command: `npm run start:prod`
- Health Check Path: `/healthz`

Probar:

```bash
curl https://smartid-backend.onrender.com/healthz
curl https://smartid-backend.onrender.com/readyz
curl -X POST https://smartid-backend.onrender.com/api/v1/auth/device/login ^
  -H "Content-Type: application/json" ^
  -d "{\"serial_number\":\"DEMO-001\",\"pin_code\":\"1234\"}"
```
