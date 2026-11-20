package com.swiftdrop.courier.dto.response;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message) {
    public static ErrorResponse notFound(String message) {
        return new ErrorResponse(Instant.now(), 404, "Not Found", message);
    }
}
