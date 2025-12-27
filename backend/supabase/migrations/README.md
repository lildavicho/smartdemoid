# Supabase Migration - Block 10

## Cómo Ejecutar

### Opción 1: Supabase Dashboard (Recomendado)

1. Abre tu proyecto en Supabase Dashboard
2. Ve a **SQL Editor**
3. Crea una nueva query
4. Copia y pega el contenido completo de `block10_complete.sql`
5. Click en **Run** o presiona `Ctrl+Enter`

### Opción 2: Supabase CLI

```bash
# Desde la raíz del proyecto backend
supabase db push --file supabase/migrations/block10_complete.sql
```

## Verificación

Después de ejecutar la migración, verifica que las tablas se crearon correctamente:

```sql
-- Ver tablas creadas
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('device_bindings', 'attendance_events', 'session_finalizations');

-- Ver conteo de registros (debería ser 0)
SELECT 'device_bindings' as table_name, COUNT(*) as row_count FROM device_bindings
UNION ALL
SELECT 'attendance_events', COUNT(*) FROM attendance_events
UNION ALL
SELECT 'session_finalizations', COUNT(*) FROM session_finalizations;

-- Ver índices creados
SELECT tablename, indexname 
FROM pg_indexes 
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, indexname;
```

## Estructura de Tablas

### device_bindings
- Almacena el binding de dispositivos por profesor
- Constraint: solo un binding activo por profesor
- RLS: solo service role

### attendance_events
- Log idempotente de eventos de asistencia
- Constraint: unique (session_id, idempotency_key)
- RLS: solo service role

### session_finalizations
- Finalizaciones idempotentes de sesiones
- Constraint: unique (session_id, idempotency_key)
- RLS: solo service role

## Rollback (si es necesario)

Si necesitas revertir la migración:

```sql
DROP TABLE IF EXISTS session_finalizations CASCADE;
DROP TABLE IF EXISTS attendance_events CASCADE;
DROP TABLE IF EXISTS device_bindings CASCADE;
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
```

## Notas

- Todas las tablas tienen RLS habilitado
- Solo service role puede acceder (configurado en NestJS)
- Los índices están optimizados para queries frecuentes
- Las constraints garantizan idempotencia
