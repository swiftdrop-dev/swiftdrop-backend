-- =============================================================================
-- SwiftDrop Merchant Service Schema
-- Database: merchant_db
-- Version: V3
-- Description: Webhook Audit Trail Retention, Soft Deletion, and Concurrency Hardening
-- =============================================================================

-- Add soft-delete timestamp to webhook endpoints to preserve delivery audit trail
ALTER TABLE merchant_webhook_endpoints
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_deleted_at
    ON merchant_webhook_endpoints (deleted_at);

-- Add claiming timestamp to webhook deliveries for atomic worker dispatching
ALTER TABLE merchant_webhook_deliveries
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

-- Drop and recreate the status check constraint to include 'PROCESSING'
ALTER TABLE merchant_webhook_deliveries
    DROP CONSTRAINT IF EXISTS chk_webhook_delivery_status;

ALTER TABLE merchant_webhook_deliveries
    ADD CONSTRAINT chk_webhook_delivery_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'RETRYING', 'PROCESSING'));

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_claiming
    ON merchant_webhook_deliveries (status, claimed_at);
