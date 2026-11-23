-- SwiftDrop Tracking Service: Initial Schema
CREATE TABLE IF NOT EXISTS public.tracking_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    tracking_number VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    courier_id VARCHAR(64),
    timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tracking_events_event_id ON public.tracking_events (event_id);
CREATE INDEX IF NOT EXISTS idx_tracking_events_tracking_number ON public.tracking_events (tracking_number, timestamp ASC);
CREATE INDEX IF NOT EXISTS idx_tracking_events_parcel_id ON public.tracking_events (parcel_id, timestamp ASC);
