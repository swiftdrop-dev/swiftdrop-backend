package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.dto.response.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ReportingAggregationServiceTest {

    private MockRestServiceServer mockServer;
    private ReportingAggregationService service;

    private static final String AUTH_URL = "http://auth-service";
    private static final String PARCEL_URL = "http://parcel-service";
    private static final String PRICING_URL = "http://pricing-service";
    private static final String MERCHANT_URL = "http://merchant-service";
    private static final String DISPATCH_URL = "http://dispatch-service";
    private static final String COURIER_URL = "http://courier-service";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        service = new ReportingAggregationService(
                AUTH_URL, PARCEL_URL, PRICING_URL, MERCHANT_URL, DISPATCH_URL, COURIER_URL, restClient
        );
    }

    private void mockParcelSummarySuccess() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Roles", "ROLE_ADMIN"))
                .andRespond(withSuccess(
                        "{\"totalParcels\":50,\"pending\":10,\"inTransit\":15,\"delivered\":20,\"failed\":5,\"totalRevenue\":45000.0,\"avgDeliveryHours\":3.8}",
                        MediaType.APPLICATION_JSON));
    }

    private void mockDispatchSummarySuccess() {
        mockServer.expect(requestTo(DISPATCH_URL + "/api/v1/dispatch/jobs/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"pendingDispatch\":4,\"assignedPendingAcceptance\":3,\"accepted\":5}}",
                        MediaType.APPLICATION_JSON));
    }

    private void mockCourierSummarySuccess() {
        mockServer.expect(requestTo(COURIER_URL + "/api/v1/couriers/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"totalCouriers\":12,\"availableCouriers\":8}",
                        MediaType.APPLICATION_JSON));
    }

    private void mockAuthStatsSuccess() {
        mockServer.expect(requestTo(AUTH_URL + "/api/v1/auth/admin/stats"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"customers\":35}",
                        MediaType.APPLICATION_JSON));
    }

    private void mockMerchantSummarySuccess() {
        mockServer.expect(requestTo(MERCHANT_URL + "/api/v1/merchants/admin/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":{\"totalMerchants\":15,\"pendingApplications\":2}}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Core dependency failure: parcel-service outage throws 503 and never silently returns zeroes")
    void parcelServiceDownThrows503() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/summary"))
                .andRespond(withServerError());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getDashboardKpis());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("parcel-service"));
    }

    @Test
    @DisplayName("Full success: all services respond and result is non-partial with real calculated metrics")
    void allServicesUpReturnsFullKpis() {
        mockParcelSummarySuccess();
        mockDispatchSummarySuccess();
        mockCourierSummarySuccess();
        mockAuthStatsSuccess();
        mockMerchantSummarySuccess();

        DashboardKpisResponse kpis = service.getDashboardKpis();
        assertFalse(kpis.isPartial());
        assertEquals("UP", kpis.sourceAvailability().get("parcel-service"));
        assertEquals("UP", kpis.sourceAvailability().get("dispatch-service"));
        assertEquals("UP", kpis.sourceAvailability().get("courier-service"));
        assertEquals("UP", kpis.sourceAvailability().get("auth-service"));
        assertEquals("UP", kpis.sourceAvailability().get("merchant-service"));

        assertEquals(50L, kpis.totalParcels());
        assertEquals(10L, kpis.pendingParcels());
        assertEquals(4L, kpis.unassignedDeliveries());
        assertEquals(8L, kpis.assignedDeliveries()); // 3 + 5
        assertEquals(15L, kpis.inTransitDeliveries());
        assertEquals(20L, kpis.deliveredParcels());
        assertEquals(5L, kpis.failedDeliveries());
        assertEquals(8L, kpis.activeCouriers());
        assertEquals(12L, kpis.totalCouriers());
        assertEquals(35L, kpis.registeredCustomers());
        assertEquals(15L, kpis.registeredMerchants());
        assertEquals(2L, kpis.pendingMerchantApplications());
        assertEquals(45000.0, kpis.totalRevenue());
        assertEquals(80.0, kpis.successRate()); // 20 / (20 + 5) * 100
        assertEquals(3.8, kpis.averageDeliveryTimeHours());
    }

    @Test
    @DisplayName("Secondary dependency failure: dispatch-service down marks isPartial=true and leaves dispatch fields null")
    void dispatchServiceDownReturnsPartial() {
        mockParcelSummarySuccess();
        mockServer.expect(requestTo(DISPATCH_URL + "/api/v1/dispatch/jobs/summary"))
                .andRespond(withServerError());
        mockCourierSummarySuccess();
        mockAuthStatsSuccess();
        mockMerchantSummarySuccess();

        DashboardKpisResponse kpis = service.getDashboardKpis();
        assertTrue(kpis.isPartial());
        assertEquals("DOWN", kpis.sourceAvailability().get("dispatch-service"));
        assertNull(kpis.unassignedDeliveries());
        assertNull(kpis.assignedDeliveries());

        // Core parcel metrics still present
        assertEquals(50L, kpis.totalParcels());
        assertEquals(20L, kpis.deliveredParcels());
        assertEquals(12L, kpis.totalCouriers());
    }

    @Test
    @DisplayName("Secondary dependency failure: courier-service down leaves courier fields null and records source status")
    void courierServiceDownReturnsPartial() {
        mockParcelSummarySuccess();
        mockDispatchSummarySuccess();
        mockServer.expect(requestTo(COURIER_URL + "/api/v1/couriers/summary"))
                .andRespond(withServerError());
        mockAuthStatsSuccess();
        mockMerchantSummarySuccess();

        DashboardKpisResponse kpis = service.getDashboardKpis();
        assertTrue(kpis.isPartial());
        assertEquals("DOWN", kpis.sourceAvailability().get("courier-service"));
        assertNull(kpis.totalCouriers());
        assertNull(kpis.activeCouriers());
        assertEquals(50L, kpis.totalParcels());
    }

    @Test
    @DisplayName("Secondary dependency failure: auth-service down leaves registeredCustomers null")
    void authServiceDownReturnsPartial() {
        mockParcelSummarySuccess();
        mockDispatchSummarySuccess();
        mockCourierSummarySuccess();
        mockServer.expect(requestTo(AUTH_URL + "/api/v1/auth/admin/stats"))
                .andRespond(withServerError());
        mockMerchantSummarySuccess();

        DashboardKpisResponse kpis = service.getDashboardKpis();
        assertTrue(kpis.isPartial());
        assertEquals("DOWN", kpis.sourceAvailability().get("auth-service"));
        assertNull(kpis.registeredCustomers());
    }

    @Test
    @DisplayName("Secondary dependency failure: merchant-service down leaves merchant fields null")
    void merchantServiceDownReturnsPartial() {
        mockParcelSummarySuccess();
        mockDispatchSummarySuccess();
        mockCourierSummarySuccess();
        mockAuthStatsSuccess();
        mockServer.expect(requestTo(MERCHANT_URL + "/api/v1/merchants/admin/summary"))
                .andRespond(withServerError());

        DashboardKpisResponse kpis = service.getDashboardKpis();
        assertTrue(kpis.isPartial());
        assertEquals("DOWN", kpis.sourceAvailability().get("merchant-service"));
        assertNull(kpis.registeredMerchants());
        assertNull(kpis.pendingMerchantApplications());
    }

    @Test
    @DisplayName("Parcel volume report calls real parcel daily-volume endpoint without fake variance")
    void getParcelVolumeRealData() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/daily-volume?days=7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Roles", "ROLE_ADMIN"))
                .andRespond(withSuccess(
                        "[{\"date\":\"Nov 20\",\"total\":5,\"delivered\":4,\"failed\":1,\"revenue\":5000.0}," +
                         "{\"date\":\"Nov 21\",\"total\":10,\"delivered\":8,\"failed\":2,\"revenue\":12000.0}]",
                        MediaType.APPLICATION_JSON));

        ParcelVolumeReportResponse report = service.getParcelVolume(7);
        assertEquals("7d", report.period());
        assertEquals(15L, report.totalVolume());
        assertEquals(2, report.dailyStats().size());
        assertEquals("Nov 20", report.dailyStats().get(0).date());
        assertEquals(5L, report.dailyStats().get(0).total());
        assertEquals(4L, report.dailyStats().get(0).delivered());
        assertEquals(5000.0, report.dailyStats().get(0).revenue());
    }

    @Test
    @DisplayName("Parcel volume throws 503 if parcel service fails")
    void getParcelVolumeDownThrows503() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/daily-volume?days=7"))
                .andRespond(withServerError());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getParcelVolume(7));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @DisplayName("Status distribution handles 0 total parcels without dividing by zero or setting total=1")
    void statusDistributionZeroParcels() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/summary"))
                .andRespond(withSuccess(
                        "{\"totalParcels\":0,\"pending\":0,\"inTransit\":0,\"delivered\":0,\"failed\":0}",
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(DISPATCH_URL + "/api/v1/dispatch/jobs/summary"))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        StatusDistributionResponse dist = service.getStatusDistribution();
        assertEquals(0L, dist.total());
        for (StatusDistributionItem item : dist.distribution()) {
            assertEquals(0L, item.count());
            assertEquals(0.0, item.percentage());
        }
    }

    @Test
    @DisplayName("Delivery performance uses real average delivery hours and no fabricated on-time rate")
    void deliveryPerformanceRealMetrics() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/summary"))
                .andRespond(withSuccess(
                        "{\"totalParcels\":20,\"delivered\":15,\"failed\":5,\"avgDeliveryHours\":2.5}",
                        MediaType.APPLICATION_JSON));

        DeliveryPerformanceResponse perf = service.getDeliveryPerformance();
        assertEquals(20L, perf.totalDeliveries());
        assertEquals(15L, perf.delivered());
        assertEquals(5L, perf.failed());
        assertEquals(75.0, perf.successRate());
        assertEquals(2.5, perf.averageDeliveryTimeHours());
        assertNull(perf.onTimeRate()); // Real: null instead of fake 94.8%
    }

    @Test
    @DisplayName("Dispatcher caller preserves identity and withholds auth and merchant admin stats without privilege elevation")
    void dispatcherCallerPreservesRoleAndWithholdsAdminStats() {
        mockServer.expect(requestTo(PARCEL_URL + "/api/v1/parcels/admin/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Roles", "ROLE_DISPATCHER"))
                .andRespond(withSuccess(
                        "{\"totalParcels\":50,\"pending\":10,\"inTransit\":15,\"delivered\":20,\"failed\":5,\"totalRevenue\":45000.0,\"avgDeliveryHours\":3.8}",
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(DISPATCH_URL + "/api/v1/dispatch/jobs/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Roles", "ROLE_DISPATCHER"))
                .andRespond(withSuccess(
                        "{\"data\":{\"pendingDispatch\":4,\"assignedPendingAcceptance\":3,\"accepted\":5}}",
                        MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(COURIER_URL + "/api/v1/couriers/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Roles", "ROLE_DISPATCHER"))
                .andRespond(withSuccess(
                        "{\"totalCouriers\":12,\"availableCouriers\":8}",
                        MediaType.APPLICATION_JSON));

        // Note: auth-service and merchant-service MUST NOT be called with elevated privileges

        DashboardKpisResponse kpis = service.getDashboardKpis("ROLE_DISPATCHER", null);
        assertTrue(kpis.isPartial());
        assertEquals("UP", kpis.sourceAvailability().get("parcel-service"));
        assertEquals("UP", kpis.sourceAvailability().get("dispatch-service"));
        assertEquals("UP", kpis.sourceAvailability().get("courier-service"));
        assertEquals("FORBIDDEN", kpis.sourceAvailability().get("auth-service"));
        assertEquals("FORBIDDEN", kpis.sourceAvailability().get("merchant-service"));

        assertEquals(50L, kpis.totalParcels());
        assertNull(kpis.registeredCustomers());
        assertNull(kpis.registeredMerchants());
        assertNull(kpis.pendingMerchantApplications());
    }

    @Test
    @DisplayName("Dispatcher role cannot access merchant volume report")
    void dispatcherCannotAccessMerchantVolumeReport() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getMerchantVolume("ROLE_DISPATCHER", null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}

