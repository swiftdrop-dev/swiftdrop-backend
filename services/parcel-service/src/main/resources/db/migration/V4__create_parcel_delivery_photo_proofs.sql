-- The assignment primary key guarantees uniqueness of this expanded key even on
-- existing data. The composite FK below uses it to bind parcel and courier too.
ALTER TABLE public.parcel_assignments
    ADD CONSTRAINT uq_parcel_assignments_id_parcel_courier
    UNIQUE (id, parcel_id, courier_id);

CREATE TABLE public.parcel_delivery_photo_proofs (
    id UUID PRIMARY KEY,
    parcel_id VARCHAR(64) NOT NULL,
    assignment_id BIGINT NOT NULL,
    courier_id VARCHAR(64) NOT NULL,
    cloudinary_asset_id VARCHAR(128) NOT NULL,
    cloudinary_public_id VARCHAR(255) NOT NULL,
    verified_format VARCHAR(8) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256_digest BYTEA,
    uploaded_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT fk_parcel_photo_proof_parcel
        FOREIGN KEY (parcel_id) REFERENCES public.parcels (id),
    CONSTRAINT fk_parcel_photo_proof_assignment_tuple
        FOREIGN KEY (assignment_id, parcel_id, courier_id)
        REFERENCES public.parcel_assignments (id, parcel_id, courier_id),
    CONSTRAINT uq_parcel_photo_proof_asset UNIQUE (cloudinary_asset_id),
    CONSTRAINT chk_parcel_photo_proof_size CHECK (byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT chk_parcel_photo_proof_format CHECK (
        (verified_format = 'jpeg' AND content_type = 'image/jpeg') OR
        (verified_format = 'png' AND content_type = 'image/png')
    ),
    CONSTRAINT chk_parcel_photo_proof_digest_length CHECK (
        sha256_digest IS NULL OR octet_length(sha256_digest) = 32
    ),
    CONSTRAINT chk_parcel_photo_proof_consumed_verified CHECK (
        consumed_at IS NULL OR verified_at IS NOT NULL
    ),
    CONSTRAINT chk_parcel_photo_proof_not_consumed_and_invalidated CHECK (
        consumed_at IS NULL OR invalidated_at IS NULL
    ),
    CONSTRAINT chk_parcel_photo_proof_lifecycle_timestamps CHECK (
        verified_at >= uploaded_at
        AND (consumed_at IS NULL OR (consumed_at >= uploaded_at AND consumed_at >= verified_at))
        AND (invalidated_at IS NULL OR invalidated_at >= uploaded_at)
    )
);

CREATE INDEX idx_parcel_photo_proof_parcel_assignment
    ON public.parcel_delivery_photo_proofs (parcel_id, assignment_id, uploaded_at, id);

CREATE UNIQUE INDEX uq_parcel_photo_proof_one_active_per_assignment
    ON public.parcel_delivery_photo_proofs (assignment_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;
