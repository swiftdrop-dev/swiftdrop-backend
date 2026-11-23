package com.swiftdrop.notification.service;

import com.swiftdrop.notification.entity.enums.NotificationType;
import com.swiftdrop.notification.service.template.NotificationTemplateContent;
import com.swiftdrop.notification.service.template.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateServiceTest {

    private NotificationTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new NotificationTemplateService();
    }

    @Test
    void bookingConfirmedTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.BOOKING_CONFIRMED, "SWD-100", Map.of()
        );
        assertThat(content.subject()).isEqualTo("Booking Confirmed - SWD-100");
        assertThat(content.message()).contains("/track/SWD-100");
    }

    @Test
    void courierAssignedTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.COURIER_ASSIGNED, "SWD-101", Map.of()
        );
        assertThat(content.subject()).isEqualTo("Courier Assigned - SWD-101");
        assertThat(content.message()).contains("courier has been assigned");
    }

    @Test
    void parcelPickedUpTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.PARCEL_PICKED_UP, "SWD-102", Map.of()
        );
        assertThat(content.subject()).isEqualTo("Package Picked Up - SWD-102");
        assertThat(content.message()).contains("collected by our courier");
    }

    @Test
    void inTransitTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.IN_TRANSIT, "SWD-103", Map.of()
        );
        assertThat(content.subject()).isEqualTo("In Transit - SWD-103");
        assertThat(content.message()).contains("in transit");
    }

    @Test
    void outForDeliveryTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.OUT_FOR_DELIVERY, "SWD-104", Map.of()
        );
        assertThat(content.subject()).isEqualTo("Out for Delivery - SWD-104");
        assertThat(content.message()).contains("out for delivery today");
    }

    @Test
    void deliveryFailedTemplate_withReason() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.DELIVERY_FAILED, "SWD-105", Map.of("failureReason", "Recipient unreachable")
        );
        assertThat(content.subject()).isEqualTo("Delivery Attempt Failed - SWD-105");
        assertThat(content.message()).contains("Recipient unreachable");
    }

    @Test
    void deliverySuccessfulTemplate() {
        NotificationTemplateContent content = templateService.generateContent(
                NotificationType.DELIVERY_SUCCESSFUL, "SWD-106", Map.of()
        );
        assertThat(content.subject()).isEqualTo("Delivered Successfully - SWD-106");
        assertThat(content.message()).contains("delivered successfully");
    }
}
