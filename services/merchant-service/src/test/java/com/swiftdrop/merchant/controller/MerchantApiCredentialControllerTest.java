package com.swiftdrop.merchant.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.exception.*;
import com.swiftdrop.merchant.security.*;
import com.swiftdrop.merchant.service.MerchantApiCredentialService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MerchantApiCredentialControllerTest {
    final String path="/api/v1/merchant/me/api-keys";
    MerchantApiCredentialService service; MockMvc mvc; GeneratedApiKey generated; MerchantApiCredential credential;
    @BeforeEach void setup() {
        service=mock(MerchantApiCredentialService.class);
        mvc=MockMvcBuilders.standaloneSetup(new MerchantApiCredentialController(service)).setControllerAdvice(new MerchantExceptionHandler()).build();
        generated=new ApiKeyGenerator().generate();
        credential=new MerchantApiCredential(UUID.randomUUID(),generated.publicId(),new ApiKeyHasher().hash(generated.plaintext()),ApiKeyHasher.VERSION,"Key",Set.of("orders:read"),"owner",Instant.now(),Instant.now().plusSeconds(60));
    }
    MerchantApiKeyIssuedResponse issued() { return new MerchantApiKeyIssuedResponse(MerchantApiKeyMetadataResponse.from(credential,Instant.now()),generated.plaintext()); }
    @Test void issuanceAndRotationAreNotCacheable() throws Exception {
        when(service.issue(any())).thenReturn(issued()); when(service.rotate(eq(credential.getId()),any())).thenReturn(issued());
        var result=mvc.perform(post(path).contentType("application/json").content("{\"name\":\"Key\",\"scopes\":[\"orders:read\"]}"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        assertTrue(result.getResponse().getContentAsString().contains(generated.plaintext()));
        assertFalse(result.getResponse().getContentAsString().contains(credential.getSecretDigest()));
        mvc.perform(post(path+"/"+credential.getId()+"/rotate").contentType("application/json").content("{}"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void listingContainsNeitherSecretNorDigest() throws Exception {
        when(service.list(0,20)).thenReturn(new MerchantApiKeyPageResponse(List.of(issued().credential()),1,0,20));
        String json=mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains(generated.plaintext())); assertFalse(json.contains(credential.getSecretDigest())); assertFalse(json.contains("apiKey"));
    }
    @Test void entitySerializationAndStringsDoNotExposeSecrets() throws Exception {
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(credential);
        assertFalse(json.contains(credential.getSecretDigest())); assertFalse(json.contains(generated.plaintext()));
        assertFalse(credential.toString().contains(credential.getSecretDigest())); assertFalse(issued().toString().contains(generated.plaintext()));
    }
    @Test void invalidRequestNeverReachesService() throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void revokeReturns204() throws Exception { mvc.perform(post(path+"/"+credential.getId()+"/revoke")).andExpect(status().isNoContent()); verify(service).revoke(credential.getId()); }
    @Test void errorsDoNotEchoSubmittedSecret() throws Exception {
        when(service.issue(any())).thenThrow(new MerchantException(403,"MERCHANT_NOT_APPROVED","Approved merchant required"));
        String json=mvc.perform(post(path).contentType("application/json").content("{\"name\":\"Key\",\"scopes\":[\"orders:read\"]}"))
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains(generated.plaintext()));
    }
}
