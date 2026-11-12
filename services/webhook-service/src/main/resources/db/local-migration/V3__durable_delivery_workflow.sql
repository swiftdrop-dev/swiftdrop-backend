CREATE TABLE webhooks_dev.deliveries (
 id UUID PRIMARY KEY,
 subscription_id UUID NOT NULL REFERENCES webhooks_dev.subscriptions(id),
 event_id VARCHAR(100) NOT NULL,
 callback_url VARCHAR(2048) NOT NULL,
 payload TEXT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PROCESSING','RETRYING','SUCCESS','DEAD_LETTER')),
 attempts INTEGER NOT NULL DEFAULT 0,
 replay_count INTEGER NOT NULL DEFAULT 0,
 response_code INTEGER,
 failure_reason VARCHAR(100),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at TIMESTAMP WITH TIME ZONE,
 next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_until TIMESTAMP WITH TIME ZONE,
 UNIQUE(subscription_id,event_id)
);
CREATE INDEX deliveries_due_idx ON webhooks_dev.deliveries(status,next_attempt_at);
CREATE TABLE webhooks_dev.delivery_attempts (
 id UUID PRIMARY KEY,
 delivery_id UUID NOT NULL REFERENCES webhooks_dev.deliveries(id),
 attempt_number INTEGER NOT NULL,
 replay_number INTEGER NOT NULL,
 response_code INTEGER,
 failure_reason VARCHAR(100),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE webhooks_dev.dead_letter_outbox (
 id UUID PRIMARY KEY,
 delivery_id UUID NOT NULL REFERENCES webhooks_dev.deliveries(id),
 published BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO webhooks_dev.deliveries(id,subscription_id,event_id,callback_url,payload,status,attempts,response_code,failure_reason,created_at,completed_at)
 SELECT d.id,d.subscription_id,CAST(d.id AS VARCHAR),s.callback_url,d.payload,
 CASE WHEN d.status='SUCCESS' THEN 'SUCCESS' WHEN d.status='FAILED' THEN 'DEAD_LETTER' ELSE 'PENDING' END,
 CASE WHEN d.status='PENDING' THEN 0 ELSE 1 END,d.response_code,d.failure_reason,d.created_at,d.completed_at
 FROM webhooks_dev.test_deliveries d JOIN webhooks_dev.subscriptions s ON s.id=d.subscription_id;
