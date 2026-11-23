CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    parcel_id VARCHAR(64) NOT NULL,
    tracking_number VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(32),
    channel VARCHAR(32) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    CONSTRAINT uq_notifications_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread ON notifications (recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_tracking_number ON notifications (tracking_number);
CREATE INDEX IF NOT EXISTS idx_notifications_parcel_id ON notifications (parcel_id);
