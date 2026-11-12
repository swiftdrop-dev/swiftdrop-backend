CREATE TABLE webhooks_dev.subscriptions (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(100) NOT NULL,
    callback_url VARCHAR(2048) NOT NULL,
    event_type VARCHAR(100) NOT NULL CHECK (event_type = 'parcel.status.changed'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX subscriptions_merchant_active_idx
    ON webhooks_dev.subscriptions (merchant_id, is_active);
