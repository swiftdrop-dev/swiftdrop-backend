package com.swiftdrop.parcel.service;

import java.time.Instant;

/** Internal service result; no HTTP endpoint currently exposes this type. */
public record DeliveryCompletionResult(String parcelId, String status, Instant completedAt) {
}
