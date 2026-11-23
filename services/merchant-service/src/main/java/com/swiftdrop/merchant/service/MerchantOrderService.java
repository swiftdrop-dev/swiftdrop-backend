package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.client.ParcelServiceClient;
import com.swiftdrop.merchant.client.PricingServiceClient;
import com.swiftdrop.merchant.dto.request.BulkMerchantOrderRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderAddressRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import com.swiftdrop.merchant.dto.request.MerchantRecipientRequest;
import com.swiftdrop.merchant.dto.response.BulkOrderErrorItem;
import com.swiftdrop.merchant.dto.response.BulkMerchantOrderResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailsDto;
import com.swiftdrop.merchant.dto.response.MerchantOrderPageResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.entity.MerchantOrder;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthenticatedException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import com.swiftdrop.merchant.repository.MerchantOrderRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MerchantOrderService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOrderService.class);

    private final MerchantOrderRepository merchantOrderRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantCredentialService merchantCredentialService;
    private final PricingServiceClient pricingServiceClient;
    private final ParcelServiceClient parcelServiceClient;

    public MerchantOrderService(
            MerchantOrderRepository merchantOrderRepository,
            MerchantRepository merchantRepository,
            MerchantCredentialService merchantCredentialService,
            PricingServiceClient pricingServiceClient,
            ParcelServiceClient parcelServiceClient) {
        this.merchantOrderRepository = merchantOrderRepository;
        this.merchantRepository = merchantRepository;
        this.merchantCredentialService = merchantCredentialService;
        this.pricingServiceClient = pricingServiceClient;
        this.parcelServiceClient = parcelServiceClient;
    }

    @Transactional(readOnly = true)
    public Merchant resolveMerchant(String xApiKey, String authorization, UUID currentUserId) {
        String rawApiKey = null;
        if (xApiKey != null && !xApiKey.isBlank()) {
            rawApiKey = xApiKey.trim();
        } else if (authorization != null) {
            String trimmed = authorization.trim();
            if (trimmed.startsWith("ApiKey ")) {
                rawApiKey = trimmed.substring(7).trim();
            } else if (trimmed.startsWith("Bearer sk_")) {
                rawApiKey = trimmed.substring(7).trim();
            }
        }

        if (rawApiKey != null && !rawApiKey.isBlank()) {
            Optional<MerchantApiCredential> credOpt = merchantCredentialService.findValidCredential(rawApiKey);
            if (credOpt.isEmpty()) {
                throw new UnauthenticatedException("Invalid, expired, or revoked API key");
            }
            Merchant merchant = credOpt.get().getMerchant();
            verifyMerchantApproved(merchant);
            return merchant;
        }

        if (currentUserId != null) {
            Merchant merchant = merchantRepository.findByUserId(currentUserId)
                    .orElseThrow(() -> new UnauthenticatedException("No merchant profile associated with authenticated user"));
            verifyMerchantApproved(merchant);
            return merchant;
        }

        throw new UnauthenticatedException("Authentication required: provide an X-API-Key or Bearer token");
    }

    private void verifyMerchantApproved(Merchant merchant) {
        if (merchant.getStatus() != MerchantStatus.APPROVED && merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new UnauthorizedException("Merchant account is not active or approved (current status: "
                    + merchant.getStatus() + ")");
        }
    }

    @Transactional
    public MerchantOrderResponse createOrder(Merchant merchant, MerchantOrderCreateRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<MerchantOrder> existingByIdempotency = merchantOrderRepository
                    .findByMerchantIdAndIdempotencyKey(merchant.getId(), idempotencyKey.trim());
            if (existingByIdempotency.isPresent()) {
                log.info("Idempotent order playback for merchant [{}] and key [{}]", merchant.getId(), idempotencyKey);
                return toResponse(existingByIdempotency.get());
            }
        }

        if (request.getMerchantOrderRef() != null && !request.getMerchantOrderRef().isBlank()) {
            Optional<MerchantOrder> existingByRef = merchantOrderRepository
                    .findByMerchantIdAndMerchantOrderRef(merchant.getId(), request.getMerchantOrderRef().trim());
            if (existingByRef.isPresent()) {
                log.info("Existing order reference [{}] returned for merchant [{}]",
                        request.getMerchantOrderRef(), merchant.getId());
                return toResponse(existingByRef.get());
            }
        }

        BigDecimal calculatedFee = pricingServiceClient.calculateEstimate(
                request.getWeightKg(),
                request.getEffectivePickupCity(),
                request.getEffectiveDeliveryCity(),
                request.getEffectiveSize()
        );

        String senderId = merchant.getUserId() != null ? merchant.getUserId().toString() : merchant.getId().toString();
        ParcelServiceClient.BookParcelResult bookResult = parcelServiceClient.bookParcel(
                senderId,
                request.getEffectiveRecipientName(),
                request.getEffectiveRecipientPhone(),
                request.getEffectiveDeliveryAddress(),
                request.getEffectivePickupAddress(),
                request.getDescription(),
                request.getWeightKg(),
                request.getEffectiveSize(),
                request.getEffectiveFragile(),
                request.getEffectivePaymentMethod(),
                calculatedFee
        );

        MerchantOrder order = new MerchantOrder();
        order.setMerchant(merchant);
        order.setMerchantOrderRef(request.getMerchantOrderRef() != null && !request.getMerchantOrderRef().isBlank()
                ? request.getMerchantOrderRef().trim()
                : "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setTrackingNumber(bookResult.trackingNumber());
        order.setParcelId(bookResult.parcelId());
        order.setStatus(bookResult.status());
        order.setRecipientName(request.getEffectiveRecipientName());
        order.setRecipientPhone(request.getEffectiveRecipientPhone());
        order.setRecipientAddress(request.getEffectiveDeliveryAddress());
        order.setRecipientCity(request.getEffectiveDeliveryCity());
        order.setRecipientDistrict(request.getEffectiveDeliveryDistrict());
        order.setRecipientPostalCode(request.getDeliveryAddress() != null ? request.getDeliveryAddress().postalCode() : null);
        order.setPickupAddress(request.getEffectivePickupAddress());
        order.setPickupCity(request.getEffectivePickupCity());
        order.setPickupDistrict(request.getEffectivePickupDistrict());
        order.setPickupPostalCode(request.getPickupAddress() != null ? request.getPickupAddress().postalCode() : null);
        order.setDescription(request.getDescription());
        order.setWeightKg(request.getWeightKg());
        order.setParcelSize(request.getEffectiveSize());
        order.setDeclaredValue(request.getDeclaredValue());
        order.setFragile(request.getEffectiveFragile());
        order.setSpecialInstructions(request.getSpecialInstructions());
        order.setPaymentMethod(request.getEffectivePaymentMethod());
        order.setDeliveryFee(calculatedFee);
        order.setTotalFee(calculatedFee);
        order.setCurrency("LKR");
        order.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null);

        MerchantOrder saved = merchantOrderRepository.save(order);
        log.info("Created merchant order [id={}, ref={}, tracking={}] for merchant [{}]",
                saved.getId(), saved.getMerchantOrderRef(), saved.getTrackingNumber(), merchant.getId());

        return toResponse(saved);
    }

    @Transactional
    public BulkMerchantOrderResponse createBulkOrders(Merchant merchant, BulkMerchantOrderRequest bulkRequest) {
        List<MerchantOrderResponse> successfulOrders = new ArrayList<>();
        List<BulkOrderErrorItem> failedOrders = new ArrayList<>();

        List<MerchantOrderCreateRequest> orders = bulkRequest.getOrders();
        for (int i = 0; i < orders.size(); i++) {
            MerchantOrderCreateRequest req = orders.get(i);
            try {
                MerchantOrderResponse created = createOrder(merchant, req, null);
                successfulOrders.add(created);
            } catch (Exception ex) {
                log.warn("Failed to create bulk order at index [{}]: {}", i, ex.getMessage());
                failedOrders.add(new BulkOrderErrorItem(
                        i,
                        req.getMerchantOrderRef(),
                        "CREATION_FAILED",
                        ex.getMessage()
                ));
            }
        }

        return new BulkMerchantOrderResponse(
                orders.size(),
                successfulOrders.size(),
                failedOrders.size(),
                successfulOrders,
                failedOrders
        );
    }

    @Transactional(readOnly = true)
    public MerchantOrderPageResponse listOrders(
            Merchant merchant,
            String status,
            String merchantOrderRef,
            String trackingNumber,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable) {
        String cleanStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String cleanRef = merchantOrderRef != null && !merchantOrderRef.isBlank() ? merchantOrderRef.trim() : null;
        String cleanTracking = trackingNumber != null && !trackingNumber.isBlank() ? trackingNumber.trim() : null;

        Page<MerchantOrder> orderPage;
        if (cleanStatus == null && cleanRef == null && cleanTracking == null && createdFrom == null && createdTo == null) {
            orderPage = merchantOrderRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.getId(), pageable);
        } else {
            orderPage = merchantOrderRepository.searchOrders(
                    merchant.getId(),
                    cleanStatus,
                    cleanRef,
                    cleanTracking,
                    createdFrom,
                    createdTo,
                    pageable
            );
        }

        List<MerchantOrderResponse> items = orderPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new MerchantOrderPageResponse(
                items,
                orderPage.getTotalElements(),
                orderPage.getNumber(),
                orderPage.getSize(),
                orderPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public MerchantOrderResponse getOrder(Merchant merchant, String orderIdentifier) {
        if (orderIdentifier == null || orderIdentifier.isBlank()) {
            throw new ResourceNotFoundException("Order identifier is required");
        }

        String trimmed = orderIdentifier.trim();
        Optional<MerchantOrder> found = Optional.empty();

        try {
            UUID orderUuid = UUID.fromString(trimmed);
            found = merchantOrderRepository.findByIdAndMerchantId(orderUuid, merchant.getId());
        } catch (IllegalArgumentException ignored) {
        }

        if (found.isEmpty()) {
            found = merchantOrderRepository.findByMerchantIdAndTrackingNumber(merchant.getId(), trimmed);
        }

        if (found.isEmpty()) {
            found = merchantOrderRepository.findByMerchantIdAndParcelId(merchant.getId(), trimmed);
        }

        if (found.isEmpty()) {
            found = merchantOrderRepository.findByMerchantIdAndMerchantOrderRef(merchant.getId(), trimmed);
        }

        return found.map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with identifier: " + orderIdentifier));
    }

    public MerchantOrderResponse toResponse(MerchantOrder o) {
        MerchantRecipientRequest recipient = new MerchantRecipientRequest(
                o.getRecipientName(),
                o.getRecipientPhone()
        );

        MerchantOrderAddressRequest pickup = new MerchantOrderAddressRequest(
                o.getPickupAddress(),
                o.getPickupCity(),
                o.getPickupDistrict(),
                o.getPickupPostalCode()
        );

        MerchantOrderAddressRequest delivery = new MerchantOrderAddressRequest(
                o.getRecipientAddress(),
                o.getRecipientCity(),
                o.getRecipientDistrict(),
                o.getRecipientPostalCode()
        );

        MerchantOrderDetailsDto details = new MerchantOrderDetailsDto(
                o.getMerchantOrderRef(),
                recipient,
                pickup,
                delivery,
                o.getDescription(),
                o.getWeightKg(),
                o.getParcelSize(),
                o.getDeclaredValue(),
                o.getFragile(),
                o.getSpecialInstructions(),
                o.getPaymentMethod()
        );

        return new MerchantOrderResponse(
                o.getId(),
                o.getMerchantOrderRef(),
                o.getTrackingNumber(),
                o.getParcelId(),
                o.getStatus(),
                details,
                o.getDeliveryFee(),
                o.getTotalFee(),
                o.getCurrency(),
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }
}
