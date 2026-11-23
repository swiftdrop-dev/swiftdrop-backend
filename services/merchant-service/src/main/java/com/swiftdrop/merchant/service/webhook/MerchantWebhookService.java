package com.swiftdrop.merchant.service.webhook;

import com.swiftdrop.merchant.dto.request.RegisterWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryAttemptResponse;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryDetailResponse;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointCreatedResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import com.swiftdrop.merchant.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookService.class);
    private static final int ABSOLUTE_MAX_ATTEMPTS = 8;

    private final MerchantRepository merchantRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final WebhookSecurityValidator securityValidator;
    private final WebhookSignatureService signatureService;
    private final WebhookSecretCryptoService cryptoService;
    private final WebhookDispatcherService dispatcherService;

    public MerchantWebhookService(
            MerchantRepository merchantRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            WebhookSecurityValidator securityValidator,
            WebhookSignatureService signatureService,
            WebhookSecretCryptoService cryptoService,
            WebhookDispatcherService dispatcherService) {
        this.merchantRepository = merchantRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.securityValidator = securityValidator;
        this.signatureService = signatureService;
        this.cryptoService = cryptoService;
        this.dispatcherService = dispatcherService;
    }

    public Merchant validateMerchantOwnership(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));

        if (SecurityUtils.hasRole("ADMIN")) {
            return merchant;
        }

        if (!SecurityUtils.hasRole("MERCHANT")) {
            throw new UnauthorizedException("Merchant role is required.");
        }

        UUID currentUserId = SecurityUtils.requireCurrentUserId();
        if (!merchant.getUserId().equals(currentUserId)) {
            throw new UnauthorizedException("You do not have permission to access resources for merchant: " + merchantId);
        }

        return merchant;
    }

    @Transactional
    public WebhookEndpointCreatedResponse registerEndpoint(UUID merchantId, RegisterWebhookEndpointRequest request) {
        Merchant merchant = validateMerchantOwnership(merchantId);
        securityValidator.validateUrl(request.getUrl());

        String rawSecret = signatureService.generateSecret();
        String encryptedSecret = cryptoService.encrypt(rawSecret);

        WebhookEndpoint endpoint = new WebhookEndpoint(
                merchant,
                request.getUrl().trim(),
                encryptedSecret,
                request.getDescription(),
                request.getSubscribedEvents()
        );

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Registered new webhook endpoint [id={}, url={}] for merchant {}", saved.getId(), saved.getUrl(), merchantId);

        return toCreatedResponse(saved, rawSecret);
    }

    @Transactional
    public WebhookEndpointResponse updateEndpoint(UUID merchantId, UUID endpointId, UpdateWebhookEndpointRequest request) {
        validateMerchantOwnership(merchantId);
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            securityValidator.validateUrl(request.getUrl());
            endpoint.setUrl(request.getUrl().trim());
        }

        if (request.getDescription() != null) {
            endpoint.setDescription(request.getDescription());
        }

        if (request.getSubscribedEvents() != null) {
            endpoint.setSubscribedEventsList(request.getSubscribedEvents());
        }

        if (request.getIsActive() != null) {
            endpoint.setActive(request.getIsActive());
        }

        WebhookEndpoint updated = endpointRepository.save(endpoint);
        log.info("Updated webhook endpoint [id={}] for merchant {}", endpointId, merchantId);

        return toResponse(updated);
    }

    @Transactional
    public WebhookEndpointResponse toggleEndpoint(UUID merchantId, UUID endpointId) {
        validateMerchantOwnership(merchantId);
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));

        endpoint.setActive(!endpoint.isActive());
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Toggled webhook endpoint [id={}, active={}] for merchant {}", endpointId, saved.isActive(), merchantId);

        return toResponse(saved);
    }

    @Transactional
    public void deleteEndpoint(UUID merchantId, UUID endpointId) {
        validateMerchantOwnership(merchantId);
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));

        // Implement soft deletion to preserve historical deliveries and audit attempts
        endpoint.setDeletedAt(Instant.now());
        endpoint.setActive(false);
        // Scrub the secret so it cannot be used for any future operations
        endpoint.setSecret("deleted_" + UUID.randomUUID());
        endpointRepository.save(endpoint);

        log.info("Soft-deleted webhook endpoint [id={}] for merchant {}, preserving audit history", endpointId, merchantId);
    }

    @Transactional
    public WebhookEndpointCreatedResponse rotateSecret(UUID merchantId, UUID endpointId) {
        validateMerchantOwnership(merchantId);
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));

        String newSecret = signatureService.generateSecret();
        String encryptedSecret = cryptoService.encrypt(newSecret);
        endpoint.setSecret(encryptedSecret);
        WebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Rotated secret for webhook endpoint [id={}] for merchant {}", endpointId, merchantId);

        return toCreatedResponse(saved, newSecret);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listEndpoints(UUID merchantId) {
        validateMerchantOwnership(merchantId);
        return endpointRepository.findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(merchantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getEndpoint(UUID merchantId, UUID endpointId) {
        validateMerchantOwnership(merchantId);
        WebhookEndpoint endpoint = endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));

        return toResponse(endpoint);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> getDeliveries(UUID merchantId, String eventType, WebhookDeliveryStatus status, Pageable pageable) {
        validateMerchantOwnership(merchantId);

        Page<WebhookDelivery> deliveries;
        if (eventType != null && !eventType.isBlank() && status != null) {
            deliveries = deliveryRepository.findByMerchantIdAndEventTypeAndStatusOrderByCreatedAtDesc(
                    merchantId, eventType.trim(), status, pageable);
        } else if (eventType != null && !eventType.isBlank()) {
            deliveries = deliveryRepository.findByMerchantIdAndEventTypeOrderByCreatedAtDesc(
                    merchantId, eventType.trim(), pageable);
        } else if (status != null) {
            deliveries = deliveryRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(
                    merchantId, status, pageable);
        } else {
            deliveries = deliveryRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        }

        return deliveries.map(this::toDeliveryResponse);
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryDetailResponse getDeliveryDetail(UUID merchantId, UUID deliveryId) {
        validateMerchantOwnership(merchantId);
        WebhookDelivery delivery = deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found: " + deliveryId));

        List<WebhookDeliveryAttemptResponse> attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId).stream()
                .map(a -> new WebhookDeliveryAttemptResponse(
                        a.getId(),
                        a.getAttemptNumber(),
                        a.getHttpStatus(),
                        a.getResponseBody(),
                        a.getErrorMessage(),
                        a.getDurationMs(),
                        a.getAttemptedAt()))
                .collect(Collectors.toList());

        WebhookDeliveryResponse base = toDeliveryResponse(delivery);
        return new WebhookDeliveryDetailResponse(base, delivery.getPayload(), attempts);
    }

    @Transactional
    public WebhookDeliveryResponse retryDelivery(UUID merchantId, UUID deliveryId) {
        validateMerchantOwnership(merchantId);
        WebhookDelivery delivery = deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook delivery not found: " + deliveryId));

        if (delivery.getStatus() == WebhookDeliveryStatus.SUCCESS) {
            throw new InvalidStateTransitionException("Webhook delivery has already succeeded and cannot be retried.");
        }

        if (delivery.getStatus() == WebhookDeliveryStatus.PROCESSING) {
            throw new InvalidStateTransitionException("Webhook delivery is currently being processed. Please wait for the current attempt to finish.");
        }

        WebhookEndpoint endpoint = delivery.getEndpoint();
        if (endpoint == null || !endpoint.isActive() || endpoint.getDeletedAt() != null) {
            throw new InvalidStateTransitionException("Cannot retry delivery: target webhook endpoint is disabled or has been deleted.");
        }

        if (delivery.getAttemptCount() >= ABSOLUTE_MAX_ATTEMPTS) {
            throw new InvalidStateTransitionException("Maximum total retry limit reached (" + ABSOLUTE_MAX_ATTEMPTS + "). This delivery cannot be retried further.");
        }

        // Explicit reactivation policy: bump maxAttempts by 1 up to ABSOLUTE_MAX_ATTEMPTS if previously FAILED
        if (delivery.getStatus() == WebhookDeliveryStatus.FAILED) {
            delivery.setMaxAttempts(Math.min(delivery.getMaxAttempts() + 1, ABSOLUTE_MAX_ATTEMPTS));
            deliveryRepository.save(delivery);
        }

        // Atomically claim the delivery for dispatch to prevent overlapping concurrent retries
        boolean claimed = dispatcherService.claimForDispatch(
                deliveryId,
                List.of(WebhookDeliveryStatus.FAILED, WebhookDeliveryStatus.RETRYING, WebhookDeliveryStatus.PENDING)
        );
        if (!claimed) {
            throw new InvalidStateTransitionException("Delivery is currently being processed or its status changed concurrently.");
        }

        log.info("Triggering manual retry for webhook delivery [id={}] by merchant {}", deliveryId, merchantId);
        WebhookDelivery result = dispatcherService.executeDelivery(deliveryId);
        return toDeliveryResponse(result);
    }

    private WebhookEndpointResponse toResponse(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getMerchant().getId(),
                endpoint.getUrl(),
                cryptoService.maskSecret(endpoint.getSecret()),
                endpoint.getDescription(),
                endpoint.isActive(),
                endpoint.getSubscribedEventsList(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }

    private WebhookEndpointCreatedResponse toCreatedResponse(WebhookEndpoint endpoint, String rawSecret) {
        return new WebhookEndpointCreatedResponse(
                endpoint.getId(),
                endpoint.getMerchant().getId(),
                endpoint.getUrl(),
                cryptoService.maskSecret(rawSecret),
                endpoint.getDescription(),
                endpoint.isActive(),
                endpoint.getSubscribedEventsList(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt(),
                rawSecret
        );
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEndpoint() != null ? delivery.getEndpoint().getId() : null,
                delivery.getEndpoint() != null ? delivery.getEndpoint().getUrl() : null,
                delivery.getMerchantId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getTrackingNumber(),
                delivery.getParcelId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getMaxAttempts(),
                delivery.getNextRetryAt(),
                delivery.getLastHttpStatus(),
                delivery.getLastError(),
                delivery.getCreatedAt(),
                delivery.getDeliveredAt()
        );
    }
}
