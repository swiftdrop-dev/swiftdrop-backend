package com.swiftdrop.merchant.exception;
import com.swiftdrop.merchant.dto.response.MerchantErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@Order(-1)
public class MerchantOrderExceptionHandler {
    @ExceptionHandler(MerchantOrderException.class)
    public ResponseEntity<MerchantErrorResponse> handle(MerchantOrderException e) {
        var builder=ResponseEntity.status(e.getStatus()).cacheControl(CacheControl.noStore());
        if (e.getCode().equals("REQUEST_IN_PROGRESS")) builder.header("Retry-After","5");
        return builder.body(new MerchantErrorResponse(e.getCode(),e.getMessage()));
    }
}
