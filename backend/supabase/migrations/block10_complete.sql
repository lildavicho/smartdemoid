-- =====================================================
-- SUPABASE MIGRATION: BLOCK 10 - COMPLETE
-- Run this entire file in Supabase SQL Editor
-- =====================================================

-- =====================================================
-- 1. DEVICE BINDINGS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS device_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked')),
    bound_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint: only one active binding per teacher
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_bindings_active_teacher 
    ON device_bindings(teacher_id) 
    WHERE status = 'active';

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_device_bindings_device_id ON device_bindings(device_id);
CREATE INDEX IF NOT EXISTS idx_device_bindings_teacher_id ON device_bindings(teacher_id);
CREATE INDEX IF NOT EXISTS idx_device_bindings_status ON device_bindings(status);

-- RLS: Service role only
ALTER TABLE device_bindings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS device_bindings_service_role ON device_bindings;
CREATE POLICY device_bindings_service_role 
    ON device_bindings 
    FOR ALL 
    USING (true);

-- Trigger function for updated_at (create only if not exists)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update updated_at
DROP TRIGGER IF EXISTS device_bindings_updated_at ON device_bindings;
CREATE TRIGGER device_bindings_updated_at
    BEFORE UPDATE ON device_bindings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE device_bindings IS 'Tracks device binding per teacher for security';
COMMENT ON COLUMN device_bindings.status IS 'active or revoked';
COMMENT ON COLUMN device_bindings.metadata IS 'Additional device info (model, OS, etc)';

-- =====================================================
-- 2. ATTENDANCE EVENTS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS attendance_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT NOT NULL,
    student_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    confidence REAL,
    source TEXT DEFAULT 'edge' CHECK (source IN ('edge', 'manual', 'import')),
    idempotency_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint for idempotency
CREATE UNIQUE INDEX IF NOT EXISTS idx_attendance_events_idempotency 
    ON attendance_events(session_id, idempotency_key);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_attendance_events_session ON attendance_events(session_id);
CREATE INDEX IF NOT EXISTS idx_attendance_events_student ON attendance_events(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_events_occurred_at ON attendance_events(occurred_at);

-- RLS: Service role only
ALTER TABLE attendance_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS attendance_events_service_role ON attendance_events;
CREATE POLICY attendance_events_service_role 
    ON attendance_events 
    FOR ALL 
    USING (true);

-- Comments
COMMENT ON TABLE attendance_events IS 'Idempotent attendance event log';
COMMENT ON COLUMN attendance_events.idempotency_key IS 'Unique key per session to prevent duplicates (use localId from Android)';
COMMENT ON COLUMN attendance_events.source IS 'Origin of event: edge (Android), manual, or import';
COMMENT ON COLUMN attendance_events.confidence IS 'AI recognition confidence (0.0-1.0)';

-- =====================================================
-- 3. SESSION FINALIZATIONS TABLE
-- =====================================================

CREATE TABLE IF NOT EXISTS session_finalizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT NOT NULL,
    teacher_id TEXT NOT NULL,
    course_id TEXT NOT NULL,
    records_json JSONB NOT NULL,
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'applied', 'rejected')),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint for idempotency
CREATE UNIQUE INDEX IF NOT EXISTS idx_session_finalizations_idempotency 
    ON session_finalizations(session_id, idempotency_key);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_session_finalizations_session ON session_finalizations(session_id);
CREATE INDEX IF NOT EXISTS idx_session_finalizations_teacher ON session_finalizations(teacher_id);
CREATE INDEX IF NOT EXISTS idx_session_finalizations_status ON session_finalizations(status);
CREATE INDEX IF NOT EXISTS idx_session_finalizations_created_at ON session_finalizations(created_at);

-- RLS: Service role only
ALTER TABLE session_finalizations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS session_finalizations_service_role ON session_finalizations;
CREATE POLICY session_finalizations_service_role 
    ON session_finalizations 
    FOR ALL 
    USING (true);

-- Trigger to update updated_at
DROP TRIGGER IF EXISTS session_finalizations_updated_at ON session_finalizations;
CREATE TRIGGER session_finalizations_updated_at
    BEFORE UPDATE ON session_finalizations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE session_finalizations IS 'Idempotent session finalization requests';
COMMENT ON COLUMN session_finalizations.idempotency_key IS 'Unique key per session to prevent duplicate finalization (use pendingSessionUpdate.localId from Android)';
COMMENT ON COLUMN session_finalizations.records_json IS 'JSON array of attendance records';
COMMENT ON COLUMN session_finalizations.status IS 'pending, applied, or rejected';
COMMENT ON COLUMN session_finalizations.error_message IS 'Error details if status=rejected';

-- =====================================================
-- VERIFICATION
-- =====================================================

-- Check tables created
SELECT 
    'device_bindings' as table_name, 
    COUNT(*) as row_count 
FROM device_bindings
UNION ALL
SELECT 
    'attendance_events', 
    COUNT(*) 
FROM attendance_events
UNION ALL
SELECT 
    'session_finalizations', 
    COUNT(*) 
FROM session_finalizations;

-- Check indexes
SELECT 
    tablename, 
    indexname
FROM pg_indexes 
WHERE tablename IN ('device_bindings', 'attendance_events', 'session_finalizations')
ORDER BY tablename, indexname;

-- =====================================================
-- MIGRATION COMPLETE
-- =====================================================
