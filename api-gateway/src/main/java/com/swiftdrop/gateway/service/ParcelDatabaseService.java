package com.swiftdrop.gateway.service;

import com.swiftdrop.gateway.entity.CourierEntity;
import com.swiftdrop.gateway.entity.ParcelEntity;
import com.swiftdrop.gateway.model.CourierRecord;
import com.swiftdrop.gateway.model.ParcelRecord;
import com.swiftdrop.gateway.repository.CourierRepository;
import com.swiftdrop.gateway.repository.ParcelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ParcelDatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(ParcelDatabaseService.class);

    private final ParcelRepository parcelRepository;
    private final CourierRepository courierRepository;

    public ParcelDatabaseService(ParcelRepository parcelRepository, CourierRepository courierRepository) {
        this.parcelRepository = parcelRepository;
        this.courierRepository = courierRepository;
    }

    public List<ParcelRecord> findParcelsByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Collections.emptyList();
        try {
            List<ParcelEntity> entities = parcelRepository.findBySenderId(userId.trim());
            return entities.stream().map(this::toRecord).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not query parcels from PostgreSQL for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<ParcelRecord> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) return Optional.empty();
        try {
            return parcelRepository.findByTrackingNumberIgnoreCase(trackingNumber.trim())
                    .map(this::toRecord);
        } catch (Exception e) {
            logger.warn("Could not query tracking number {} from PostgreSQL: {}", trackingNumber, e.getMessage());
            return Optional.empty();
        }
    }

    public List<CourierRecord> getAllCouriers() {
        try {
            return courierRepository.findAll().stream()
                    .map(c -> new CourierRecord(
                            c.getId(),
                            c.getName(),
                            c.getPhone(),
                            c.getVehicleType(),
                            c.getVehiclePlate(),
                            c.getRating() != null ? c.getRating() : 5.0,
                            Boolean.TRUE.equals(c.getIsAvailable())
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Could not query couriers from PostgreSQL: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<String> extractTrackingNumber(String query) {
        if (query == null) return Optional.empty();
        Pattern pattern = Pattern.compile("(?i)(SWF-[0-9A-Z-]+|SWD-[0-9A-Z-]+)");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    public String buildDatabaseContext(String userId, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SWIFTDROP POSTGRESQL LIVE CONTEXT ===\n");

        // 1. Specific tracking number
        Optional<String> trackingMention = extractTrackingNumber(query);
        if (trackingMention.isPresent()) {
            Optional<ParcelRecord> match = findByTrackingNumber(trackingMention.get());
            if (match.isPresent()) {
                ParcelRecord p = match.get();
                sb.append("SPECIFIC PARCEL SEARCH RESULT (Tracking No: ").append(p.getTrackingNumber()).append("):\n");
                sb.append("  - Status: ").append(p.getStatus().toUpperCase()).append("\n");
                sb.append("  - Description: ").append(p.getDescription()).append("\n");
                sb.append("  - Sender: ").append(p.getSenderName()).append(" (").append(p.getSenderPhone()).append(")\n");
                sb.append("  - Recipient: ").append(p.getRecipientName()).append(" at ").append(p.getRecipientAddress()).append("\n");
                sb.append("  - Courier Assigned: ").append(p.getCourierName()).append(" (Phone: ").append(p.getCourierPhone()).append(")\n");
                sb.append("  - Fee: LKR ").append(p.getTotalFee()).append(" (Payment: ").append(p.getPaymentStatus()).append(")\n");
                sb.append("  - Estimated Delivery: ").append(p.getEstimatedDeliveryDate()).append("\n\n");
            } else {
                sb.append("SEARCH NOTICE: The tracking number '").append(trackingMention.get())
                        .append("' was not found in the SwiftDrop database. Ask user to check tracking number format.\n\n");
            }
        }

        // 2. User parcels
        List<ParcelRecord> userParcels = findParcelsByUserId(userId);
        if (!userParcels.isEmpty()) {
            sb.append("CURRENT LOGGED-IN USER PARCELS (User ID: ").append(userId).append("):\n");
            for (ParcelRecord p : userParcels) {
                sb.append("  • [").append(p.getTrackingNumber()).append("] ")
                        .append(p.getDescription()).append(" | Status: ").append(p.getStatus().toUpperCase())
                        .append(" | To: ").append(p.getRecipientName()).append(" (").append(p.getRecipientAddress()).append(")")
                        .append(" | Courier: ").append(p.getCourierName())
                        .append(" | Est. Delivery: ").append(p.getEstimatedDeliveryDate()).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("CURRENT LOGGED-IN USER: ").append(userId).append(" (No active parcels found directly under this ID, or user is guest/courier).\n\n");
        }

        // 3. Couriers
        List<CourierRecord> couriers = getAllCouriers();
        if (!couriers.isEmpty()) {
            sb.append("AVAILABLE COURIERS ON DUTY:\n");
            for (CourierRecord c : couriers) {
                sb.append("  - ").append(c.getName()).append(" (").append(c.getVehicleType())
                        .append(", Phone: ").append(c.getPhone())
                        .append(", Available: ").append(c.isAvailable() ? "YES" : "NO").append(")\n");
            }
        }
        sb.append("=========================================\n");

        return sb.toString();
    }

    private ParcelRecord toRecord(ParcelEntity entity) {
        return new ParcelRecord(
                entity.getId(),
                entity.getTrackingNumber(),
                entity.getStatus(),
                entity.getSenderId(),
                entity.getSenderName(),
                entity.getSenderPhone(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getRecipientAddress(),
                entity.getPickupAddress(),
                entity.getDescription(),
                entity.getWeight() != null ? entity.getWeight() : 0.0,
                entity.getTotalFee() != null ? entity.getTotalFee() : 0.0,
                entity.getPaymentStatus(),
                entity.getCourierName(),
                entity.getCourierPhone(),
                entity.getEstimatedDeliveryDate(),
                List.of("Current Status: " + entity.getStatus())
        );
    }
}
