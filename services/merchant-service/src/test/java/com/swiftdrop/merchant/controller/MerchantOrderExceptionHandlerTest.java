package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.exception.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MerchantOrderExceptionHandlerTest {
    @Test void processingConflictHasRetryHeaderAndSafeShape() {
        var response=new MerchantOrderExceptionHandler().handle(new MerchantOrderException(409,"REQUEST_IN_PROGRESS","Request is being processed"));
        assertEquals(409,response.getStatusCode().value()); assertEquals("5",response.getHeaders().getFirst("Retry-After")); assertEquals("no-store",response.getHeaders().getCacheControl()); assertEquals("REQUEST_IN_PROGRESS",response.getBody().code());
    }
}
