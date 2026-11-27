package com.swiftdrop.courier.config;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.security.DispatchServiceAuthenticationFilter;
import com.swiftdrop.courier.service.CourierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "dispatch.service-auth.token=test-dispatch-service-credential")
@AutoConfigureMockMvc
class DispatchServiceAuthenticationTest {

    private static final String TOKEN = "test-dispatch-service-credential";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourierService courierService;

    @Test
    @DisplayName("A valid service credential can read one courier eligibility record")
    void validServiceCredentialReadsOneCourier() throws Exception {
        when(courierService.getCourierById("cou_001")).thenReturn(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120));

        mockMvc.perform(get("/api/v1/couriers/cou_001")
                        .header(DispatchServiceAuthenticationFilter.HEADER, TOKEN)
                        .header("X-User-Id", "cou_999")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cou_001"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    @DisplayName("A missing service credential cannot read the directory")
    void missingServiceCredentialIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/cou_001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An invalid service credential is rejected")
    void invalidServiceCredentialIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/cou_001")
                        .header(DispatchServiceAuthenticationFilter.HEADER, "wrong-credential")
                        .header("X-User-Roles", "DISPATCH_SERVICE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The service credential cannot list the fleet or use courier self-service")
    void serviceCredentialCannotUseUnrelatedEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/couriers")
                        .header(DispatchServiceAuthenticationFilter.HEADER, TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header(DispatchServiceAuthenticationFilter.HEADER, TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/couriers/me/availability")
                        .header(DispatchServiceAuthenticationFilter.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAvailable\":false}"))
                .andExpect(status().isForbidden());
    }
}
