package com.swiftdrop.courier.config;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.service.CourierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourierSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourierService courierService;

    @Test
    void allowsPermittedCourierEndpointsWithoutHttpBasicAuth() throws Exception {
        when(courierService.getCourierById("cou_001")).thenReturn(new CourierResponse(
                "cou_001", "Nimal Senanayake", "motorcycle", "ABC-1234",
                4.8, true, 3, 120));

        mockMvc.perform(get("/api/v1/couriers/cou_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cou_001"));
    }
}
