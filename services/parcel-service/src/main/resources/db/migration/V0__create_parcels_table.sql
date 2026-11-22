-- SwiftDrop Parcel Service: Initial Base Parcels Table
-- V0 ensures a fresh PostgreSQL database initializes cleanly before V1 foreign keys execute.
-- Uses IF NOT EXISTS to guarantee safe idempotent execution in pre-seeded environments.

CREATE TABLE IF NOT EXISTS public.parcels (
    id VARCHAR(64) PRIMARY KEY,
    tracking_number VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    courier_id VARCHAR(64),
    delivered_at TIMESTAMP WITHOUT TIME ZONE,
    estimated_delivery_date VARCHAR(64),
    recipient_name VARCHAR(255),
    recipient_phone VARCHAR(32),
    recipient_address VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_parcels_tracking_number ON public.parcels (tracking_number);
CREATE INDEX IF NOT EXISTS idx_parcels_courier_id ON public.parcels (courier_id);
CREATE INDEX IF NOT EXISTS idx_parcels_status ON public.parcels (status);
