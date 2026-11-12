package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.exception.*;
import com.swiftdrop.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MerchantExceptionHandlerTest {
 @Test void safeErrorResponses() throws Exception {
  for (RuntimeException error : new RuntimeException[]{new MerchantException(404,"MERCHANT_NOT_FOUND","Merchant not found"),new DataIntegrityViolationException("private database detail"),new OptimisticLockingFailureException("private database detail")}) {
   var service=mock(MerchantService.class); when(service.profile()).thenThrow(error);
   var mvc=MockMvcBuilders.standaloneSetup(new MerchantController(service)).setControllerAdvice(new MerchantExceptionHandler()).build();
   mvc.perform(get("/api/v1/merchant/me")).andExpect(status().is(error instanceof MerchantException ? 404 : 409)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
  }
 }
}
