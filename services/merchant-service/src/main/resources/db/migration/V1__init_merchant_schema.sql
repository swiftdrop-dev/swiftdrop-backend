-- =============================================================================
-- SwiftDrop Merchant Service Schema
-- Database: merchant_db
-- Version: V1
-- Description: Initial schema for merchant onboarding, KYC, and credentials
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. merchants
-- -----------------------------------------------------------------------------
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    business_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    registration_number VARCHAR(100) NOT NULL UNIQUE,
    tax_id VARCHAR(100),
    business_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    pricing_tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    rejection_reason TEXT,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    webhook_url VARCHAR(1024),
    webhook_secret VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_merchant_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED', 'ACTIVE')),
    CONSTRAINT chk_merchant_tier CHECK (pricing_tier IN ('STANDARD', 'SILVER', 'GOLD', 'ENTERPRISE'))
);

CREATE INDEX idx_merchants_user_id ON merchants (user_id);
CREATE INDEX idx_merchants_status ON merchants (status);
CREATE INDEX idx_merchants_reg_num ON merchants (registration_number);

-- -----------------------------------------------------------------------------
-- 2. merchant_contacts
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    contact_type VARCHAR(50) NOT NULL DEFAULT 'PRIMARY',
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(255) NOT NULL,
    designation VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contacts_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT chk_contact_type CHECK (contact_type IN ('PRIMARY', 'FINANCE', 'OPERATIONS', 'TECHNICAL'))
);

CREATE INDEX idx_merchant_contacts_merchant_id ON merchant_contacts (merchant_id);

-- -----------------------------------------------------------------------------
-- 3. merchant_addresses
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    address_label VARCHAR(100) NOT NULL,
    street_address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20),
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    is_primary_pickup BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_addresses_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT chk_merchant_address_lat CHECK (latitude BETWEEN -90.0 AND 90.0),
    CONSTRAINT chk_merchant_address_lng CHECK (longitude BETWEEN -180.0 AND 180.0)
);

CREATE INDEX idx_merchant_addresses_merchant_id ON merchant_addresses (merchant_id);
CREATE INDEX idx_merchant_addresses_city ON merchant_addresses (city);

-- -----------------------------------------------------------------------------
-- 4. merchant_bank_accounts
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    bank_name VARCHAR(150) NOT NULL,
    branch_name VARCHAR(150) NOT NULL,
    branch_code VARCHAR(20),
    account_number VARCHAR(50) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    settlement_cycle VARCHAR(50) NOT NULL DEFAULT 'WEEKLY',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_accounts_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT chk_settlement_cycle CHECK (settlement_cycle IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_merchant_bank_merchant_id ON merchant_bank_accounts (merchant_id);

-- -----------------------------------------------------------------------------
-- 5. merchant_documents
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100) NOT NULL,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    verified_by UUID,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMPTZ,
    CONSTRAINT fk_documents_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT chk_document_type CHECK (document_type IN ('BUSINESS_REGISTRATION', 'TAX_CERTIFICATE', 'DIRECTOR_NIC_PASSPORT', 'BANK_PASSBOOK', 'UTILITY_BILL')),
    CONSTRAINT chk_doc_verification_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX idx_merchant_docs_merchant_id ON merchant_documents (merchant_id);
CREATE INDEX idx_merchant_docs_status ON merchant_documents (verification_status);

-- -----------------------------------------------------------------------------
-- 6. merchant_api_credentials
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_api_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    label VARCHAR(100) NOT NULL DEFAULT 'Default API Key',
    scopes VARCHAR(255) NOT NULL DEFAULT 'parcels:create,parcels:read,tracking:read',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    revocation_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_credentials_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE,
    CONSTRAINT chk_api_env CHECK (environment IN ('PRODUCTION', 'SANDBOX'))
);

CREATE INDEX idx_merchant_creds_merchant_id ON merchant_api_credentials (merchant_id);
CREATE INDEX idx_merchant_creds_key_hash ON merchant_api_credentials (key_hash);
CREATE INDEX idx_merchant_creds_prefix ON merchant_api_credentials (key_prefix);

-- -----------------------------------------------------------------------------
-- 7. merchant_audit_logs
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_by UUID NOT NULL,
    performed_by_role VARCHAR(50) NOT NULL,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id) ON DELETE CASCADE
);

CREATE INDEX idx_merchant_audit_merchant_id ON merchant_audit_logs (merchant_id);
CREATE INDEX idx_merchant_audit_action ON merchant_audit_logs (action);
CREATE INDEX idx_merchant_audit_created_at ON merchant_audit_logs (created_at);

-- -----------------------------------------------------------------------------
-- 8. outbox_events (Transactional Outbox Pattern)
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_merchant_outbox_status_created ON outbox_events (status, created_at);
