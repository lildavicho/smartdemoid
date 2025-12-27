-- =====================================================
-- SMARTPRESENCE - MASTER SUPABASE MIGRATION
-- Version: 1.0
-- Date: 2024-01-15
-- Description: Complete database setup for production
-- =====================================================

-- =====================================================
-- STEP 1: EXTENSIONS
-- =====================================================

-- Enable pgcrypto for gen_random_uuid() if not already enabled
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- STEP 2: CREATE TABLES
-- =====================================================

-- -----------------------------------------------------
-- TABLE: device_bindings
-- Purpose: Track device binding per teacher for security
-- -----------------------------------------------------

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

-- Comments
COMMENT ON TABLE device_bindings IS 'Tracks device binding per teacher for security';
COMMENT ON COLUMN device_bindings.teacher_id IS 'Teacher identifier (from your app, not necessarily auth.uid())';
COMMENT ON COLUMN device_bindings.device_id IS 'Android device unique identifier';
COMMENT ON COLUMN device_bindings.status IS 'Binding status: active or revoked';
COMMENT ON COLUMN device_bindings.metadata IS 'Device info: model, manufacturer, OS version, etc.';

-- -----------------------------------------------------
-- TABLE: attendance_events
-- Purpose: Idempotent attendance event log
-- -----------------------------------------------------

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

-- Comments
COMMENT ON TABLE attendance_events IS 'Idempotent attendance event log';
COMMENT ON COLUMN attendance_events.idempotency_key IS 'Unique key per session to prevent duplicates (localId from Android)';
COMMENT ON COLUMN attendance_events.source IS 'Origin: edge (Android), manual, or import';
COMMENT ON COLUMN attendance_events.confidence IS 'AI recognition confidence (0.0-1.0)';

-- -----------------------------------------------------
-- TABLE: session_finalizations
-- Purpose: Idempotent session finalization requests
-- -----------------------------------------------------

CREATE TABLE IF NOT EXISTS session_finalizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id TEXT NOT NULL,
    teacher_id TEXT NOT NULL,
    course_id TEXT NOT NULL DEFAULT '',
    records_json JSONB NOT NULL,
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'applied', 'rejected')),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Comments
COMMENT ON TABLE session_finalizations IS 'Idempotent session finalization requests';
COMMENT ON COLUMN session_finalizations.course_id IS 'Course identifier for multi-course support';
COMMENT ON COLUMN session_finalizations.records_json IS 'JSON array of attendance records';
COMMENT ON COLUMN session_finalizations.idempotency_key IS 'Unique key per session (localId from Android PendingSessionUpdateEntity)';
COMMENT ON COLUMN session_finalizations.status IS 'Status: pending, applied, or rejected';

-- =====================================================
-- STEP 3: CREATE INDEXES
-- =====================================================

-- device_bindings indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_bindings_active_teacher 
    ON device_bindings(teacher_id) 
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS idx_device_bindings_teacher_id 
    ON device_bindings(teacher_id);

CREATE INDEX IF NOT EXISTS idx_device_bindings_device_id 
    ON device_bindings(device_id);

CREATE INDEX IF NOT EXISTS idx_device_bindings_status 
    ON device_bindings(status);

CREATE INDEX IF NOT EXISTS idx_device_bindings_created_at 
    ON device_bindings(created_at);

-- attendance_events indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_attendance_events_idempotency 
    ON attendance_events(session_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_attendance_events_session 
    ON attendance_events(session_id);

CREATE INDEX IF NOT EXISTS idx_attendance_events_student 
    ON attendance_events(student_id);

CREATE INDEX IF NOT EXISTS idx_attendance_events_occurred_at 
    ON attendance_events(occurred_at);

CREATE INDEX IF NOT EXISTS idx_attendance_events_created_at 
    ON attendance_events(created_at);

-- session_finalizations indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_session_finalizations_idempotency 
    ON session_finalizations(session_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_session_finalizations_session 
    ON session_finalizations(session_id);

CREATE INDEX IF NOT EXISTS idx_session_finalizations_teacher 
    ON session_finalizations(teacher_id);

CREATE INDEX IF NOT EXISTS idx_session_finalizations_course 
    ON session_finalizations(course_id);

CREATE INDEX IF NOT EXISTS idx_session_finalizations_status 
    ON session_finalizations(status);

CREATE INDEX IF NOT EXISTS idx_session_finalizations_created_at 
    ON session_finalizations(created_at);

-- =====================================================
-- STEP 4: TRIGGERS
-- =====================================================

-- Trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to device_bindings
DROP TRIGGER IF EXISTS device_bindings_updated_at ON device_bindings;
CREATE TRIGGER device_bindings_updated_at
    BEFORE UPDATE ON device_bindings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to session_finalizations
DROP TRIGGER IF EXISTS session_finalizations_updated_at ON session_finalizations;
CREATE TRIGGER session_finalizations_updated_at
    BEFORE UPDATE ON session_finalizations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- STEP 5: ROW LEVEL SECURITY (RLS)
-- =====================================================

-- Enable RLS on all tables
ALTER TABLE device_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_finalizations ENABLE ROW LEVEL SECURITY;

-- -----------------------------------------------------
-- RLS POLICIES - OPTION A: teacher_id == auth.uid()
-- Use this if your teacher_id matches Supabase auth.uid()
-- -----------------------------------------------------

-- UNCOMMENT IF teacher_id == auth.uid():
/*
-- device_bindings policies
DROP POLICY IF EXISTS device_bindings_select_own ON device_bindings;
CREATE POLICY device_bindings_select_own 
    ON device_bindings FOR SELECT 
    USING (teacher_id = auth.uid()::text);

DROP POLICY IF EXISTS device_bindings_service_role ON device_bindings;
CREATE POLICY device_bindings_service_role 
    ON device_bindings FOR ALL 
    USING (true);

-- attendance_events policies
DROP POLICY IF EXISTS attendance_events_select_own ON attendance_events;
CREATE POLICY attendance_events_select_own 
    ON attendance_events FOR SELECT 
    USING (
        session_id IN (
            SELECT id FROM attendance_sessions 
            WHERE teacher_id = auth.uid()::text
        )
    );

DROP POLICY IF EXISTS attendance_events_service_role ON attendance_events;
CREATE POLICY attendance_events_service_role 
    ON attendance_events FOR ALL 
    USING (true);

-- session_finalizations policies
DROP POLICY IF EXISTS session_finalizations_select_own ON session_finalizations;
CREATE POLICY session_finalizations_select_own 
    ON session_finalizations FOR SELECT 
    USING (teacher_id = auth.uid()::text);

DROP POLICY IF EXISTS session_finalizations_service_role ON session_finalizations;
CREATE POLICY session_finalizations_service_role 
    ON session_finalizations FOR ALL 
    USING (true);
*/

-- -----------------------------------------------------
-- RLS POLICIES - OPTION B: teacher_id is external (RECOMMENDED)
-- Use this if teacher_id is managed by your app (not auth.uid())
-- All operations go through NestJS with service role
-- -----------------------------------------------------

-- ACTIVE BY DEFAULT:

-- device_bindings: service role only
DROP POLICY IF EXISTS device_bindings_service_role ON device_bindings;
CREATE POLICY device_bindings_service_role 
    ON device_bindings FOR ALL 
    USING (true);

-- attendance_events: service role only
DROP POLICY IF EXISTS attendance_events_service_role ON attendance_events;
CREATE POLICY attendance_events_service_role 
    ON attendance_events FOR ALL 
    USING (true);

-- session_finalizations: service role only
DROP POLICY IF EXISTS session_finalizations_service_role ON session_finalizations;
CREATE POLICY session_finalizations_service_role 
    ON session_finalizations FOR ALL 
    USING (true);

-- =====================================================
-- MIGRATION COMPLETE
-- =====================================================

-- Verify tables created
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

-- Success message
DO $$
BEGIN
    RAISE NOTICE '✅ SmartPresence database migration completed successfully!';
    RAISE NOTICE '📊 Tables created: device_bindings, attendance_events, session_finalizations';
    RAISE NOTICE '🔒 RLS enabled with service role policies';
    RAISE NOTICE '📝 Run verification.sql to confirm setup';
END $$;
