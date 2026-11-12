-- Prepared only. No runner is installed; requires separately approved execution.
CREATE TABLE merchant_api_credentials (
 id UUID PRIMARY KEY,
 merchant_id UUID NOT NULL REFERENCES merchants(id),
 public_id VARCHAR(22) NOT NULL UNIQUE,
 secret_digest VARCHAR(64) NOT NULL CHECK (secret_digest ~ '^[0-9a-f]{64}$'),
 hash_version VARCHAR(20) NOT NULL CHECK (hash_version = 'sha256-v1'),
 name VARCHAR(100) NOT NULL,
 created_by VARCHAR(255) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 revoked_at TIMESTAMP WITH TIME ZONE,
 revoked_by VARCHAR(255),
 revocation_reason VARCHAR(40),
 replacement_id UUID UNIQUE REFERENCES merchant_api_credentials(id),
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '90 days'),
 CHECK ((revoked_at IS NULL AND revoked_by IS NULL AND revocation_reason IS NULL AND replacement_id IS NULL)
     OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revocation_reason IS NOT NULL)),
 CHECK (replacement_id IS NULL OR (replacement_id <> id AND revocation_reason = 'ROTATED'))
);
CREATE INDEX ix_merchant_api_credentials_owner_created ON merchant_api_credentials(merchant_id,created_at,id);
CREATE TABLE merchant_api_credential_scopes (
 credential_id UUID NOT NULL REFERENCES merchant_api_credentials(id),
 scope VARCHAR(40) NOT NULL CHECK (scope IN ('orders:read','orders:write')),
 PRIMARY KEY (credential_id,scope)
);
