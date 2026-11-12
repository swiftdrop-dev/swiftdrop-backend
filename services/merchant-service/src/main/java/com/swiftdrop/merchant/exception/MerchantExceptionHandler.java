package com.swiftdrop.merchant.exception;
import com.swiftdrop.merchant.dto.response.MerchantErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.validation.ConstraintViolationException;
@RestControllerAdvice
public class MerchantExceptionHandler {
    @ExceptionHandler(MerchantException.class)
    public ResponseEntity<MerchantErrorResponse> merchant(MerchantException e) {
        return ResponseEntity.status(e.getStatus()).body(new MerchantErrorResponse(e.getCode(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<MerchantErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new MerchantErrorResponse("INVALID_REQUEST", "Request validation failed"));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<MerchantErrorResponse> conflict(Exception e) {
        return ResponseEntity.status(409).body(new MerchantErrorResponse("CONFLICT", "Merchant data changed or conflicts with an existing record"));
    }
}
