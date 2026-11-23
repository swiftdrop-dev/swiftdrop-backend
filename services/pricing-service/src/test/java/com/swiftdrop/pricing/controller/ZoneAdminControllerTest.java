package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.response.ZoneDto;
import com.swiftdrop.pricing.exception.GlobalExceptionHandler;
import com.swiftdrop.pricing.service.ZoneService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZoneAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ZoneAdminController RBAC and Direct Access Tests")
class ZoneAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ZoneService zoneService;

    @Test
    @DisplayName("Public endpoint getActiveZones succeeds without authentication headers")
    void publicGetActiveZonesSucceeds() throws Exception {
        when(zoneService.getAllActiveZones()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/zones")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin endpoint getAllZones without identity header returns 401 Unauthorized")
    void getAllZonesWithoutHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin endpoint getAllZones with non-admin role returns 403 Forbidden")
    void getAllZonesWithDispatcherReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("X-User-Roles", "dispatcher")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin endpoint getAllZones with customer role returns 403 Forbidden")
    void getAllZonesWithCustomerReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("X-User-Roles", "customer")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin endpoint getAllZones with admin role returns 200 OK")
    void getAllZonesWithAdminSucceeds() throws Exception {
        when(zoneService.getAllZones()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/zones/admin/all")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Create zone with dispatcher role returns 403 Forbidden")
    void createZoneWithDispatcherReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/zones/admin")
                        .header("X-User-Roles", "dispatcher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WP\",\"name\":\"Western Province\",\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Create zone with admin role returns 201 Created")
    void createZoneWithAdminSucceeds() throws Exception {
        ZoneDto dto = new ZoneDto(1L, "Western Province", "WP", true, null);
        when(zoneService.createZone(any(ZoneDto.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/zones/admin")
                        .header("X-User-Roles", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WP\",\"name\":\"Western Province\",\"active\":true}"))
                .andExpect(status().isCreated());
    }
}
