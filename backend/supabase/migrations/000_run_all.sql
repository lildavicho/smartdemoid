-- Migration 004: Combined Migration Script
-- Purpose: Run all migrations in order
-- This is a convenience script to run all migrations at once

-- 1. Device Bindings
\i 001_device_bindings.sql

-- 2. Attendance Events
\i 002_attendance_events.sql

-- 3. Session Finalizations
\i 003_session_finalizations.sql

-- Verification queries
SELECT 'device_bindings' as table_name, COUNT(*) as row_count FROM device_bindings
UNION ALL
SELECT 'attendance_events', COUNT(*) FROM attendance_events
UNION ALL
SELECT 'session_finalizations', COUNT(*) FROM session_finalizations;

-- Check indexes
SELECT 
    tablename, 
    indexname, 
    indexdef 
FROM pg_indexes 
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, indexname;
