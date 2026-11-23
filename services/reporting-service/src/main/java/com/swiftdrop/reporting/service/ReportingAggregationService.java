package com.swiftdrop.reporting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.reporting.dto.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReportingAggregationService {

    private static final Logger log = LoggerFactory.getLogger(ReportingAggregationService.class);

    private final RestClient restClient;
    private final String authUrl;
    private final String parcelUrl;
    private final String pricingUrl;
    private final String merchantUrl;
    private final String dispatchUrl;
    private final String courierUrl;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ReportingAggregationService(
            @Value("${services.auth.url:http://localhost:8081}") String authUrl,
            @Value("${services.parcel.url:http://localhost:8082}") String parcelUrl,
            @Value("${services.pricing.url:http://localhost:8083}") String pricingUrl,
            @Value("${services.merchant.url:http://localhost:8084}") String merchantUrl,
            @Value("${services.dispatch.url:http://localhost:8085}") String dispatchUrl,
            @Value("${services.courier.url:http://localhost:8086}") String courierUrl) {
        this(authUrl, parcelUrl, pricingUrl, merchantUrl, dispatchUrl, courierUrl, defaultRestClient());
    }

    public ReportingAggregationService(
            String authUrl,
            String parcelUrl,
            String pricingUrl,
            String merchantUrl,
            String dispatchUrl,
            String courierUrl,
            RestClient restClient) {
        this.authUrl = authUrl;
        this.parcelUrl = parcelUrl;
        this.pricingUrl = pricingUrl;
        this.merchantUrl = merchantUrl;
        this.dispatchUrl = dispatchUrl;
        this.courierUrl = courierUrl;
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }

    private RestClient.RequestHeadersSpec<?> createSpec(String url, String roleHeader, String authHeader) {
        var spec = restClient.get()
                .uri(url)
                .header("X-User-Roles", roleHeader);
        if (authHeader != null && !authHeader.isBlank()) {
            spec.header("Authorization", authHeader);
        }
        return spec;
    }

    public DashboardKpisResponse getDashboardKpis() {
        return getDashboardKpis("ROLE_ADMIN", null);
    }

    public DashboardKpisResponse getDashboardKpis(String callerRole, String authHeader) {
        boolean isPartial = false;
        Map<String, String> sourceAvailability = new LinkedHashMap<>();
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";
        boolean isCallerAdmin = roleHeader.toUpperCase(Locale.ROOT).contains("ADMIN");

        // 1. Fetch Parcel Stats (CORE - cannot fabricate or default if down)
        Long totalParcels = null;
        Long pendingParcels = null;
        Long inTransitDeliveries = null;
        Long deliveredParcels = null;
        Long failedDeliveries = null;
        Double totalRevenue = null;
        Double averageDeliveryTimeHours = null;

        try {
            JsonNode pNode = createSpec(parcelUrl + "/api/v1/parcels/admin/summary", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (pNode != null) {
                totalParcels = pNode.path("totalParcels").asLong(0);
                pendingParcels = pNode.path("pending").asLong(0);
                inTransitDeliveries = pNode.path("inTransit").asLong(0);
                deliveredParcels = pNode.path("delivered").asLong(0);
                failedDeliveries = pNode.path("failed").asLong(0);
                totalRevenue = pNode.hasNonNull("totalRevenue") ? pNode.path("totalRevenue").asDouble() : 0.0;
                if (pNode.hasNonNull("avgDeliveryHours")) {
                    averageDeliveryTimeHours = pNode.path("avgDeliveryHours").asDouble();
                }
                sourceAvailability.put("parcel-service", "UP");
            } else {
                throw new IllegalStateException("Empty response from parcel-service");
            }
        } catch (Exception e) {
            log.error("Failed to fetch core parcel stats from {}: {}", parcelUrl, e.getMessage());
            sourceAvailability.put("parcel-service", "DOWN");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Reporting core data source (parcel-service) unavailable: " + e.getMessage());
        }

        // 2. Fetch Dispatch Stats (Secondary - keep partial if down)
        Long unassignedDeliveries = null;
        Long assignedDeliveries = null;

        try {
            JsonNode dNode = createSpec(dispatchUrl + "/api/v1/dispatch/jobs/summary", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (dNode != null) {
                JsonNode data = dNode.path("data");
                unassignedDeliveries = data.path("pendingDispatch").asLong(0);
                assignedDeliveries = data.path("assignedPendingAcceptance").asLong(0)
                        + data.path("accepted").asLong(0);
                sourceAvailability.put("dispatch-service", "UP");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch dispatch stats from {}: {}", dispatchUrl, e.getMessage());
            sourceAvailability.put("dispatch-service", "DOWN");
            isPartial = true;
        }

        // 3. Fetch Courier Stats (Secondary - keep partial if down)
        Long totalCouriers = null;
        Long activeCouriers = null;

        try {
            JsonNode cNode = createSpec(courierUrl + "/api/v1/couriers/summary", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (cNode != null) {
                totalCouriers = cNode.path("totalCouriers").asLong(0);
                activeCouriers = cNode.path("availableCouriers").asLong(0);
                sourceAvailability.put("courier-service", "UP");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch courier stats from {}: {}", courierUrl, e.getMessage());
            sourceAvailability.put("courier-service", "DOWN");
            isPartial = true;
        }

        // 4. Fetch User Stats from Auth (Secondary - keep partial if down/forbidden for caller)
        Long registeredCustomers = null;
        if (!isCallerAdmin) {
            log.info("Non-admin caller ({}) restricted from auth-service user management stats", roleHeader);
            sourceAvailability.put("auth-service", "FORBIDDEN");
            isPartial = true;
        } else {
            try {
                JsonNode uNode = createSpec(authUrl + "/api/v1/auth/admin/stats", roleHeader, authHeader)
                        .retrieve()
                        .body(JsonNode.class);

                if (uNode != null) {
                    registeredCustomers = uNode.path("customers").asLong(0);
                    sourceAvailability.put("auth-service", "UP");
                }
            } catch (Exception e) {
                log.warn("Failed to fetch user stats from {}: {}", authUrl, e.getMessage());
                sourceAvailability.put("auth-service", "DOWN");
                isPartial = true;
            }
        }

        // 5. Fetch Merchant Stats (Secondary - keep partial if down/forbidden for caller)
        Long registeredMerchants = null;
        Long pendingMerchantApplications = null;
        if (!isCallerAdmin) {
            log.info("Non-admin caller ({}) restricted from merchant-service onboarding stats", roleHeader);
            sourceAvailability.put("merchant-service", "FORBIDDEN");
            isPartial = true;
        } else {
            try {
                JsonNode mNode = createSpec(merchantUrl + "/api/v1/merchants/admin/summary", roleHeader, authHeader)
                        .retrieve()
                        .body(JsonNode.class);

                if (mNode != null) {
                    JsonNode data = mNode.path("data");
                    registeredMerchants = data.path("totalMerchants").asLong(0);
                    pendingMerchantApplications = data.path("pendingApplications").asLong(0);
                    sourceAvailability.put("merchant-service", "UP");
                }
            } catch (Exception e) {
                log.warn("Failed to fetch merchant stats from {}: {}", merchantUrl, e.getMessage());
                sourceAvailability.put("merchant-service", "DOWN");
                isPartial = true;
            }
        }

        // Real computed success rate - no fabrication
        Double successRate = null;
        if (deliveredParcels != null && failedDeliveries != null) {
            long resolved = deliveredParcels + failedDeliveries;
            if (resolved > 0) {
                successRate = Math.round((deliveredParcels * 100.0 / resolved) * 10.0) / 10.0;
            }
        }

        return new DashboardKpisResponse(
                totalParcels,
                pendingParcels,
                unassignedDeliveries,
                assignedDeliveries,
                inTransitDeliveries,
                deliveredParcels,
                failedDeliveries,
                activeCouriers,
                totalCouriers,
                registeredCustomers,
                registeredMerchants,
                pendingMerchantApplications,
                totalRevenue,
                successRate,
                averageDeliveryTimeHours,
                isPartial,
                sourceAvailability,
                Instant.now()
        );
    }

    public ParcelVolumeReportResponse getParcelVolume(int days) {
        return getParcelVolume(days, "ROLE_ADMIN", null);
    }

    public ParcelVolumeReportResponse getParcelVolume(int days, String callerRole, String authHeader) {
        int count = days > 0 ? Math.min(days, 90) : 7;
        List<DailyVolumeItem> dailyStats = new ArrayList<>();
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";

        try {
            JsonNode arrNode = createSpec(parcelUrl + "/api/v1/parcels/admin/daily-volume?days=" + count, roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (arrNode != null && arrNode.isArray()) {
                for (JsonNode item : arrNode) {
                    dailyStats.add(new DailyVolumeItem(
                            item.path("date").asText(""),
                            item.path("total").asLong(0),
                            item.path("delivered").asLong(0),
                            item.path("failed").asLong(0),
                            item.path("revenue").asDouble(0.0)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch real daily parcel volume from {}: {}", parcelUrl, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel service unavailable for daily volume report: " + e.getMessage());
        }

        long totalVolume = dailyStats.stream().mapToLong(DailyVolumeItem::total).sum();
        return new ParcelVolumeReportResponse(count + "d", totalVolume, dailyStats);
    }

    public StatusDistributionResponse getStatusDistribution() {
        return getStatusDistribution("ROLE_ADMIN", null);
    }

    public StatusDistributionResponse getStatusDistribution(String callerRole, String authHeader) {
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";
        try {
            JsonNode pNode = createSpec(parcelUrl + "/api/v1/parcels/admin/summary", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (pNode == null) {
                throw new IllegalStateException("Empty response from parcel-service");
            }

            long total = pNode.path("totalParcels").asLong(0);
            long delivered = pNode.path("delivered").asLong(0);
            long inTransit = pNode.path("inTransit").asLong(0);
            long pending = pNode.path("pending").asLong(0);
            long failed = pNode.path("failed").asLong(0);

            // Fetch assigned from dispatch if available
            long assigned = 0;
            try {
                JsonNode dNode = createSpec(dispatchUrl + "/api/v1/dispatch/jobs/summary", roleHeader, authHeader)
                        .retrieve()
                        .body(JsonNode.class);
                if (dNode != null) {
                    JsonNode data = dNode.path("data");
                    assigned = data.path("assignedPendingAcceptance").asLong(0) + data.path("accepted").asLong(0);
                }
            } catch (Exception ignored) {
                // Secondary service failure is non-fatal for distribution
            }

            List<StatusDistributionItem> items = new ArrayList<>();
            addStatusItem(items, "Delivered", delivered, total);
            addStatusItem(items, "In Transit", inTransit, total);
            addStatusItem(items, "Pending Assignment", pending, total);
            addStatusItem(items, "Assigned", assigned, total);
            addStatusItem(items, "Failed / Cancelled", failed, total);

            return new StatusDistributionResponse(total, items);
        } catch (Exception e) {
            log.error("Failed to fetch status distribution from {}: {}", parcelUrl, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel service unavailable for status distribution: " + e.getMessage());
        }
    }

    private void addStatusItem(List<StatusDistributionItem> list, String status, long count, long total) {
        double pct = (total > 0 && count > 0) ? (Math.round((count * 100.0 / total) * 10.0) / 10.0) : 0.0;
        list.add(new StatusDistributionItem(status, count, pct));
    }

    public DeliveryPerformanceResponse getDeliveryPerformance() {
        return getDeliveryPerformance("ROLE_ADMIN", null);
    }

    public DeliveryPerformanceResponse getDeliveryPerformance(String callerRole, String authHeader) {
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";
        try {
            JsonNode pNode = createSpec(parcelUrl + "/api/v1/parcels/admin/summary", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (pNode == null) {
                throw new IllegalStateException("Empty response from parcel-service");
            }

            long delivered = pNode.path("delivered").asLong(0);
            long failed = pNode.path("failed").asLong(0);
            long total = delivered + failed;
            Double successRate = total > 0 ? (Math.round((delivered * 100.0 / total) * 10.0) / 10.0) : null;
            Double avgDeliveryHours = pNode.hasNonNull("avgDeliveryHours") ? pNode.path("avgDeliveryHours").asDouble() : null;
            Double onTimeRate = null; // Ground truth: not fabricated

            return new DeliveryPerformanceResponse(
                    total,
                    delivered,
                    failed,
                    successRate,
                    avgDeliveryHours,
                    onTimeRate
            );
        } catch (Exception e) {
            log.error("Failed to fetch delivery performance from {}: {}", parcelUrl, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Parcel service unavailable for delivery performance: " + e.getMessage());
        }
    }

    public CourierPerformanceResponse getCourierPerformance() {
        return getCourierPerformance("ROLE_ADMIN", null);
    }

    public CourierPerformanceResponse getCourierPerformance(String callerRole, String authHeader) {
        List<CourierPerformanceItem> couriers = new ArrayList<>();
        long total = 0;
        long active = 0;
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";

        try {
            JsonNode arrayNode = createSpec(courierUrl + "/api/v1/couriers", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (arrayNode != null && arrayNode.isArray()) {
                total = arrayNode.size();
                for (JsonNode c : arrayNode) {
                    boolean avail = c.path("isAvailable").asBoolean(c.path("available").asBoolean(false));
                    if (avail) {
                        active++;
                    }
                    Double rating = c.hasNonNull("rating") ? c.path("rating").asDouble() : null;
                    couriers.add(new CourierPerformanceItem(
                            c.path("id").asText(""),
                            c.path("name").asText("Courier"),
                            c.path("vehicleType").asText("MOTORCYCLE"),
                            c.path("vehiclePlate").asText(""),
                            rating,
                            avail,
                            c.path("deliveriesToday").asInt(0),
                            c.path("totalDeliveries").asInt(0)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list couriers from {}: {}", courierUrl, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Courier service unavailable: " + e.getMessage());
        }

        return new CourierPerformanceResponse(total, active, couriers);
    }

    public MerchantVolumeResponse getMerchantVolume() {
        return getMerchantVolume("ROLE_ADMIN", null);
    }

    public MerchantVolumeResponse getMerchantVolume(String callerRole, String authHeader) {
        String roleHeader = (callerRole != null && !callerRole.isBlank()) ? callerRole : "ROLE_ADMIN";
        if (!roleHeader.toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Admin role required for merchant reporting");
        }

        List<MerchantVolumeItem> merchants = new ArrayList<>();
        long total = 0;
        long pending = 0;

        try {
            JsonNode resNode = createSpec(merchantUrl + "/api/v1/merchants/admin?size=50", roleHeader, authHeader)
                    .retrieve()
                    .body(JsonNode.class);

            if (resNode != null) {
                JsonNode content = resNode.path("data").path("content");
                if (content.isArray()) {
                    total = resNode.path("data").path("totalElements").asLong(content.size());
                    for (JsonNode m : content) {
                        String status = m.path("status").asText("SUBMITTED");
                        if ("SUBMITTED".equalsIgnoreCase(status) || "UNDER_REVIEW".equalsIgnoreCase(status)) {
                            pending++;
                        }
                        merchants.add(new MerchantVolumeItem(
                                m.path("id").asText(""),
                                m.path("businessName").asText("Merchant"),
                                m.path("pricingTier").asText("STANDARD"),
                                status,
                                m.path("orderCount").asLong(0)
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch merchant volume from {}: {}", merchantUrl, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Merchant service unavailable: " + e.getMessage());
        }

        return new MerchantVolumeResponse(total, pending, merchants);
    }
}
