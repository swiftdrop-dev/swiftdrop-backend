-- Prepared only. No automatic execution; not an authoritative parcel/order table.
CREATE TABLE merchant_order_requests (
 id UUID PRIMARY KEY,
 merchant_id UUID NOT NULL REFERENCES merchants(id),
 credential_id UUID NOT NULL REFERENCES merchant_api_credentials(id),
 operation VARCHAR(30) NOT NULL CHECK (operation = 'CREATE_ORDER'),
 key_digest VARCHAR(64) NOT NULL CHECK (key_digest ~ '^[0-9a-f]{64}$'),
 fingerprint_version VARCHAR(20) NOT NULL CHECK (fingerprint_version = 'v1'),
 request_fingerprint VARCHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
 downstream_request_id UUID NOT NULL UNIQUE,
 state VARCHAR(20) NOT NULL CHECK (state IN ('IN_PROGRESS','SUCCEEDED','REJECTED','UNCERTAIN')),
 attempt_token UUID NOT NULL,
 lease_until TIMESTAMP WITH TIME ZONE NOT NULL,
 parcel_id VARCHAR(64),
 response_version VARCHAR(20),
 http_status INTEGER,
 response_snapshot TEXT,
 location VARCHAR(300),
 failure_code VARCHAR(60),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 completed_at TIMESTAMP WITH TIME ZONE,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE (merchant_id,operation,key_digest),
 CHECK ((state='SUCCEEDED' AND http_status=201 AND parcel_id IS NOT NULL AND response_snapshot IS NOT NULL AND location IS NOT NULL AND completed_at IS NOT NULL AND response_version='v1')
 OR (state='REJECTED' AND http_status IN (400,409,422) AND failure_code IS NOT NULL AND completed_at IS NOT NULL AND response_version='v1')
 OR (state IN ('IN_PROGRESS','UNCERTAIN') AND http_status IS NULL AND completed_at IS NULL)),
 CHECK (lease_until > created_at)
);
CREATE INDEX ix_merchant_order_request_leases ON merchant_order_requests(state,lease_until);
