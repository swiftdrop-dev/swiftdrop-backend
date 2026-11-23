-- =============================================================================
-- SwiftDrop Dispatch Service Schema
-- Database: dispatch_db
-- Version: V1
-- Description: Initial schema for dispatch console, job assignment, zones, and local projections
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. dispatch_zones
-- -----------------------------------------------------------------------------
CREATE TABLE dispatch_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_code VARCHAR(50) NOT NULL UNIQUE,
    zone_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    center_latitude NUMERIC(10, 7) NOT NULL,
    center_longitude NUMERIC(10, 7) NOT NULL,
    radius_km NUMERIC(6, 2) NOT NULL DEFAULT 10.00,
    assigned_dispatcher_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_zone_lat CHECK (center_latitude BETWEEN -90.0 AND 90.0),
    CONSTRAINT chk_zone_lng CHECK (center_longitude BETWEEN -180.0 AND 180.0)
);

CREATE INDEX idx_dispatch_zones_code ON dispatch_zones (zone_code);
CREATE INDEX idx_dispatch_zones_dispatcher ON dispatch_zones (assigned_dispatcher_id);

-- -----------------------------------------------------------------------------
-- 2. dispatch_jobs
-- -----------------------------------------------------------------------------
CREATE TABLE dispatch_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id UUID NOT NULL UNIQUE,
    tracking_number VARCHAR(100) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL,
    zone_id UUID REFERENCES dispatch_zones (id) ON DELETE SET NULL,
    courier_id UUID,
    pickup_street_address VARCHAR(500) NOT NULL,
    pickup_city VARCHAR(100) NOT NULL,
    pickup_latitude NUMERIC(10, 7) NOT NULL,
    pickup_longitude NUMERIC(10, 7) NOT NULL,
    delivery_street_address VARCHAR(500) NOT NULL,
    delivery_city VARCHAR(100) NOT NULL,
    delivery_latitude NUMERIC(10, 7) NOT NULL,
    delivery_longitude NUMERIC(10, 7) NOT NULL,
    parcel_weight_kg NUMERIC(6, 2) NOT NULL,
    required_vehicle_type VARCHAR(50) NOT NULL DEFAULT 'MOTORCYCLE',
    priority VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    dispatch_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_DISPATCH',
    assigned_by UUID,
    assigned_at TIMESTAMPTZ,
    offer_expires_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    dispatcher_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_dispatch_vehicle CHECK (required_vehicle_type IN ('BICYCLE', 'MOTORCYCLE', 'CAR', 'VAN', 'TRUCK')),
    CONSTRAINT chk_dispatch_priority CHECK (priority IN ('STANDARD', 'HIGH', 'URGENT')),
    CONSTRAINT chk_dispatch_type CHECK (dispatch_type IN ('MANUAL', 'AUTOMATED')),
    CONSTRAINT chk_dispatch_status CHECK (status IN (
        'PENDING_DISPATCH',
        'ASSIGNED_PENDING_ACCEPTANCE',
        'ACCEPTED',
        'DECLINED',
        'EXPIRED',
        'IN_PROGRESS',
        'COMPLETED',
        'CANCELLED'
    ))
);

CREATE INDEX idx_dispatch_jobs_parcel_id ON dispatch_jobs (parcel_id);
CREATE INDEX idx_dispatch_jobs_tracking_num ON dispatch_jobs (tracking_number);
CREATE INDEX idx_dispatch_jobs_status ON dispatch_jobs (status);
CREATE INDEX idx_dispatch_jobs_courier_id ON dispatch_jobs (courier_id);
CREATE INDEX idx_dispatch_jobs_zone_id ON dispatch_jobs (zone_id);
CREATE INDEX idx_dispatch_jobs_priority ON dispatch_jobs (priority);
CREATE INDEX idx_dispatch_jobs_created_at ON dispatch_jobs (created_at);

-- -----------------------------------------------------------------------------
-- 3. dispatch_assignments
-- -----------------------------------------------------------------------------
CREATE TABLE dispatch_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatch_job_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    decision VARCHAR(50) NOT NULL DEFAULT 'OFFERED',
    decision_reason VARCHAR(255),
    offered_distance_km NUMERIC(6, 2),
    offered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignments_job FOREIGN KEY (dispatch_job_id) REFERENCES dispatch_jobs (id) ON DELETE CASCADE,
    CONSTRAINT chk_assignment_decision CHECK (decision IN ('OFFERED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_dispatch_assign_job_id ON dispatch_assignments (dispatch_job_id);
CREATE INDEX idx_dispatch_assign_courier_id ON dispatch_assignments (courier_id);
CREATE INDEX idx_dispatch_assign_decision ON dispatch_assignments (decision);

-- -----------------------------------------------------------------------------
-- 4. courier_status_cache (Non-authoritative local projection)
-- -----------------------------------------------------------------------------
CREATE TABLE courier_status_cache (
    courier_id UUID PRIMARY KEY,
    courier_name VARCHAR(255) NOT NULL,
    courier_phone VARCHAR(32) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL DEFAULT 'MOTORCYCLE',
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    is_available BOOLEAN NOT NULL DEFAULT FALSE,
    current_latitude NUMERIC(10, 7),
    current_longitude NUMERIC(10, 7),
    active_deliveries_count INTEGER NOT NULL DEFAULT 0,
    max_capacity INTEGER NOT NULL DEFAULT 4,
    last_location_updated_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    projection_version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT chk_courier_cache_vehicle CHECK (vehicle_type IN ('BICYCLE', 'MOTORCYCLE', 'CAR', 'VAN', 'TRUCK'))
);

CREATE INDEX idx_courier_cache_avail ON courier_status_cache (is_online, is_available);
CREATE INDEX idx_courier_cache_vehicle ON courier_status_cache (vehicle_type);

-- -----------------------------------------------------------------------------
-- 5. outbox_events (Transactional Outbox Pattern)
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

CREATE INDEX idx_dispatch_outbox_status_created ON outbox_events (status, created_at);
