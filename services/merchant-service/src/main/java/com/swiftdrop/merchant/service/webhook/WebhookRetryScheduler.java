package com.swiftdrop.merchant.service.webhook;

import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookDeliveryAttempt;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);
    private static final Duration STUCK_PROCESSING_THRESHOLD = Duration.ofMinutes(5);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final WebhookDispatcherService dispatcherService;

    public WebhookRetryScheduler(
            WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            WebhookDispatcherService dispatcherService) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.dispatcherService = dispatcherService;
    }

    @Scheduled(fixedDelayString = "${merchant.webhook.retry.fixed-delay-ms:15000}")
    public void processPendingRetries() {
        recoverStuckDeliveries();

        Instant now = Instant.now();
        List<UUID> candidateIds = deliveryRepository.findRetryCandidateIds(
                WebhookDeliveryStatus.RETRYING, now);

        if (candidateIds.isEmpty()) {
            return;
        }

        log.info("Found {} webhook deliveries scheduled for retry", candidateIds.size());
        for (UUID deliveryId : candidateIds) {
            try {
                dispatcherService.dispatch(deliveryId);
            } catch (Exception e) {
                log.error("Error retrying webhook delivery {}: {}", deliveryId, e.getMessage());
            }
        }
    }

    @Transactional
    public void recoverStuckDeliveries() {
        Instant threshold = Instant.now().minus(STUCK_PROCESSING_THRESHOLD);
        List<UUID> stuckIds = deliveryRepository.findStuckDeliveryIds(
                WebhookDeliveryStatus.PROCESSING, threshold);

        if (stuckIds.isEmpty()) {
            return;
        }

        log.warn("Found {} stuck webhook deliveries in PROCESSING state exceeding threshold; recovering", stuckIds.size());
        for (UUID deliveryId : stuckIds) {
            try {
                recoverSingleDelivery(deliveryId);
            } catch (Exception e) {
                log.error("Failed to recover stuck webhook delivery {}: {}", deliveryId, e.getMessage());
            }
        }
    }

    private void recoverSingleDelivery(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != WebhookDeliveryStatus.PROCESSING) {
            return;
        }

        int currentAttempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(currentAttempt);
        delivery.setClaimedAt(null);
        delivery.setLastError("Worker crashed or dispatch exceeded processing timeout (recovered)");

        if (currentAttempt < delivery.getMaxAttempts()) {
            delivery.setStatus(WebhookDeliveryStatus.RETRYING);
            delivery.setNextRetryAt(Instant.now().plusSeconds(30));
            log.warn("Stuck webhook delivery {} reset to RETRYING (attempt {})", deliveryId, currentAttempt);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setNextRetryAt(null);
            log.error("Stuck webhook delivery {} marked FAILED after exceeding max attempts upon recovery", deliveryId);
        }

        WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt(
                delivery,
                currentAttempt,
                null,
                null,
                "Worker timeout / crash recovery",
                0L
        );
        attemptRepository.save(attempt);
        deliveryRepository.save(delivery);
    }
}
