CREATE TABLE webhooks_dev.test_deliveries (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES webhooks_dev.subscriptions(id),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    response_code INTEGER,
    failure_reason VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX test_deliveries_subscription_idx
    ON webhooks_dev.test_deliveries(subscription_id, created_at);
