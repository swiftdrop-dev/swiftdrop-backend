package com.swiftdrop.courier.exception;

import com.swiftdrop.courier.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CourierNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCourierNotFound(CourierNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound(exception.getMessage()));
    }
}
