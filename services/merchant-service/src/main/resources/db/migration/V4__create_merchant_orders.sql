-- =============================================================================
-- SwiftDrop Merchant Service Schema
-- Database: merchant_db
-- Version: V4
-- Description: Dedicated table for merchant delivery orders and idempotency tracking
-- =============================================================================

CREATE TABLE IF NOT EXISTS merchant_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    merchant_order_ref VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    parcel_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    recipient_name VARCHAR(255) NOT NULL,
    recipient_phone VARCHAR(50) NOT NULL,
    recipient_address TEXT NOT NULL,
    recipient_city VARCHAR(100),
    recipient_district VARCHAR(100),
    recipient_postal_code VARCHAR(20),
    pickup_address TEXT NOT NULL,
    pickup_city VARCHAR(100),
    pickup_district VARCHAR(100),
    pickup_postal_code VARCHAR(20),
    description TEXT,
    weight_kg NUMERIC(8, 3) NOT NULL,
    parcel_size VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    declared_value NUMERIC(12, 2),
    fragile BOOLEAN NOT NULL DEFAULT FALSE,
    special_instructions TEXT,
    payment_method VARCHAR(50) NOT NULL DEFAULT 'COD',
    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'LKR',
    idempotency_key VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_merchant_orders_merchant_id ON merchant_orders (merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_ref ON merchant_orders (merchant_id, merchant_order_ref);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_tracking_number ON merchant_orders (tracking_number);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_parcel_id ON merchant_orders (parcel_id);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_idempotency ON merchant_orders (merchant_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_created_at ON merchant_orders (created_at DESC);
