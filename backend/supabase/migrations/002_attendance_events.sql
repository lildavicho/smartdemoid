-- Migration 002: Attendance Events Table
-- Purpose: Idempotent attendance event tracking
-- Run this in Supabase SQL Editor

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
CREATE UNIQUE INDEX idx_attendance_events_idempotency 
    ON attendance_events(session_id, idempotency_key);

-- Performance indexes
CREATE INDEX idx_attendance_events_session ON attendance_events(session_id);
CREATE INDEX idx_attendance_events_student ON attendance_events(student_id);
CREATE INDEX idx_attendance_events_occurred_at ON attendance_events(occurred_at);

-- RLS: Service role only
ALTER TABLE attendance_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY attendance_events_service_role 
    ON attendance_events 
    FOR ALL 
    USING (auth.role() = 'service_role');

-- Comments
COMMENT ON TABLE attendance_events IS 'Idempotent attendance event log';
COMMENT ON COLUMN attendance_events.idempotency_key IS 'Unique key per session to prevent duplicates (use localId from Android)';
COMMENT ON COLUMN attendance_events.source IS 'Origin of event: edge (Android), manual, or import';
COMMENT ON COLUMN attendance_events.confidence IS 'AI recognition confidence (0.0-1.0)';
