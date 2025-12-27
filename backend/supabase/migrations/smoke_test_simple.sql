-- =====================================================
-- SMARTPRESENCE - SMOKE TESTS (SIMPLIFIED)
-- Run AFTER apply_all_simple.sql
-- =====================================================

-- TEST 1: Insert first device binding
INSERT INTO device_bindings (teacher_id, device_id, metadata)
VALUES ('test-teacher-001', 'test-device-001', '{"model": "Samsung Galaxy S21"}'::jsonb)
RETURNING id, teacher_id, device_id, status;

-- TEST 2: Update last_seen_at (same device)
UPDATE device_bindings
SET last_seen_at = NOW()
WHERE teacher_id = 'test-teacher-001' AND device_id = 'test-device-001'
RETURNING id, last_seen_at;

-- TEST 3: Try to bind different device (should fail with unique constraint error)
-- UNCOMMENT to test (will error):
-- INSERT INTO device_bindings (teacher_id, device_id)
-- VALUES ('test-teacher-001', 'test-device-002');

-- TEST 4: Revoke old binding
UPDATE device_bindings
SET status = 'revoked', revoked_at = NOW()
WHERE teacher_id = 'test-teacher-001' AND device_id = 'test-device-001'
RETURNING id, status, revoked_at;

-- TEST 5: Create new binding
INSERT INTO device_bindings (teacher_id, device_id, metadata)
VALUES ('test-teacher-001', 'test-device-002', '{"model": "Xiaomi"}'::jsonb)
RETURNING id, device_id, status;

-- TEST 6: Verify bindings
SELECT teacher_id, device_id, status, bound_at
FROM device_bindings
WHERE teacher_id = 'test-teacher-001'
ORDER BY bound_at;

-- TEST 7: Insert batch events
INSERT INTO attendance_events (session_id, student_id, occurred_at, confidence, idempotency_key)
VALUES 
    ('test-session-001', 'student-001', NOW(), 0.95, 'key-001'),
    ('test-session-001', 'student-002', NOW(), 0.92, 'key-002'),
    ('test-session-001', 'student-003', NOW(), 0.88, 'key-003')
RETURNING id, student_id, idempotency_key;

-- TEST 8: Count events
SELECT COUNT(*) as total_events
FROM attendance_events
WHERE session_id = 'test-session-001';

-- TEST 9: Try duplicate event (should fail)
-- UNCOMMENT to test (will error):
-- INSERT INTO attendance_events (session_id, student_id, occurred_at, confidence, idempotency_key)
-- VALUES ('test-session-001', 'student-001', NOW(), 0.95, 'key-001');

-- TEST 10: Verify no duplicates
SELECT session_id, idempotency_key, COUNT(*) as count
FROM attendance_events
WHERE session_id = 'test-session-001'
GROUP BY session_id, idempotency_key
HAVING COUNT(*) > 1;

-- TEST 11: Insert session finalization
INSERT INTO session_finalizations (
    session_id, teacher_id, course_id, records_json, idempotency_key, status, applied_at
)
VALUES (
    'test-session-001',
    'test-teacher-001',
    'course-math-101',
    '[{"studentId":"student-001","status":"present"}]'::jsonb,
    'finalize-key-001',
    'applied',
    NOW()
)
RETURNING id, session_id, course_id, status;

-- TEST 12: Try duplicate finalization (should fail)
-- UNCOMMENT to test (will error):
-- INSERT INTO session_finalizations (session_id, teacher_id, course_id, records_json, idempotency_key)
-- VALUES ('test-session-001', 'test-teacher-001', 'course-math-101', '[]'::jsonb, 'finalize-key-001');

-- TEST 13: Verify finalization
SELECT session_id, course_id, status, applied_at
FROM session_finalizations
WHERE session_id = 'test-session-001';

-- TEST 14: Summary
SELECT 
    (SELECT COUNT(*) FROM device_bindings WHERE teacher_id = 'test-teacher-001') as bindings,
    (SELECT COUNT(*) FROM attendance_events WHERE session_id = 'test-session-001') as events,
    (SELECT COUNT(*) FROM session_finalizations WHERE session_id = 'test-session-001') as finalizations;

-- CLEANUP (optional - uncomment to remove test data)
-- DELETE FROM session_finalizations WHERE session_id LIKE 'test-%';
-- DELETE FROM attendance_events WHERE session_id LIKE 'test-%';
-- DELETE FROM device_bindings WHERE teacher_id LIKE 'test-%';
