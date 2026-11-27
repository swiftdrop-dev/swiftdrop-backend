-- Preserve existing courier identifiers as text and store an optional authoritative fee.
-- Existing UUID values are converted to their canonical text form. No rows are deleted.

ALTER TABLE dispatch_jobs
    ALTER COLUMN courier_id TYPE VARCHAR(64) USING courier_id::text;

ALTER TABLE dispatch_assignments
    ALTER COLUMN courier_id TYPE VARCHAR(64) USING courier_id::text;

ALTER TABLE courier_status_cache
    ALTER COLUMN courier_id TYPE VARCHAR(64) USING courier_id::text;

ALTER TABLE dispatch_jobs
    ADD COLUMN IF NOT EXISTS total_fee NUMERIC(12, 2);

ALTER TABLE dispatch_jobs
    ALTER COLUMN pickup_latitude DROP NOT NULL,
    ALTER COLUMN pickup_longitude DROP NOT NULL,
    ALTER COLUMN delivery_latitude DROP NOT NULL,
    ALTER COLUMN delivery_longitude DROP NOT NULL;
