package com.swiftdrop.gateway.service;

import com.swiftdrop.gateway.model.CourierRecord;
import com.swiftdrop.gateway.model.ParcelRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ParcelDatabaseService {

    private final List<ParcelRecord> parcels = new ArrayList<>();
    private final List<CourierRecord> couriers = new ArrayList<>();

    public ParcelDatabaseService() {
        seedInitialData();
    }

    private void seedInitialData() {
        // Couriers
        couriers.add(new CourierRecord("cou_001", "Nimal Senanayake", "076-9876543", "Bike", "WP-2345", 4.8, true));
        couriers.add(new CourierRecord("cou_002", "Kasun Fernando", "071-2345678", "Scooter", "CP-9876", 4.6, true));
        couriers.add(new CourierRecord("cou_003", "Priya Ranasinghe", "070-5566778", "Van", "SP-1122", 4.9, false));
        couriers.add(new CourierRecord("cou_004", "Dilshan Jayawardena", "077-8899001", "Bike", "NW-4433", 4.4, true));

        // Parcels
        parcels.add(new ParcelRecord(
                "prc_001", "SWF-2024-001234", "delivered",
                "usr_001", "Amal Perera", "077-1234567",
                "Kamani Silva", "072-3456789", "45 Galle Road, Colombo", "12 Kandy Road, Colombo",
                "Electronics - Laptop", 2.5, 435.0, "paid",
                "Nimal Senanayake", "076-9876543", "2024-11-01",
                List.of("Booking confirmed", "Picked up from Kandy Road", "In transit", "Delivered successfully")
        ));

        parcels.add(new ParcelRecord(
                "prc_002", "SWF-2024-001235", "in_transit",
                "usr_001", "Amal Perera", "077-1234567",
                "Roshan Mendis", "071-8765432", "88 Flower Road, Kandy", "12 Kandy Road, Colombo",
                "Clothes & Accessories", 0.8, 380.0, "paid",
                "Kasun Fernando", "071-2345678", "2024-11-10",
                List.of("Booking confirmed", "Courier assigned", "Picked up", "In transit to Kandy hub")
        ));

        parcels.add(new ParcelRecord(
                "prc_003", "SWF-2024-001236", "pending",
                "usr_001", "Amal Perera", "077-1234567",
                "Dilini Jayasekara", "075-5554433", "22 Main Street, Galle", "12 Kandy Road, Colombo",
                "Books", 1.2, 350.0, "pending",
                "Unassigned", "-", "2024-11-12",
                List.of("Booking confirmed, awaiting courier assignment")
        ));

        parcels.add(new ParcelRecord(
                "prc_004", "SWF-2024-001237", "assigned",
                "usr_002", "Lanka Traders", "011-5678900",
                "Thilak Bandara", "076-1122334", "5 Lake Drive, Kurunegala", "78 Industrial Zone, Colombo",
                "Auto Parts", 5.0, 750.0, "paid",
                "Nimal Senanayake", "076-9876543", "2024-11-11",
                List.of("Booking confirmed", "Assigned to Nimal Senanayake for pickup")
        ));

        parcels.add(new ParcelRecord(
                "prc_005", "SWF-2024-001238", "picked_up",
                "usr_002", "Lanka Traders", "011-5678900",
                "Sunil Wickrama", "070-9988776", "10 Temple Road, Negombo", "78 Industrial Zone, Colombo",
                "Garments (3 boxes)", 3.5, 620.0, "pending",
                "Dilshan Jayawardena", "077-8899001", "2024-11-10",
                List.of("Booking confirmed", "Picked up from sender, arriving at sorting center")
        ));

        parcels.add(new ParcelRecord(
                "prc_006", "SWF-2024-001239", "delivered",
                "usr_002", "Lanka Traders", "011-5678900",
                "Nalini Kumar", "074-6677889", "99 Green Path, Colombo", "78 Industrial Zone, Colombo",
                "Household Items", 1.0, 350.0, "paid",
                "Priya Ranasinghe", "070-5566778", "2024-11-05",
                List.of("Booking confirmed", "Picked up", "Out for delivery", "Delivered with signature")
        ));
    }

    public List<ParcelRecord> findParcelsByUserId(String userId) {
        if (userId == null || userId.isBlank()) return Collections.emptyList();
        return parcels.stream()
                .filter(p -> userId.equalsIgnoreCase(p.getSenderId()))
                .collect(Collectors.toList());
    }

    public Optional<ParcelRecord> findByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) return Optional.empty();
        String cleaned = trackingNumber.trim().toUpperCase(Locale.ROOT);
        return parcels.stream()
                .filter(p -> p.getTrackingNumber().equalsIgnoreCase(cleaned))
                .findFirst();
    }

    public List<CourierRecord> getAllCouriers() {
        return Collections.unmodifiableList(couriers);
    }

    /**
     * Extracts potential tracking number mentions like SWF-2024-001234 from text.
     */
    public Optional<String> extractTrackingNumber(String query) {
        if (query == null) return Optional.empty();
        Pattern pattern = Pattern.compile("(?i)(SWF-[0-9A-Z-]+|SWD-[0-9A-Z-]+)");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    /**
     * Builds live database context dynamically based on the user's ID and query.
     */
    public String buildDatabaseContext(String userId, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SWIFTDROP DATABASE LIVE CONTEXT ===\n");

        // 1. Check if a specific tracking number was mentioned
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
                sb.append("  - Estimated Delivery: ").append(p.getEstimatedDeliveryDate()).append("\n");
                sb.append("  - Status Updates: ").append(String.join(" -> ", p.getStatusHistory())).append("\n\n");
            } else {
                sb.append("SEARCH NOTICE: The tracking number '").append(trackingMention.get())
                        .append("' was not found in the SwiftDrop database. Please ask the user to double check.\n\n");
            }
        }

        // 2. Fetch all parcels owned by the current user
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

        // 3. Active Couriers summary
        sb.append("AVAILABLE COURIERS ON DUTY:\n");
        for (CourierRecord c : couriers) {
            sb.append("  - ").append(c.getName()).append(" (").append(c.getVehicleType())
                    .append(", Phone: ").append(c.getPhone())
                    .append(", Available: ").append(c.isAvailable() ? "YES" : "NO").append(")\n");
        }
        sb.append("=======================================\n");

        return sb.toString();
    }
}
