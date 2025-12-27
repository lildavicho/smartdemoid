-- Migration 001: Device Bindings Table
-- Purpose: Track device binding per teacher with revocation support
-- Run this in Supabase SQL Editor

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
CREATE UNIQUE INDEX idx_device_bindings_active_teacher 
    ON device_bindings(teacher_id) 
    WHERE status = 'active';

-- Performance indexes
CREATE INDEX idx_device_bindings_device_id ON device_bindings(device_id);
CREATE INDEX idx_device_bindings_teacher_id ON device_bindings(teacher_id);
CREATE INDEX idx_device_bindings_status ON device_bindings(status);

-- RLS: Service role only
ALTER TABLE device_bindings ENABLE ROW LEVEL SECURITY;

CREATE POLICY device_bindings_service_role 
    ON device_bindings 
    FOR ALL 
    USING (auth.role() = 'service_role');

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER device_bindings_updated_at
    BEFORE UPDATE ON device_bindings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE device_bindings IS 'Tracks device binding per teacher for security';
COMMENT ON COLUMN device_bindings.status IS 'active or revoked';
COMMENT ON COLUMN device_bindings.metadata IS 'Additional device info (model, OS, etc)';
