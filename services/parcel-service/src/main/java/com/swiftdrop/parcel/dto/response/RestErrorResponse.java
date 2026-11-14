package com.swiftdrop.parcel.dto.response;

import java.time.Instant;

public record RestErrorResponse(Instant timestamp, int status, String error, String message) {
}
