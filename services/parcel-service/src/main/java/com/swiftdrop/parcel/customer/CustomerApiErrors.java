package com.swiftdrop.parcel.customer;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = CustomerBookingController.class)
public class CustomerApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException exception) {
        return response(exception.getStatusCode(), exception.getReason());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid(Exception exception) {
        // Never echo rejected values, addresses, JWTs, SQL or stack traces.
        return response(HttpStatus.BAD_REQUEST, "Invalid request. Check required fields and supported values.");
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> conflict(Exception exception) {
        return response(HttpStatus.CONFLICT, "Parcel changed. Refresh and try again.");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete the request");
    }
    private ResponseEntity<ProblemDetail> response(HttpStatusCode status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail == null ? "Request failed" : detail));
    }
}
