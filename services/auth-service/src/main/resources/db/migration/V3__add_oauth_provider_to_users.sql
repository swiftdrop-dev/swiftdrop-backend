-- =============================================================================
-- SwiftDrop Auth Service Schema
-- Database: auth_db
-- Version: V3
-- Description: Add OAuth provider and provider_id for Google OAuth 2.0
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(32) NOT NULL DEFAULT 'local';
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

-- Ensure all existing rows have provider = 'local'
UPDATE users SET provider = 'local' WHERE provider IS NULL;

-- Index for fast lookup by provider and provider identity
CREATE INDEX IF NOT EXISTS idx_users_provider ON users (provider);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_provider_id ON users (provider, provider_id);
