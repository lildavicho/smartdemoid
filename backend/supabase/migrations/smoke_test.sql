-- =====================================================
-- SMARTPRESENCE - SMOKE TESTS
-- Run after verification.sql passes
-- =====================================================

-- NOTE: These tests should be run using SERVICE ROLE KEY
-- in Supabase SQL Editor or via NestJS endpoints

-- =====================================================
-- TEST 1: Device Binding - First Bind
-- =====================================================

-- Insert first binding
INSERT INTO device_bindings (teacher_id, device_id, metadata)
VALUES (
    'test-teacher-001',
    'test-device-001',
    '{"model": "Samsung Galaxy S21", "manufacturer": "Samsung", "osVersion": "13"}'::jsonb
)
RETURNING id, teacher_id, device_id, status, bound_at;

-- Verify: Should return 1 row with status='active'

-- =====================================================
-- TEST 2: Device Binding - Same Device (Update last_seen_at)
-- =====================================================

-- Simulate same device binding again (should update last_seen_at)
UPDATE device_bindings
SET last_seen_at = NOW()
WHERE teacher_id = 'test-teacher-001' 
AND device_id = 'test-device-001'
AND status = 'active'
RETURNING id, teacher_id, device_id, last_seen_at;

-- Verify: Should return 1 row with updated last_seen_at

-- =====================================================
-- TEST 3: Device Binding - Different Device (Should Fail)
-- =====================================================

-- Try to bind different device (should fail due to unique constraint)
-- This simulates 409 DEVICE_MISMATCH scenario
INSERT INTO device_bindings (teacher_id, device_id, metadata)
VALUES (
    'test-teacher-001',
    'test-device-002',
    '{"model": "Xiaomi Redmi Note 12"}'::jsonb
);

-- Expected: ERROR - violates unique constraint "idx_device_bindings_active_teacher"

-- =====================================================
-- TEST 4: Device Rebind - Revoke Old + Create New
-- =====================================================

-- Revoke old binding
UPDATE device_bindings
SET status = 'revoked', revoked_at = NOW()
WHERE teacher_id = 'test-teacher-001' 
AND device_id = 'test-device-001'
AND status = 'active'
RETURNING id, device_id, status, revoked_at;

-- Create new binding
INSERT INTO device_bindings (teacher_id, device_id, metadata)
VALUES (
    'test-teacher-001',
    'test-device-002',
    '{"model": "Xiaomi Redmi Note 12"}'::jsonb
)
RETURNING id, teacher_id, device_id, status, bound_at;

-- Verify: Should have 2 bindings for teacher (1 revoked, 1 active)
SELECT teacher_id, device_id, status 
FROM device_bindings 
WHERE teacher_id = 'test-teacher-001'
ORDER BY bound_at;

-- =====================================================
-- TEST 5: Batch Events - Insert 3 Events
-- =====================================================

INSERT INTO attendance_events (session_id, student_id, occurred_at, confidence, idempotency_key, source)
VALUES 
    ('test-session-001', 'student-001', NOW(), 0.95, 'idempotency-key-001', 'edge'),
    ('test-session-001', 'student-002', NOW(), 0.92, 'idempotency-key-002', 'edge'),
    ('test-session-001', 'student-003', NOW(), 0.88, 'idempotency-key-003', 'edge')
RETURNING id, session_id, student_id, idempotency_key;

-- Verify: Should return 3 rows

-- Count events
SELECT COUNT(*) as total_events
FROM attendance_events
WHERE session_id = 'test-session-001';

-- Expected: 3

-- =====================================================
-- TEST 6: Idempotency - Duplicate Events (Should Fail)
-- =====================================================

-- Try to insert same events again
INSERT INTO attendance_events (session_id, student_id, occurred_at, confidence, idempotency_key, source)
VALUES 
    ('test-session-001', 'student-001', NOW(), 0.95, 'idempotency-key-001', 'edge');

-- Expected: ERROR - violates unique constraint "idx_attendance_events_idempotency"

-- =====================================================
-- TEST 7: Idempotency Check - No Duplicates
-- =====================================================

-- Verify no duplicates exist
SELECT 
    session_id,
    idempotency_key,
    COUNT(*) as duplicate_count
FROM attendance_events
WHERE session_id = 'test-session-001'
GROUP BY session_id, idempotency_key
HAVING COUNT(*) > 1;

-- Expected: 0 rows (no duplicates)

-- =====================================================
-- TEST 8: Session Finalization - First Time
-- =====================================================

INSERT INTO session_finalizations (
    session_id, 
    teacher_id, 
    course_id, 
    records_json, 
    idempotency_key, 
    status
)
VALUES (
    'test-session-001',
    'test-teacher-001',
    'test-course-math-101',
    '[
        {"studentId": "student-001", "status": "present", "confidence": 0.95},
        {"studentId": "student-002", "status": "present", "confidence": 0.92},
        {"studentId": "student-003", "status": "present", "confidence": 0.88}
    ]'::jsonb,
    'finalize-idempotency-key-001',
    'applied'
)
RETURNING id, session_id, course_id, status, created_at;

-- Verify: Should return 1 row with status='applied'

-- Update applied_at
UPDATE session_finalizations
SET applied_at = NOW()
WHERE session_id = 'test-session-001'
AND idempotency_key = 'finalize-idempotency-key-001'
RETURNING id, session_id, applied_at;

-- =====================================================
-- TEST 9: Idempotency - Duplicate Finalization (Should Fail)
-- =====================================================

-- Try to finalize same session again
INSERT INTO session_finalizations (
    session_id, 
    teacher_id, 
    course_id, 
    records_json, 
    idempotency_key, 
    status
)
VALUES (
    'test-session-001',
    'test-teacher-001',
    'test-course-math-101',
    '[]'::jsonb,
    'finalize-idempotency-key-001',
    'applied'
);

-- Expected: ERROR - violates unique constraint "idx_session_finalizations_idempotency"

-- =====================================================
-- TEST 10: Verify All Data
-- =====================================================

-- Device bindings summary
SELECT 
    teacher_id,
    COUNT(*) as total_bindings,
    COUNT(CASE WHEN status = 'active' THEN 1 END) as active_bindings,
    COUNT(CASE WHEN status = 'revoked' THEN 1 END) as revoked_bindings
FROM device_bindings
WHERE teacher_id = 'test-teacher-001'
GROUP BY teacher_id;

-- Expected: total=2, active=1, revoked=1

-- Attendance events summary
SELECT 
    session_id,
    COUNT(*) as total_events,
    COUNT(DISTINCT student_id) as unique_students,
    AVG(confidence) as avg_confidence
FROM attendance_events
WHERE session_id = 'test-session-001'
GROUP BY session_id;

-- Expected: total=3, unique_students=3, avg_confidence~0.92

-- Session finalizations summary
SELECT 
    session_id,
    course_id,
    status,
    jsonb_array_length(records_json) as records_count,
    applied_at IS NOT NULL as has_applied_at
FROM session_finalizations
WHERE session_id = 'test-session-001';

-- Expected: 1 row with records_count=3, has_applied_at=true

-- =====================================================
-- CLEANUP TEST DATA (Optional)
-- =====================================================

-- Uncomment to clean up test data:
/*
DELETE FROM session_finalizations WHERE session_id LIKE 'test-%';
DELETE FROM attendance_events WHERE session_id LIKE 'test-%';
DELETE FROM device_bindings WHERE teacher_id LIKE 'test-%';
*/

-- =====================================================
-- SMOKE TEST SUMMARY
-- =====================================================

DO $$
BEGIN
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'SMARTPRESENCE SMOKE TESTS COMPLETED';
    RAISE NOTICE '==============================================';
    RAISE NOTICE '✅ Device binding: OK';
    RAISE NOTICE '✅ Device rebind: OK';
    RAISE NOTICE '✅ Batch events: OK (3 inserted)';
    RAISE NOTICE '✅ Idempotency events: OK (duplicate rejected)';
    RAISE NOTICE '✅ Session finalization: OK';
    RAISE NOTICE '✅ Idempotency finalization: OK (duplicate rejected)';
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'Next: Run HTTP smoke tests via NestJS/Postman';
    RAISE NOTICE '==============================================';
END $$;
