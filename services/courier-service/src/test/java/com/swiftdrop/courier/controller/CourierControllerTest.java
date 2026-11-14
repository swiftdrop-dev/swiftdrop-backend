package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.exception.ApiExceptionHandler;
import com.swiftdrop.courier.exception.CourierNotFoundException;
import com.swiftdrop.courier.service.CourierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class CourierControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourierService courierService;

    @Test
    void returns200AndCourierDetailsWhenFound() throws Exception {
        when(courierService.getCourierById("cou_001")).thenReturn(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120));

        mockMvc.perform(get("/api/v1/couriers/cou_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cou_001"))
                .andExpect(jsonPath("$.name").value("Nimal Senanayake"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.totalDeliveries").value(120));
    }

    @Test
    void returns404WithConsistentErrorBodyWhenMissing() throws Exception {
        when(courierService.getCourierById("missing"))
                .thenThrow(new CourierNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/couriers/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Courier not found: missing"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
