-- SwiftDrop Courier Service: Initial Schema
-- V1 creates the base couriers table.

CREATE TABLE IF NOT EXISTS public.couriers (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(32),
    vehicle_type VARCHAR(32),
    vehicle_plate VARCHAR(32),
    rating NUMERIC(3, 2) DEFAULT 5.0,
    is_available BOOLEAN DEFAULT TRUE,
    deliveries_today INTEGER DEFAULT 0,
    total_deliveries INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_couriers_user_id ON public.couriers (user_id);
CREATE INDEX IF NOT EXISTS idx_couriers_is_available ON public.couriers (is_available);
