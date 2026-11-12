package com.swiftdrop.merchant.security;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;
import com.swiftdrop.merchant.repository.MerchantRepository;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
class MerchantSecurityConfigTest {
 @Autowired MockMvc mvc;
 @Autowired ApplicationContext context;
 @Test void defaultContextHasNoPersistenceOrMigrationRunner() {
  assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
  assertTrue(context.getBeansOfType(MerchantRepository.class).isEmpty());
  assertFalse(context.containsBean("flyway"));
  assertFalse(context.containsBean("merchantService"));
  assertFalse(context.containsBean("merchantApiCredentialService"));
  assertTrue(context.getBeansOfType(com.swiftdrop.merchant.repository.MerchantApiCredentialRepository.class).isEmpty());
  assertTrue(context.getBeansOfType(MerchantApiKeyVerifier.class).isEmpty());
  assertTrue(context.getBean(MerchantIdentityResolver.class) instanceof UnavailableMerchantIdentityResolver);
 }
 @Test void anonymousDenied() throws Exception { mvc.perform(get("/api/v1/merchant/me")).andExpect(status().isUnauthorized()); }
 @Test @WithMockUser(authorities="merchant:review") void authenticatedStillDenied() throws Exception {
  mvc.perform(get("/api/v1/merchant/me")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/admin/merchants/00000000-0000-0000-0000-000000000001/review").with(csrf()).contentType("application/json").content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
 }
 @Test void unverifiedHeadersDenied() throws Exception { mvc.perform(get("/api/v1/merchant/me").header("X-User-Id","owner").header("Authorization","Bearer unverified")).andExpect(status().isUnauthorized()); }
 @Test void credentialManagementRemainsDenied() throws Exception {
  mvc.perform(get("/api/v1/merchant/me/api-keys")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/merchant/me/api-keys").with(csrf()).contentType("application/json").content("{}"))
      .andExpect(status().isUnauthorized());
 }
}
