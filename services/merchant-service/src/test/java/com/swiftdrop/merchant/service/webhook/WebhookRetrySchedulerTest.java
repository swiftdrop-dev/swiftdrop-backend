package com.swiftdrop.merchant.service.webhook;

import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookDeliveryAttempt;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRetrySchedulerTest {

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookDeliveryAttemptRepository attemptRepository;

    @Mock
    private WebhookDispatcherService dispatcherService;

    @InjectMocks
    private WebhookRetryScheduler retryScheduler;

    @Test
    void shouldDispatchPendingRetriesUsingCandidateIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(deliveryRepository.findStuckDeliveryIds(eq(WebhookDeliveryStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of());
        when(deliveryRepository.findRetryCandidateIds(eq(WebhookDeliveryStatus.RETRYING), any(Instant.class)))
                .thenReturn(List.of(id1, id2));

        retryScheduler.processPendingRetries();

        verify(dispatcherService).dispatch(id1);
        verify(dispatcherService).dispatch(id2);
    }

    @Test
    void shouldRecoverStuckDeliveryAfterWorkerCrash() {
        UUID stuckId = UUID.randomUUID();
        when(deliveryRepository.findStuckDeliveryIds(eq(WebhookDeliveryStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(stuckId));

        WebhookDelivery stuckDelivery = new WebhookDelivery();
        stuckDelivery.setId(stuckId);
        stuckDelivery.setStatus(WebhookDeliveryStatus.PROCESSING);
        stuckDelivery.setAttemptCount(1);
        stuckDelivery.setMaxAttempts(5);
        stuckDelivery.setClaimedAt(Instant.now().minusSeconds(600));

        when(deliveryRepository.findById(stuckId)).thenReturn(Optional.of(stuckDelivery));
        when(deliveryRepository.findRetryCandidateIds(eq(WebhookDeliveryStatus.RETRYING), any(Instant.class)))
                .thenReturn(List.of());

        retryScheduler.processPendingRetries();

        // Verify delivery was recovered to RETRYING with reset claim
        assertEquals(WebhookDeliveryStatus.RETRYING, stuckDelivery.getStatus());
        assertEquals(2, stuckDelivery.getAttemptCount());
        assertNull(stuckDelivery.getClaimedAt());
        assertNotNull(stuckDelivery.getNextRetryAt());

        // Verify recovery attempt was logged
        ArgumentCaptor<WebhookDeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(WebhookDeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertEquals(2, attemptCaptor.getValue().getAttemptNumber());
        assertEquals("Worker timeout / crash recovery", attemptCaptor.getValue().getErrorMessage());

        verify(deliveryRepository).save(stuckDelivery);
    }

    @Test
    void shouldMarkPermanentlyFailedWhenStuckDeliveryExceedsMaxAttempts() {
        UUID stuckId = UUID.randomUUID();
        when(deliveryRepository.findStuckDeliveryIds(eq(WebhookDeliveryStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(stuckId));

        WebhookDelivery stuckDelivery = new WebhookDelivery();
        stuckDelivery.setId(stuckId);
        stuckDelivery.setStatus(WebhookDeliveryStatus.PROCESSING);
        stuckDelivery.setAttemptCount(4);
        stuckDelivery.setMaxAttempts(5);
        stuckDelivery.setClaimedAt(Instant.now().minusSeconds(600));

        when(deliveryRepository.findById(stuckId)).thenReturn(Optional.of(stuckDelivery));
        when(deliveryRepository.findRetryCandidateIds(eq(WebhookDeliveryStatus.RETRYING), any(Instant.class)))
                .thenReturn(List.of());

        retryScheduler.processPendingRetries();

        assertEquals(WebhookDeliveryStatus.FAILED, stuckDelivery.getStatus());
        assertEquals(5, stuckDelivery.getAttemptCount());
        assertNull(stuckDelivery.getNextRetryAt());
        assertNull(stuckDelivery.getClaimedAt());
    }
}
