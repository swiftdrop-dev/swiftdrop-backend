package com.swiftdrop.parcel.exception;

import com.swiftdrop.parcel.dto.response.RestErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class ParcelRestExceptionHandler {

    @ExceptionHandler(ParcelNotFoundException.class)
    public ResponseEntity<RestErrorResponse> handleNotFound(ParcelNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AssignmentConflictException.class)
    public ResponseEntity<RestErrorResponse> handleConflict(AssignmentConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ParcelStatusConflictException.class)
    public ResponseEntity<RestErrorResponse> handleStatusConflict(ParcelStatusConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CourierAssignmentMismatchException.class)
    public ResponseEntity<RestErrorResponse> handleCourierAssignmentMismatch(
            CourierAssignmentMismatchException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({
            InvalidRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<RestErrorResponse> handleBadRequest(Exception exception) {
        String message = exception instanceof InvalidRequestException
                ? exception.getMessage()
                : "Request parameters or body are invalid";
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<RestErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new RestErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
