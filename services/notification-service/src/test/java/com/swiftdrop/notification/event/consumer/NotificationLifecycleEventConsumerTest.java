package com.swiftdrop.notification.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.DeliveryStatus;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import com.swiftdrop.notification.entity.enums.NotificationType;
import com.swiftdrop.notification.provider.NotificationDispatcher;
import com.swiftdrop.notification.provider.ProviderSendResult;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.template.NotificationTemplateContent;
import com.swiftdrop.notification.service.template.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationLifecycleEventConsumerTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationTemplateService templateService;

    @Mock
    private NotificationDispatcher dispatcher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationLifecycleEventConsumer consumer;

    @BeforeEach
    void setUp() {
    }

    @Test
    void handleLifecycleEvent_bookingConfirmed_processesSuccessfully() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-booking-1",
                "eventType", "parcel.booking.confirmed",
                "parcelId", "pcl-101",
                "trackingNumber", "SWD-10101",
                "senderId", "cust-42",
                "senderEmail", "customer42@example.com",
                "senderPhone", "+1555123456"
        );

        when(repository.existsByEventId("evt-booking-1")).thenReturn(false);
        when(templateService.generateContent(eq(NotificationType.BOOKING_CONFIRMED), eq("SWD-10101"), any()))
                .thenReturn(new NotificationTemplateContent("Booking Confirmed - SWD-10101", "Your booking is confirmed"));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dispatcher.dispatch(any(Notification.class))).thenReturn(ProviderSendResult.success("SimulatedEmailProvider", "REF-1", "Sent"));

        consumer.handleLifecycleEvent(payload);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(notifCaptor.capture());

        Notification saved = notifCaptor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-booking-1");
        assertThat(saved.getTrackingNumber()).isEqualTo("SWD-10101");
        assertThat(saved.getRecipientId()).isEqualTo("cust-42");
        assertThat(saved.getRecipientEmail()).isEqualTo("customer42@example.com");
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        verify(dispatcher).dispatch(saved);
        verify(repository).save(saved);
    }

    @Test
    void handleLifecycleEvent_duplicateEventId_isSkippedIdempotently() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-already-processed",
                "eventType", "parcel.picked_up",
                "trackingNumber", "SWD-999"
        );

        when(repository.existsByEventId("evt-already-processed")).thenReturn(true);

        consumer.handleLifecycleEvent(payload);

        verify(repository, never()).saveAndFlush(any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void handleLifecycleEvent_courierAccepted_resolvesCourierAssignedType() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-courier-1",
                "eventType", "courier.accepted",
                "trackingNumber", "SWD-777",
                "customerId", "cust-77"
        );

        when(repository.existsByEventId("evt-courier-1")).thenReturn(false);
        when(templateService.generateContent(eq(NotificationType.COURIER_ASSIGNED), eq("SWD-777"), any()))
                .thenReturn(new NotificationTemplateContent("Courier Assigned", "Courier assigned"));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleLifecycleEvent(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.COURIER_ASSIGNED);
        assertThat(captor.getValue().getRecipientId()).isEqualTo("cust-77");
    }

    @Test
    void handleLifecycleEvent_outForDelivery_processesSuccessfully() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-out-1",
                "eventType", "parcel.out_for_delivery",
                "trackingNumber", "SWD-888",
                "customerId", "cust-88"
        );

        when(repository.existsByEventId("evt-out-1")).thenReturn(false);
        when(templateService.generateContent(eq(NotificationType.OUT_FOR_DELIVERY), eq("SWD-888"), any()))
                .thenReturn(new NotificationTemplateContent("Out for Delivery", "Out for delivery"));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleLifecycleEvent(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.OUT_FOR_DELIVERY);
    }

    @Test
    void handleLifecycleEvent_deliveryFailed_processesWithReason() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-fail-1",
                "eventType", "parcel.delivery_failed",
                "trackingNumber", "SWD-404",
                "customerId", "cust-404",
                "failureReason", "Customer not home"
        );

        when(repository.existsByEventId("evt-fail-1")).thenReturn(false);
        when(templateService.generateContent(eq(NotificationType.DELIVERY_FAILED), eq("SWD-404"), any()))
                .thenReturn(new NotificationTemplateContent("Delivery Failed", "Customer not home"));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handleLifecycleEvent(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.DELIVERY_FAILED);
    }

    @Test
    void handleLifecycleEvent_unsupportedEventType_isIgnoredGracefully() {
        Map<String, Object> payload = Map.of(
                "eventType", "billing.invoice.generated",
                "trackingNumber", "SWD-INV"
        );

        consumer.handleLifecycleEvent(payload);

        verify(repository, never()).saveAndFlush(any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void handleLifecycleEvent_missingTrackingIdentifier_rejectsWithoutRequeue() {
        Map<String, Object> payload = Map.of(
                "eventType", "parcel.in_transit"
        );

        assertThatThrownBy(() -> consumer.handleLifecycleEvent(payload))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Missing tracking identifier");
    }

    @Test
    void handleLifecycleEvent_nullRawMessage_rejectsWithoutRequeue() {
        assertThatThrownBy(() -> consumer.handleLifecycleEvent(null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Payload cannot be null");
    }

    @Test
    void handleLifecycleEvent_malformedJsonString_rejectsWithoutRequeue() {
        assertThatThrownBy(() -> consumer.handleLifecycleEvent("invalid-json{not_a_map"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Malformed JSON message");
    }
}
