package com.swiftdrop.merchant.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import com.swiftdrop.merchant.service.webhook.WebhookDispatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantWebhookEventConsumerTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookDispatcherService dispatcherService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MerchantWebhookEventConsumer consumer;

    private UUID merchantId;
    private UUID merchantUserId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchantUserId = UUID.randomUUID();

        merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setUserId(merchantUserId);
        merchant.setBusinessName("Test Store");
    }

    @Test
    void shouldDispatchWebhookWhenEventMatchesMerchantAndSubscribedEndpoint() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.store.com/hook", "secret", "Test",
                List.of("PARCEL_OUT_FOR_DELIVERY", "PARCEL_DELIVERED"));
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantId)).thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEndpointIdAndEventId(eq(endpointId), eq("evt-001"))).thenReturn(false);
        when(deliveryRepository.saveAndFlush(any(WebhookDelivery.class))).thenAnswer(i -> {
            WebhookDelivery d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        Map<String, Object> message = Map.of(
                "eventId", "evt-001",
                "eventType", "PARCEL_OUT_FOR_DELIVERY",
                "trackingNumber", "TRK12345678",
                "parcelId", "pcl-1",
                "merchantId", merchantId.toString(),
                "status", "OUT_FOR_DELIVERY"
        );

        consumer.handleLifecycleEvent(message);

        verify(deliveryRepository).saveAndFlush(any(WebhookDelivery.class));
        verify(dispatcherService).executeDelivery(any(WebhookDelivery.class));
    }

    @Test
    void shouldResolveMerchantViaSenderUserId() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.store.com/hook", "secret", "Test",
                List.of("PARCEL_CREATED"));
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        // SenderId corresponds to merchant's user account ID
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantId)).thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEndpointIdAndEventId(eq(endpointId), eq("evt-sender-01"))).thenReturn(false);
        when(deliveryRepository.saveAndFlush(any(WebhookDelivery.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> message = Map.of(
                "eventId", "evt-sender-01",
                "eventType", "PARCEL_CREATED",
                "trackingNumber", "TRK-SND-100",
                "senderId", merchantUserId.toString(),
                "status", "CREATED"
        );

        consumer.handleLifecycleEvent(message);

        verify(merchantRepository).findByUserId(merchantUserId);
        verify(deliveryRepository).saveAndFlush(any(WebhookDelivery.class));
        verify(dispatcherService).executeDelivery(any(WebhookDelivery.class));
    }

    @Test
    void shouldIgnoreCustomerParcelsCleanly() {
        UUID regularCustomerId = UUID.randomUUID();
        when(merchantRepository.findByUserId(regularCustomerId)).thenReturn(Optional.empty());

        Map<String, Object> message = Map.of(
                "eventId", "evt-cust",
                "eventType", "PARCEL_CREATED",
                "trackingNumber", "TRK99999",
                "senderId", regularCustomerId.toString(),
                "status", "CREATED"
        );

        consumer.handleLifecycleEvent(message);

        verify(endpointRepository, never()).findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(any());
        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldIsolateEventsAcrossDifferentMerchants() {
        UUID merchantBId = UUID.randomUUID();
        Merchant merchantB = new Merchant();
        merchantB.setId(merchantBId);

        UUID endpointBId = UUID.randomUUID();
        WebhookEndpoint endpointB = new WebhookEndpoint(merchantB, "https://merchantb.com/hook", "secret", "Desc", List.of("PARCEL_CREATED"));
        endpointB.setId(endpointBId);

        // Event is for merchant B
        when(merchantRepository.findById(merchantBId)).thenReturn(Optional.of(merchantB));
        when(endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantBId)).thenReturn(List.of(endpointB));
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> message = Map.of(
                "eventId", "evt-iso-1",
                "eventType", "PARCEL_CREATED",
                "trackingNumber", "TRK-ISO",
                "merchantId", merchantBId.toString()
        );

        consumer.handleLifecycleEvent(message);

        // Verify merchant A's endpoints were never queried
        verify(endpointRepository, never()).findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantId);
        // Verify only merchant B's endpoint was used
        verify(endpointRepository).findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantBId);
    }

    @Test
    void shouldSkipEndpointWhenNotSubscribedToEventType() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.store.com/hook", "secret", "Test",
                List.of("PARCEL_DELIVERED"));
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantId)).thenReturn(List.of(endpoint));

        Map<String, Object> message = Map.of(
                "eventId", "evt-create",
                "eventType", "PARCEL_CREATED",
                "trackingNumber", "TRK123",
                "merchantId", merchantId.toString()
        );

        consumer.handleLifecycleEvent(message);

        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldSkipDuplicateEventForIdempotency() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.store.com/hook", "secret", "Test",
                List.of("PARCEL_CREATED"));
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchantId)).thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEndpointIdAndEventId(endpointId, "evt-dup")).thenReturn(true);

        Map<String, Object> message = Map.of(
                "eventId", "evt-dup",
                "eventType", "PARCEL_CREATED",
                "trackingNumber", "TRK123",
                "merchantId", merchantId.toString()
        );

        consumer.handleLifecycleEvent(message);

        verify(deliveryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectNullOrMissingTrackingIdentifiers() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handleLifecycleEvent(null));

        Map<String, Object> messageMissingTracking = Map.of(
                "eventType", "PARCEL_CREATED",
                "merchantId", merchantId.toString()
        );
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handleLifecycleEvent(messageMissingTracking));
    }
}
