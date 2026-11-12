-- Prepared only: no migration runner is installed. Requires separately approved execution.
CREATE TABLE merchants (
 id UUID PRIMARY KEY,
 owner_subject VARCHAR(255) NOT NULL CONSTRAINT uk_merchants_owner UNIQUE,
 business_name VARCHAR(200) NOT NULL,
 registration_number VARCHAR(100) NOT NULL,
 contact_name VARCHAR(200) NOT NULL,
 contact_email VARCHAR(254) NOT NULL,
 contact_phone VARCHAR(32) NOT NULL,
 pickup_street VARCHAR(300) NOT NULL,
 pickup_city VARCHAR(100) NOT NULL,
 pickup_district VARCHAR(100) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(255),
 review_reason VARCHAR(1000),
 reviewed_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK ((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL)
 OR (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
 CHECK (status <> 'REJECTED' OR (review_reason IS NOT NULL AND length(trim(review_reason)) > 0))
);
CREATE INDEX ix_merchants_status_created ON merchants(status, created_at);
