-- =====================================================
-- SMARTPRESENCE - VERIFICATION (SIMPLIFIED)
-- =====================================================

-- 1. Verify tables exist
SELECT table_name
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY table_name;

-- 2. Verify columns in device_bindings
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'device_bindings'
ORDER BY ordinal_position;

-- 3. Verify columns in attendance_events
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'attendance_events'
ORDER BY ordinal_position;

-- 4. Verify columns in session_finalizations (check course_id exists)
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'session_finalizations'
ORDER BY ordinal_position;

-- 5. Verify indexes
SELECT tablename, indexname
FROM pg_indexes 
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, indexname;

-- 6. Verify RLS enabled
SELECT tablename, rowsecurity
FROM pg_tables
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename;

-- 7. Verify RLS policies
SELECT tablename, policyname, cmd
FROM pg_policies
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename;

-- 8. Count check constraints
SELECT tc.table_name, tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc ON tc.constraint_name = cc.constraint_name
WHERE tc.table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tc.table_name;

-- 9. Summary counts
SELECT 
    (SELECT COUNT(*) FROM information_schema.tables 
     WHERE table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')) as tables_created,
    (SELECT COUNT(*) FROM pg_indexes 
     WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')) as indexes_created,
    (SELECT COUNT(*) FROM pg_tables 
     WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations') 
     AND rowsecurity = true) as rls_enabled;
