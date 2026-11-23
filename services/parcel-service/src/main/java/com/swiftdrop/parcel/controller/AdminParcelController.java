package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.response.ParcelBookingResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/parcels/admin")
public class AdminParcelController {

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Colombo");
    private final ParcelRepository parcelRepository;

    public AdminParcelController(ParcelRepository parcelRepository) {
        this.parcelRepository = parcelRepository;
    }

    private String resolveEffectiveRole(String headerRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.parcel.security.UserPrincipal principal) {
            return "ROLE_" + principal.getRole().toUpperCase(Locale.ROOT);
        }
        if (headerRoles != null && !headerRoles.isBlank()) {
            return headerRoles;
        }
        return null;
    }

    private void verifyPrivilegedRole(String userRoles) {
        String role = resolveEffectiveRole(userRoles);
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication role");
        }
        String lower = role.toLowerCase(Locale.ROOT);
        if (!lower.contains("admin") && !lower.contains("dispatcher") && !lower.contains("staff")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Admin or Dispatcher role required");
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ParcelBookingResponse>> getAllParcels(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        verifyPrivilegedRole(userRoles);

        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanStatus = (status != null && !status.isBlank() && !status.equalsIgnoreCase("all"))
                ? status.trim().toLowerCase(Locale.ROOT) : null;

        Page<Parcel> parcels = parcelRepository.searchParcels(cleanQuery, cleanStatus, pageable);
        return ResponseEntity.ok(parcels.map(this::toBookingResponse));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyPrivilegedRole(userRoles);

        Map<String, Object> summary = new HashMap<>();

        long total = parcelRepository.count();
        summary.put("totalParcels", total);

        Map<String, Long> statusCounts = new HashMap<>();
        List<Object[]> grouped = parcelRepository.countByStatusGroup();
        for (Object[] row : grouped) {
            String st = (String) row[0];
            Long cnt = (Long) row[1];
            if (st != null) {
                statusCounts.put(st.toLowerCase(Locale.ROOT), cnt);
            }
        }
        summary.put("statusCounts", statusCounts);

        long pending = statusCounts.getOrDefault("pending", 0L) + statusCounts.getOrDefault("created", 0L);
        long inTransit = statusCounts.getOrDefault("in_transit", 0L)
                + statusCounts.getOrDefault("assigned", 0L)
                + statusCounts.getOrDefault("picked_up", 0L)
                + statusCounts.getOrDefault("out_for_delivery", 0L);
        long delivered = statusCounts.getOrDefault("delivered", 0L);
        long failed = statusCounts.getOrDefault("failed", 0L)
                + statusCounts.getOrDefault("delivery_failed", 0L)
                + statusCounts.getOrDefault("cancelled", 0L);

        summary.put("pending", pending);
        summary.put("inTransit", inTransit);
        summary.put("delivered", delivered);
        summary.put("failed", failed);

        Double revenue = parcelRepository.sumTotalRevenue();
        summary.put("totalRevenue", revenue != null ? revenue : 0.0);

        // Calculate actual average delivery hours from delivered parcels
        List<Object[]> durations = parcelRepository.findDeliveredDurations();
        Double avgDeliveryHours = null;
        if (durations != null && !durations.isEmpty()) {
            long totalSeconds = 0;
            long count = 0;
            for (Object[] row : durations) {
                Instant created = (Instant) row[0];
                Instant completed = null;
                if (row.length > 1 && row[1] instanceof LocalDateTime ldt) {
                    completed = ldt.atZone(REPORTING_ZONE).toInstant();
                } else if (row.length > 2 && row[2] instanceof Instant inst) {
                    completed = inst;
                }
                if (created != null && completed != null) {
                    long secs = Duration.between(created, completed).getSeconds();
                    if (secs >= 0) {
                        totalSeconds += secs;
                        count++;
                    }
                }
            }
            if (count > 0) {
                double avg = (totalSeconds / 3600.0) / count;
                avgDeliveryHours = Math.round(avg * 10.0) / 10.0;
            }
        }
        summary.put("avgDeliveryHours", avgDeliveryHours);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/daily-volume")
    public ResponseEntity<List<Map<String, Object>>> getDailyVolume(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyPrivilegedRole(userRoles);

        int count = days > 0 ? Math.min(days, 90) : 7;
        LocalDate today = LocalDate.now(REPORTING_ZONE);
        LocalDate startDate = today.minusDays(count - 1);
        Instant since = startDate.atStartOfDay(REPORTING_ZONE).toInstant();

        List<Parcel> parcels = parcelRepository.findParcelsCreatedSince(since);

        // Group by calendar date in Asia/Colombo
        Map<LocalDate, List<Parcel>> parcelsByDate = new HashMap<>();
        if (parcels != null) {
            for (Parcel p : parcels) {
                if (p.getCreatedAt() != null) {
                    LocalDate d = p.getCreatedAt().atZone(REPORTING_ZONE).toLocalDate();
                    parcelsByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(p);
                }
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH);
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate d = startDate.plusDays(i);
            List<Parcel> dayParcels = parcelsByDate.getOrDefault(d, Collections.emptyList());

            long dayTotal = dayParcels.size();
            long dayDelivered = 0;
            long dayFailed = 0;
            double dayRevenue = 0.0;

            for (Parcel p : dayParcels) {
                String st = p.getStatus() != null ? p.getStatus().toLowerCase(Locale.ROOT) : "";
                if ("delivered".equals(st)) {
                    dayDelivered++;
                } else if ("failed".equals(st) || "delivery_failed".equals(st) || "cancelled".equals(st)) {
                    dayFailed++;
                }
                if (!"cancelled".equals(st) && p.getTotalFee() != null) {
                    dayRevenue += p.getTotalFee().doubleValue();
                }
            }

            Map<String, Object> dayMap = new LinkedHashMap<>();
            dayMap.put("date", d.format(fmt));
            dayMap.put("total", dayTotal);
            dayMap.put("delivered", dayDelivered);
            dayMap.put("failed", dayFailed);
            dayMap.put("revenue", Math.round(dayRevenue * 100.0) / 100.0);
            result.add(dayMap);
        }

        return ResponseEntity.ok(result);
    }

    private ParcelBookingResponse toBookingResponse(Parcel p) {
        return new ParcelBookingResponse(
                p.getId(),
                p.getTrackingNumber(),
                p.getStatus(),
                p.getSenderId(),
                p.getSenderName(),
                p.getSenderPhone(),
                p.getRecipientName(),
                p.getRecipientPhone(),
                p.getRecipientAddress(),
                p.getPickupAddress(),
                p.getWeightKg(),
                p.getParcelSize(),
                p.getDescription(),
                p.getFragile(),
                p.getTotalFee(),
                p.getTotalFee() == null ? "PENDING_PRICING_SERVICE" : "CALCULATED",
                p.getPaymentMethod(),
                p.getPaymentStatus(),
                p.getCourierId(),
                p.getEstimatedDeliveryDate(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
