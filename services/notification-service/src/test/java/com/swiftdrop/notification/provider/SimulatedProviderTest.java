package com.swiftdrop.notification.provider;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.DeliveryStatus;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import com.swiftdrop.notification.entity.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedProviderTest {

    private SimulatedEmailProvider emailProvider;
    private SimulatedSmsProvider smsProvider;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        emailProvider = new SimulatedEmailProvider();
        smsProvider = new SimulatedSmsProvider();
        dispatcher = new NotificationDispatcher(List.of(emailProvider, smsProvider));
        ReflectionTestUtils.setField(dispatcher, "emailEnabled", true);
        ReflectionTestUtils.setField(dispatcher, "smsEnabled", true);
    }

    @Test
    void emailProvider_successfulDispatch() {
        Notification notification = new Notification(
                "evt-100", "parcel-1", "SWD-12345",
                "cust-1", "test@swiftdrop.io", "+1234567890",
                NotificationChannel.EMAIL, NotificationType.BOOKING_CONFIRMED,
                "Booking Confirmed", "Your booking is confirmed", DeliveryStatus.PENDING
        );

        ProviderSendResult result = emailProvider.send(notification);

        assertThat(result.accepted()).isTrue();
        assertThat(result.providerName()).isEqualTo("SimulatedEmailProvider");
        assertThat(result.referenceId()).startsWith("SIM-EML-");
        assertThat(result.message()).contains("Simulated dispatch");
    }

    @Test
    void emailProvider_simulatedFailureTriggered() {
        emailProvider.setSimulateFailure(true);
        Notification notification = new Notification(
                "evt-101", "parcel-2", "SWD-67890",
                "cust-2", "fail@swiftdrop.io", "+1234567890",
                NotificationChannel.EMAIL, NotificationType.BOOKING_CONFIRMED,
                "Booking Confirmed", "Your booking is confirmed", DeliveryStatus.PENDING
        );

        ProviderSendResult result = emailProvider.send(notification);

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("Simulated email delivery failure");
    }

    @Test
    void smsProvider_successfulDispatch() {
        Notification notification = new Notification(
                "evt-102", "parcel-3", "SWD-11111",
                "cust-3", null, "+1987654321",
                NotificationChannel.SMS, NotificationType.OUT_FOR_DELIVERY,
                "Out for Delivery", "Your parcel is out for delivery", DeliveryStatus.PENDING
        );

        ProviderSendResult result = smsProvider.send(notification);

        assertThat(result.accepted()).isTrue();
        assertThat(result.providerName()).isEqualTo("SimulatedSmsProvider");
        assertThat(result.referenceId()).startsWith("SIM-SMS-");
        assertThat(result.message()).contains("Simulated dispatch");
    }

    @Test
    void dispatcher_updatesNotificationStatusOnSuccess() {
        Notification notification = new Notification(
                "evt-103", "parcel-4", "SWD-22222",
                "cust-4", "user@test.io", null,
                NotificationChannel.EMAIL, NotificationType.PARCEL_PICKED_UP,
                "Picked Up", "Parcel collected", DeliveryStatus.PENDING
        );

        ProviderSendResult result = dispatcher.dispatch(notification);

        assertThat(result.accepted()).isTrue();
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void dispatcher_handlesDisabledChannelGracefully() {
        ReflectionTestUtils.setField(dispatcher, "emailEnabled", false);

        Notification notification = new Notification(
                "evt-104", "parcel-5", "SWD-33333",
                "cust-5", "user@test.io", null,
                NotificationChannel.EMAIL, NotificationType.IN_TRANSIT,
                "In Transit", "Parcel moving", DeliveryStatus.PENDING
        );

        ProviderSendResult result = dispatcher.dispatch(notification);

        assertThat(result.accepted()).isFalse();
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(notification.getFailureReason()).contains("Email channel disabled");
    }
}
