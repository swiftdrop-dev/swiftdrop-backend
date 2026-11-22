
package com.swiftdrop.merchant.exception;

import com.swiftdrop.merchant.controller.MerchantOrderController;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = MerchantOrderController.class)
@Order(-1)
public class MerchantOrderExceptionHandler {

    public record OrderErrorResponse(String code, String message) {}

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<OrderErrorResponse> handlePersistenceFailure() {
        return handle(new MerchantOrderException(503, "ORDER_STORAGE_UNAVAILABLE", "Order storage is unavailable"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<OrderErrorResponse> handleMalformedRequest() {
        return handle(new MerchantOrderException(400, "INVALID_REQUEST", "Order request is invalid"));
    }

    @ExceptionHandler(MerchantOrderException.class)
    public ResponseEntity<OrderErrorResponse> handle(MerchantOrderException e) {

        var builder = ResponseEntity
                .status(e.getStatus())
                .cacheControl(CacheControl.noStore());

        if ("REQUEST_IN_PROGRESS".equals(e.getCode())) {
            builder.header("Retry-After", "5");
        }

        return builder.body(
                new OrderErrorResponse(e.getCode(), e.getMessage())
        );
    }
}
