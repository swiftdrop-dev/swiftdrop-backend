package com.swiftdrop.notification.service.template;

import com.swiftdrop.notification.entity.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationTemplateService {

    public NotificationTemplateContent generateContent(NotificationType type, String trackingNumber, Map<String, Object> metadata) {
        String trk = (trackingNumber != null && !trackingNumber.isBlank()) ? trackingNumber : "Unknown";
        String failureReason = extractMetadata(metadata, "failureReason");
        if (failureReason == null) {
            failureReason = extractMetadata(metadata, "reason");
        }

        return switch (type) {
            case BOOKING_CONFIRMED -> new NotificationTemplateContent(
                    "Booking Confirmed - " + trk,
                    "Your parcel booking " + trk + " has been confirmed and is waiting for courier assignment. Track your delivery at /track/" + trk + "."
            );
            case COURIER_ASSIGNED -> new NotificationTemplateContent(
                    "Courier Assigned - " + trk,
                    "A courier has been assigned to transport your parcel " + trk + ". Pickup is being scheduled."
            );
            case PARCEL_PICKED_UP -> new NotificationTemplateContent(
                    "Package Picked Up - " + trk,
                    "Your package " + trk + " has been collected by our courier and is moving to the sorting facility."
            );
            case IN_TRANSIT -> new NotificationTemplateContent(
                    "In Transit - " + trk,
                    "Your package " + trk + " is currently in transit through the SwiftDrop delivery network."
            );
            case OUT_FOR_DELIVERY -> new NotificationTemplateContent(
                    "Out for Delivery - " + trk,
                    "Your package " + trk + " is out for delivery today. Please ensure someone is available at the destination address."
            );
            case DELIVERY_FAILED -> new NotificationTemplateContent(
                    "Delivery Attempt Failed - " + trk,
                    "Delivery attempt for package " + trk + " was unsuccessful"
                            + (failureReason != null && !failureReason.isBlank() ? " (" + failureReason + ")" : "")
                            + ". A reattempt will be scheduled."
            );
            case DELIVERY_SUCCESSFUL -> new NotificationTemplateContent(
                    "Delivered Successfully - " + trk,
                    "Your package " + trk + " has been delivered successfully. Thank you for using SwiftDrop!"
            );
        };
    }

    private String extractMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
        Object val = metadata.get(key);
        return val != null ? String.valueOf(val).trim() : null;
    }
}
