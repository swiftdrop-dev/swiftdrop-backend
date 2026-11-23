-- Add customer_id to tracking_events for ownership verification
ALTER TABLE public.tracking_events ADD COLUMN IF NOT EXISTS customer_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_tracking_events_customer_id ON public.tracking_events (customer_id);
