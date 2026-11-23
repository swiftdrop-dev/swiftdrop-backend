package com.swiftdrop.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts exceptions into consistent JSON error responses.
 * Never exposes internal stack traces or sensitive details to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Validation errors (e.g. blank email, short password) ─

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    // ── Account deactivated ───────────────────────────────────

    @ExceptionHandler(AccountDeactivatedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountDeactivated(
            AccountDeactivatedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── Google OAuth token validation failure ────────────────
    @ExceptionHandler(GoogleTokenValidationException.class)
    public ResponseEntity<Map<String, Object>> handleGoogleTokenValidation(
            GoogleTokenValidationException ex) {
        return errorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ── Missing configuration or illegal state ─────────────────
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Resource not found ─────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }


    // ── Business-rule errors (duplicate email, bad credentials, illegal actions) ─

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        String msg = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        HttpStatus status;
        if (msg.contains("already registered")) {
            status = HttpStatus.CONFLICT;
        } else if (msg.contains("cannot") || msg.contains("Invalid role") || msg.contains("required")) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.UNAUTHORIZED;
        }

        return errorResponse(status, msg);
    }

    // ── Catch-all — do not expose internal details ─────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ── Helper ─────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

