# SmartPresence - Supabase SQL Verification Queries

## 1. Listar Últimos Device Bindings por Teacher

```sql
-- Ver todos los bindings de un teacher (activos y revocados)
SELECT 
    id,
    teacher_id,
    device_id,
    status,
    bound_at,
    last_seen_at,
    revoked_at,
    metadata
FROM device_bindings
WHERE teacher_id = 'TEACHER_ID'
ORDER BY bound_at DESC;

-- Ver solo binding activo
SELECT 
    teacher_id,
    device_id,
    status,
    bound_at,
    last_seen_at,
    EXTRACT(EPOCH FROM (NOW() - last_seen_at))/3600 as hours_since_last_seen
FROM device_bindings
WHERE teacher_id = 'TEACHER_ID' 
  AND status = 'active';

-- Ver historial de rebinds
SELECT 
    device_id,
    status,
    bound_at,
    revoked_at,
    EXTRACT(EPOCH FROM (revoked_at - bound_at))/86400 as days_active
FROM device_bindings
WHERE teacher_id = 'TEACHER_ID'
ORDER BY bound_at DESC;
```

---

## 2. Contar Attendance Events por Session

```sql
-- Contar eventos por sesión
SELECT 
    session_id,
    COUNT(*) as total_events,
    COUNT(DISTINCT student_id) as unique_students,
    MIN(occurred_at) as first_event,
    MAX(occurred_at) as last_event,
    AVG(confidence) as avg_confidence
FROM attendance_events
WHERE session_id = 'SESSION_ID'
GROUP BY session_id;

-- Ver todos los eventos de una sesión
SELECT 
    id,
    student_id,
    occurred_at,
    confidence,
    source,
    idempotency_key,
    created_at
FROM attendance_events
WHERE session_id = 'SESSION_ID'
ORDER BY occurred_at ASC;

-- Contar eventos por estudiante en una sesión
SELECT 
    student_id,
    COUNT(*) as event_count,
    MAX(confidence) as max_confidence,
    MIN(occurred_at) as first_seen,
    MAX(occurred_at) as last_seen
FROM attendance_events
WHERE session_id = 'SESSION_ID'
GROUP BY student_id
ORDER BY event_count DESC;
```

---

## 3. Detectar Duplicados (Debe dar 0)

```sql
-- Detectar duplicados por idempotency_key (debe retornar 0 filas)
SELECT 
    session_id,
    idempotency_key,
    COUNT(*) as duplicate_count
FROM attendance_events
GROUP BY session_id, idempotency_key
HAVING COUNT(*) > 1;

-- Verificar constraint único funciona
SELECT 
    COUNT(*) as total_events,
    COUNT(DISTINCT idempotency_key) as unique_keys
FROM attendance_events
WHERE session_id = 'SESSION_ID';
-- total_events debe ser igual a unique_keys

-- Detectar duplicados en session_finalizations (debe retornar 0 filas)
SELECT 
    session_id,
    idempotency_key,
    COUNT(*) as duplicate_count
FROM session_finalizations
GROUP BY session_id, idempotency_key
HAVING COUNT(*) > 1;
```

---

## 4. Últimos Session Finalizations con Course ID

```sql
-- Ver finalizaciones recientes con courseId
SELECT 
    id,
    session_id,
    teacher_id,
    course_id,
    status,
    created_at,
    applied_at,
    error_message,
    EXTRACT(EPOCH FROM (applied_at - created_at)) as seconds_to_apply
FROM session_finalizations
ORDER BY created_at DESC
LIMIT 20;

-- Ver finalizaciones por teacher
SELECT 
    teacher_id,
    course_id,
    COUNT(*) as total_finalizations,
    COUNT(CASE WHEN status = 'applied' THEN 1 END) as applied_count,
    COUNT(CASE WHEN status = 'rejected' THEN 1 END) as rejected_count,
    COUNT(CASE WHEN status = 'pending' THEN 1 END) as pending_count
FROM session_finalizations
WHERE teacher_id = 'TEACHER_ID'
GROUP BY teacher_id, course_id;

-- Ver finalizaciones por curso
SELECT 
    course_id,
    COUNT(*) as total_sessions,
    COUNT(DISTINCT teacher_id) as unique_teachers,
    AVG(jsonb_array_length(records_json)) as avg_students_per_session
FROM session_finalizations
WHERE status = 'applied'
GROUP BY course_id
ORDER BY total_sessions DESC;
```

---

## 5. Verificar Status y Timestamps

```sql
-- Verificar estados de finalizaciones
SELECT 
    status,
    COUNT(*) as count,
    COUNT(CASE WHEN applied_at IS NOT NULL THEN 1 END) as with_applied_at,
    COUNT(CASE WHEN error_message IS NOT NULL THEN 1 END) as with_errors
FROM session_finalizations
GROUP BY status;

-- Ver finalizaciones pendientes (posible problema)
SELECT 
    id,
    session_id,
    teacher_id,
    course_id,
    created_at,
    EXTRACT(EPOCH FROM (NOW() - created_at))/3600 as hours_pending
FROM session_finalizations
WHERE status = 'pending'
  AND created_at < NOW() - INTERVAL '1 hour'
ORDER BY created_at ASC;

-- Ver finalizaciones rechazadas con errores
SELECT 
    session_id,
    teacher_id,
    course_id,
    error_message,
    created_at
FROM session_finalizations
WHERE status = 'rejected'
ORDER BY created_at DESC
LIMIT 10;
```

---

## 6. Verificar Inserted vs Ignored (Análisis Indirecto)

```sql
-- No hay forma directa de ver inserted/ignored del response,
-- pero podemos inferir por timestamps y duplicados

-- Ver eventos creados en ventana de tiempo (posible retry)
SELECT 
    session_id,
    idempotency_key,
    created_at,
    LAG(created_at) OVER (PARTITION BY session_id, idempotency_key ORDER BY created_at) as prev_attempt
FROM attendance_events
WHERE session_id = 'SESSION_ID'
ORDER BY idempotency_key, created_at;

-- Ver finalizaciones con mismo idempotency_key (retry detection)
SELECT 
    session_id,
    idempotency_key,
    status,
    created_at,
    applied_at,
    ROW_NUMBER() OVER (PARTITION BY session_id, idempotency_key ORDER BY created_at) as attempt_number
FROM session_finalizations
WHERE session_id = 'SESSION_ID';
-- Si attempt_number > 1, hubo retry (pero constraint único lo previene)
```

---

## 7. Health Check Queries

```sql
-- Ver actividad reciente (últimas 24 horas)
SELECT 
    'device_bindings' as table_name,
    COUNT(*) as total_records,
    COUNT(CASE WHEN created_at > NOW() - INTERVAL '24 hours' THEN 1 END) as last_24h
FROM device_bindings
UNION ALL
SELECT 
    'attendance_events',
    COUNT(*),
    COUNT(CASE WHEN created_at > NOW() - INTERVAL '24 hours' THEN 1 END)
FROM attendance_events
UNION ALL
SELECT 
    'session_finalizations',
    COUNT(*),
    COUNT(CASE WHEN created_at > NOW() - INTERVAL '24 hours' THEN 1 END)
FROM session_finalizations;

-- Ver teachers activos (con binding activo)
SELECT 
    COUNT(DISTINCT teacher_id) as active_teachers
FROM device_bindings
WHERE status = 'active'
  AND last_seen_at > NOW() - INTERVAL '7 days';

-- Ver sesiones finalizadas por día (últimos 7 días)
SELECT 
    DATE(applied_at) as date,
    COUNT(*) as sessions_finalized,
    COUNT(DISTINCT teacher_id) as unique_teachers,
    SUM(jsonb_array_length(records_json)) as total_students
FROM session_finalizations
WHERE status = 'applied'
  AND applied_at > NOW() - INTERVAL '7 days'
GROUP BY DATE(applied_at)
ORDER BY date DESC;
```

---

## 8. Data Integrity Checks

```sql
-- Verificar que no hay bindings activos duplicados por teacher
SELECT 
    teacher_id,
    COUNT(*) as active_bindings
FROM device_bindings
WHERE status = 'active'
GROUP BY teacher_id
HAVING COUNT(*) > 1;
-- Debe retornar 0 filas

-- Verificar que finalizaciones applied tienen applied_at
SELECT COUNT(*) as invalid_finalizations
FROM session_finalizations
WHERE status = 'applied' 
  AND applied_at IS NULL;
-- Debe ser 0

-- Verificar que finalizaciones rejected tienen error_message
SELECT COUNT(*) as missing_error_messages
FROM session_finalizations
WHERE status = 'rejected' 
  AND (error_message IS NULL OR error_message = '');
-- Debería ser 0 (opcional)

-- Verificar índices únicos funcionan
SELECT 
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
  AND indexdef LIKE '%UNIQUE%'
ORDER BY tablename, indexname;
```

---

## 9. Performance Queries

```sql
-- Ver tamaño de tablas
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Ver índices más usados
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY idx_scan DESC;
```

---

## Quick Copy-Paste Queries

```sql
-- Quick check: últimos 10 bindings
SELECT teacher_id, device_id, status, bound_at 
FROM device_bindings 
ORDER BY bound_at DESC LIMIT 10;

-- Quick check: eventos de última sesión
SELECT session_id, COUNT(*) as events 
FROM attendance_events 
GROUP BY session_id 
ORDER BY MAX(created_at) DESC LIMIT 1;

-- Quick check: últimas finalizaciones
SELECT session_id, course_id, status, applied_at 
FROM session_finalizations 
ORDER BY created_at DESC LIMIT 10;

-- Quick check: duplicados (debe ser 0)
SELECT COUNT(*) FROM (
    SELECT session_id, idempotency_key, COUNT(*) 
    FROM attendance_events 
    GROUP BY session_id, idempotency_key 
    HAVING COUNT(*) > 1
) duplicates;
```
