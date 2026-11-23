-- =============================================================================
-- SwiftDrop Auth Service Schema
-- Database: auth_db
-- Version: V2
-- Description: Ensure active column exists with safe default value for existing users
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- Ensure any existing rows receive active = true
UPDATE users SET active = true WHERE active IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_active ON users (active);

