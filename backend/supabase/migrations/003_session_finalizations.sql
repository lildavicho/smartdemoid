-- Migration 003: Session Finalizations Table
-- Purpose: Idempotent session finalization tracking
-- Run this in Supabase SQL Editor

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
CREATE UNIQUE INDEX idx_session_finalizations_idempotency 
    ON session_finalizations(session_id, idempotency_key);

-- Performance indexes
CREATE INDEX idx_session_finalizations_session ON session_finalizations(session_id);
CREATE INDEX idx_session_finalizations_teacher ON session_finalizations(teacher_id);
CREATE INDEX idx_session_finalizations_status ON session_finalizations(status);
CREATE INDEX idx_session_finalizations_created_at ON session_finalizations(created_at);

-- RLS: Service role only
ALTER TABLE session_finalizations ENABLE ROW LEVEL SECURITY;

CREATE POLICY session_finalizations_service_role 
    ON session_finalizations 
    FOR ALL 
    USING (auth.role() = 'service_role');

-- Trigger to update updated_at
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
