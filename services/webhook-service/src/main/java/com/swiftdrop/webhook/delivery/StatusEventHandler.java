package com.swiftdrop.webhook.delivery;

/** Broker adapter boundary for the existing proposed status-event contract.
 * M9 must agree on the contract before enabling real parcel events. */
public interface StatusEventHandler {
    void ingest(String payload);
}
