CREATE TABLE public.parcel_delivery_otp_challenges (
    id UUID PRIMARY KEY,
    parcel_id VARCHAR(64) NOT NULL,
    assignment_id BIGINT NOT NULL,
    courier_id VARCHAR(64) NOT NULL,
    code_hmac BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    verified_at TIMESTAMPTZ,
    otp_consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    proof_consumed_at TIMESTAMPTZ,
    CONSTRAINT fk_parcel_otp_parcel
        FOREIGN KEY (parcel_id) REFERENCES public.parcels (id),
    CONSTRAINT fk_parcel_otp_assignment
        FOREIGN KEY (assignment_id) REFERENCES public.parcel_assignments (id),
    CONSTRAINT chk_parcel_otp_hash_length
        CHECK (octet_length(code_hmac) = 32),
    CONSTRAINT chk_parcel_otp_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT chk_parcel_otp_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_parcel_otp_verified_consumed_together
        CHECK ((verified_at IS NULL) = (otp_consumed_at IS NULL))
);

CREATE INDEX idx_parcel_otp_assignment_created
    ON public.parcel_delivery_otp_challenges (parcel_id, assignment_id, created_at DESC, id DESC);

CREATE INDEX idx_parcel_otp_expiry
    ON public.parcel_delivery_otp_challenges (expires_at)
    WHERE invalidated_at IS NULL AND proof_consumed_at IS NULL;

CREATE UNIQUE INDEX uq_parcel_otp_one_usable_per_parcel
    ON public.parcel_delivery_otp_challenges (parcel_id)
    WHERE invalidated_at IS NULL
      AND proof_consumed_at IS NULL
      AND attempt_count < 5;
