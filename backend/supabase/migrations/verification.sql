-- =====================================================
-- SMARTPRESENCE - VERIFICATION QUERIES
-- Run after apply_all.sql to confirm setup
-- =====================================================

-- =====================================================
-- 1. VERIFY TABLES EXIST
-- =====================================================

SELECT 
    table_name,
    table_type
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY table_name;

-- Expected: 3 rows (all table_type = 'BASE TABLE')

-- =====================================================
-- 2. VERIFY COLUMNS EXIST
-- =====================================================

-- device_bindings columns
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'device_bindings'
ORDER BY ordinal_position;

-- Expected columns: id, teacher_id, device_id, status, bound_at, last_seen_at, revoked_at, metadata, created_at, updated_at

-- attendance_events columns
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'attendance_events'
ORDER BY ordinal_position;

-- Expected columns: id, session_id, student_id, occurred_at, confidence, source, idempotency_key, created_at

-- session_finalizations columns
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'session_finalizations'
ORDER BY ordinal_position;

-- Expected columns: id, session_id, teacher_id, course_id, records_json, idempotency_key, status, error_message, created_at, applied_at, updated_at

-- =====================================================
-- 3. VERIFY COURSE_ID EXISTS
-- =====================================================

SELECT 
    column_name,
    data_type,
    column_default,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'session_finalizations'
AND column_name = 'course_id';

-- Expected: 1 row with data_type = 'text', column_default = ''::text

-- =====================================================
-- 4. VERIFY INDEXES
-- =====================================================

SELECT 
    tablename,
    indexname,
    indexdef
FROM pg_indexes 
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, indexname;

-- Expected indexes:
-- device_bindings: idx_device_bindings_active_teacher (UNIQUE partial), idx_device_bindings_teacher_id, idx_device_bindings_device_id, idx_device_bindings_status, idx_device_bindings_created_at
-- attendance_events: idx_attendance_events_idempotency (UNIQUE), idx_attendance_events_session, idx_attendance_events_student, idx_attendance_events_occurred_at, idx_attendance_events_created_at
-- session_finalizations: idx_session_finalizations_idempotency (UNIQUE), idx_session_finalizations_session, idx_session_finalizations_teacher, idx_session_finalizations_course, idx_session_finalizations_status, idx_session_finalizations_created_at

-- =====================================================
-- 5. VERIFY UNIQUE CONSTRAINTS
-- =====================================================

SELECT 
    tc.table_name,
    tc.constraint_name,
    tc.constraint_type,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.table_schema = 'public'
AND tc.table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')
AND tc.constraint_type = 'UNIQUE'
ORDER BY tc.table_name, tc.constraint_name;

-- Expected: Unique constraints via indexes (idx_device_bindings_active_teacher, idx_attendance_events_idempotency, idx_session_finalizations_idempotency)

-- =====================================================
-- 6. VERIFY CHECK CONSTRAINTS
-- =====================================================

SELECT 
    tc.table_name,
    tc.constraint_name,
    cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc 
    ON tc.constraint_name = cc.constraint_name
WHERE tc.table_schema = 'public'
AND tc.table_name IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tc.table_name;

-- Expected:
-- device_bindings: status IN ('active', 'revoked')
-- attendance_events: source IN ('edge', 'manual', 'import')
-- session_finalizations: status IN ('pending', 'applied', 'rejected')

-- =====================================================
-- 7. VERIFY RLS ENABLED
-- =====================================================

SELECT 
    schemaname,
    tablename,
    rowsecurity
FROM pg_tables
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename;

-- Expected: All rowsecurity = true

-- =====================================================
-- 8. VERIFY RLS POLICIES
-- =====================================================

SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual
FROM pg_policies
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, policyname;

-- Expected: 3 policies (one per table) with name '*_service_role'

-- =====================================================
-- 9. VERIFY TRIGGERS
-- =====================================================

SELECT 
    trigger_name,
    event_manipulation,
    event_object_table,
    action_statement
FROM information_schema.triggers
WHERE event_object_table IN ('device_bindings', 'session_finalizations')
ORDER BY event_object_table, trigger_name;

-- Expected: 
-- device_bindings_updated_at on device_bindings
-- session_finalizations_updated_at on session_finalizations

-- =====================================================
-- 10. VERIFY EXTENSIONS
-- =====================================================

SELECT 
    extname,
    extversion
FROM pg_extension
WHERE extname = 'pgcrypto';

-- Expected: 1 row with extname = 'pgcrypto'

-- =====================================================
-- SUMMARY VERIFICATION
-- =====================================================

DO $$
DECLARE
    tables_count INT;
    indexes_count INT;
    rls_count INT;
BEGIN
    -- Count tables
    SELECT COUNT(*) INTO tables_count
    FROM information_schema.tables 
    WHERE table_schema = 'public' 
    AND table_name IN ('device_bindings', 'attendance_events', 'session_finalizations');
    
    -- Count indexes
    SELECT COUNT(*) INTO indexes_count
    FROM pg_indexes 
    WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations');
    
    -- Count RLS enabled tables
    SELECT COUNT(*) INTO rls_count
    FROM pg_tables
    WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
    AND rowsecurity = true;
    
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'SMARTPRESENCE VERIFICATION SUMMARY';
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'Tables created: % / 3', tables_count;
    RAISE NOTICE 'Indexes created: % (expected ~16)', indexes_count;
    RAISE NOTICE 'RLS enabled: % / 3', rls_count;
    
    IF tables_count = 3 AND rls_count = 3 THEN
        RAISE NOTICE '✅ ALL CHECKS PASSED';
    ELSE
        RAISE WARNING '⚠️ SOME CHECKS FAILED - Review output above';
    END IF;
    RAISE NOTICE '==============================================';
END $$;
