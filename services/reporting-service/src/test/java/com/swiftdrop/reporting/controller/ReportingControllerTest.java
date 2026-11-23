package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.dto.response.*;
import com.swiftdrop.reporting.service.ReportingAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingControllerTest {

    @Mock
    private ReportingAggregationService aggregationService;

    private ReportingController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportingController(aggregationService);
    }

    @Test
    @DisplayName("Admin or Dispatcher can access dashboard KPIs")
    void privilegedUserCanAccessDashboardKpis() {
        DashboardKpisResponse mockKpis = new DashboardKpisResponse(
                100L, 10L, 5L, 20L, 25L, 45L, 5L, 8L, 10L, 50L, 15L, 3L, 50000.0, 90.0, 3.5
        );
        when(aggregationService.getDashboardKpis("ROLE_ADMIN", null)).thenReturn(mockKpis);

        ResponseEntity<DashboardKpisResponse> res = controller.getDashboardKpis("ROLE_ADMIN", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(100L, res.getBody().totalParcels());
        assertEquals(50000.0, res.getBody().totalRevenue());
    }

    @Test
    @DisplayName("Missing role header is rejected with 401 Unauthorized")
    void missingRoleThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getDashboardKpis(null, null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Customer role is denied with 403 on report endpoints")
    void customerRoleDenied() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getDashboardKpis("customer", null)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("Can retrieve parcel volume for 7 days")
    void canGetParcelVolume() {
        ParcelVolumeReportResponse mockReport = new ParcelVolumeReportResponse(
                "7d", 70, List.of(new DailyVolumeItem("Nov 20", 10, 9, 1, 10000.0))
        );
        when(aggregationService.getParcelVolume(7, "dispatcher", null)).thenReturn(mockReport);

        ResponseEntity<ParcelVolumeReportResponse> res = controller.getParcelVolume(7, "dispatcher", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(70, res.getBody().totalVolume());
    }

    @Test
    @DisplayName("Can retrieve status distribution")
    void canGetStatusDistribution() {
        StatusDistributionResponse mockDist = new StatusDistributionResponse(
                100, List.of(new StatusDistributionItem("Delivered", 80, 80.0))
        );
        when(aggregationService.getStatusDistribution("admin", null)).thenReturn(mockDist);

        ResponseEntity<StatusDistributionResponse> res = controller.getStatusDistribution("admin", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(100, res.getBody().total());
    }

    @Test
    @DisplayName("Can retrieve delivery performance")
    void canGetDeliveryPerformance() {
        DeliveryPerformanceResponse mockPerf = new DeliveryPerformanceResponse(
                100L, 95L, 5L, 95.0, 3.2, 96.0
        );
        when(aggregationService.getDeliveryPerformance("admin", null)).thenReturn(mockPerf);

        ResponseEntity<DeliveryPerformanceResponse> res = controller.getDeliveryPerformance("admin", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(95.0, res.getBody().successRate());
    }

    @Test
    @DisplayName("Can retrieve courier performance")
    void canGetCourierPerformance() {
        CourierPerformanceResponse mockCouriers = new CourierPerformanceResponse(
                1, 1, List.of(new CourierPerformanceItem("c1", "Kamal", "VAN", "WP-1234", 4.9, true, 5, 120))
        );
        when(aggregationService.getCourierPerformance("admin", null)).thenReturn(mockCouriers);

        ResponseEntity<CourierPerformanceResponse> res = controller.getCourierPerformance("admin", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().totalCouriers());
    }

    @Test
    @DisplayName("Admin can retrieve merchant volume")
    void adminCanGetMerchantVolume() {
        MerchantVolumeResponse mockMerchants = new MerchantVolumeResponse(
                1, 0, List.of(new MerchantVolumeItem("m1", "Tech Store", "GOLD", "APPROVED", 50))
        );
        when(aggregationService.getMerchantVolume("admin", null)).thenReturn(mockMerchants);

        ResponseEntity<MerchantVolumeResponse> res = controller.getMerchantVolume("admin", null);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().totalMerchants());
    }

    @Test
    @DisplayName("Dispatcher is denied with 403 on merchant volume report")
    void dispatcherDeniedOnMerchantVolume() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getMerchantVolume("ROLE_DISPATCHER", null)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("Missing role header is rejected with 401 on merchant volume report")
    void missingRoleOnMerchantVolumeThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getMerchantVolume(null, null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
