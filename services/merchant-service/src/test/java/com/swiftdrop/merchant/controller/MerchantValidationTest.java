package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.exception.MerchantExceptionHandler;
import com.swiftdrop.merchant.service.MerchantService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MerchantValidationTest {
 MerchantService service; MockMvc mvc;
 @BeforeEach void setup() { service=mock(MerchantService.class); mvc=MockMvcBuilders.standaloneSetup(new MerchantController(service),new MerchantReviewController(service)).setControllerAdvice(new MerchantExceptionHandler()).build(); }
 @Test void invalidRegistration() throws Exception { mvc.perform(post("/api/v1/merchant/onboarding").contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST")); verifyNoInteractions(service); }
 @Test void invalidUpdate() throws Exception { mvc.perform(patch("/api/v1/merchant/me").contentType("application/json").content("{}")).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
 @Test void invalidRejection() throws Exception { mvc.perform(post("/api/v1/admin/merchants/00000000-0000-0000-0000-000000000001/review").contentType("application/json").content("{\"decision\":\"REJECTED\",\"reason\":\" \"}")).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
 @Test void malformedIdentifier() throws Exception { mvc.perform(post("/api/v1/admin/merchants/bad/review").contentType("application/json").content("{\"decision\":\"APPROVED\"}")).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
 @Test void validRegistrationReturns201() throws Exception {
  mvc.perform(post("/api/v1/merchant/onboarding").contentType("application/json").content("""
   {"businessName":"Shop","registrationNumber":"REG","contactName":"Owner","contactEmail":"owner@example.com",
   "contactPhone":"+94112345678","pickupStreet":"Street","pickupCity":"City","pickupDistrict":"District"}
   """)).andExpect(status().isCreated()); verify(service).register(any());
 }
}
