package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.security.ParcelSecurityConfiguration;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import com.swiftdrop.parcel.service.ParcelQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParcelController.class)
@AutoConfigureMockMvc
@Import(ParcelSecurityConfiguration.class)
class ParcelControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParcelAssignmentService assignmentService;

    @MockBean
    private ParcelQueryService parcelQueryService;

    @Test
    void anonymousAssignmentRequestIsDeniedBeforeControllerRuns() throws Exception {
        assertDenied(post("/internal/v1/parcels/prc_001/assignments")
                .contentType("application/json")
                .content("{\"courierId\":\"cou_001\"}"));
        verifyNoInteractions(assignmentService, parcelQueryService);
    }

    @Test
    void spoofedIdentityHeadersDoNotAuthorizeAssignmentOrParcelList() throws Exception {
        assertDenied(post("/internal/v1/parcels/prc_001/assignments")
                .header("X-User-Id", "cou_001")
                .header("X-User-Roles", "courier")
                .header("X-User-Email", "courier@example.invalid")
                .contentType("application/json")
                .content("{\"courierId\":\"cou_001\"}"));

        assertDenied(get("/internal/v1/parcels")
                .header("X-User-Id", "cou_001")
                .header("X-User-Roles", "courier")
                .param("courierId", "cou_001")
                .param("scheduledDate", "2026-11-15"));
        verifyNoInteractions(assignmentService, parcelQueryService);
    }

    @Test
    void anonymousOrSpoofedCallerCannotReadAssignmentHistory() throws Exception {
        assertDenied(get("/internal/v1/parcels/prc_001/assignment-history"));
        assertDenied(get("/internal/v1/parcels/prc_001/assignment-history")
                .header("X-User-Id", "cou_001")
                .header("X-User-Roles", "courier")
                .header("X-User-Email", "courier@example.invalid"));
        verifyNoInteractions(assignmentService, parcelQueryService);
    }

    private void assertDenied(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }
}
