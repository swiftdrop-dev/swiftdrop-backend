-- SwiftDrop Parcel Service: Schema Alignment for Customer & Merchant Booking
-- Adds sender, pickup, parcel specifications, and payment metadata safely with ADD COLUMN IF NOT EXISTS.

ALTER TABLE public.parcels
    ADD COLUMN IF NOT EXISTS sender_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sender_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS parcel_size VARCHAR(32),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS fragile BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS total_fee NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(32),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_parcels_sender_id ON public.parcels (sender_id);
CREATE INDEX IF NOT EXISTS idx_parcels_created_at ON public.parcels (created_at);
CREATE INDEX IF NOT EXISTS idx_parcels_sender_status ON public.parcels (sender_id, status);
