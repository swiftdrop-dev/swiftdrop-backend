-- =============================================================================
-- SwiftDrop Merchant Service Schema
-- Database: merchant_db
-- Version: V2
-- Description: Merchant Webhook Endpoints, Deliveries, and Delivery Attempts
-- =============================================================================

CREATE TABLE IF NOT EXISTS merchant_webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    url VARCHAR(1024) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    subscribed_events VARCHAR(500) NOT NULL DEFAULT 'PARCEL_CREATED,PARCEL_ASSIGNED,PARCEL_PICKED_UP,PARCEL_IN_TRANSIT,PARCEL_OUT_FOR_DELIVERY,PARCEL_DELIVERY_FAILED,PARCEL_DELIVERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_endpoints_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_merchant_id ON merchant_webhook_endpoints (merchant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_active ON merchant_webhook_endpoints (is_active);

CREATE TABLE IF NOT EXISTS merchant_webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(64),
    parcel_id VARCHAR(64),
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ,
    last_http_status INTEGER,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ,
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES merchant_webhook_endpoints (id) ON DELETE CASCADE,
    CONSTRAINT fk_webhook_deliveries_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT uq_webhook_endpoint_event UNIQUE (endpoint_id, event_id),
    CONSTRAINT chk_webhook_delivery_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'RETRYING'))
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_merchant_created ON merchant_webhook_deliveries (merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_endpoint ON merchant_webhook_deliveries (endpoint_id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_retry ON merchant_webhook_deliveries (status, next_retry_at);

CREATE TABLE IF NOT EXISTS merchant_webhook_delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    http_status INTEGER,
    response_body TEXT,
    error_message TEXT,
    duration_ms BIGINT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_attempts_delivery FOREIGN KEY (delivery_id) REFERENCES merchant_webhook_deliveries (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_webhook_attempts_delivery_id ON merchant_webhook_delivery_attempts (delivery_id, attempt_number ASC);
