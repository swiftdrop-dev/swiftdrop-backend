package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.client.ParcelServiceClient;
import com.swiftdrop.merchant.client.PricingServiceClient;
import com.swiftdrop.merchant.dto.request.BulkMerchantOrderRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderAddressRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import com.swiftdrop.merchant.dto.request.MerchantRecipientRequest;
import com.swiftdrop.merchant.dto.response.BulkMerchantOrderResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock
    private MerchantOrderRepository merchantOrderRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantCredentialService merchantCredentialService;

    @Mock
    private PricingServiceClient pricingServiceClient;

    @Mock
    private ParcelServiceClient parcelServiceClient;

    @InjectMocks
    private MerchantOrderService merchantOrderService;

    private Merchant approvedMerchant;
    private Merchant pendingMerchant;
    private MerchantApiCredential validCredential;

    @BeforeEach
    void setUp() {
        approvedMerchant = new Merchant();
        approvedMerchant.setId(UUID.randomUUID());
        approvedMerchant.setUserId(UUID.randomUUID());
        approvedMerchant.setBusinessName("Acme Global");
        approvedMerchant.setStatus(MerchantStatus.APPROVED);

        pendingMerchant = new Merchant();
        pendingMerchant.setId(UUID.randomUUID());
        pendingMerchant.setUserId(UUID.randomUUID());
        pendingMerchant.setBusinessName("Pending Store");
        pendingMerchant.setStatus(MerchantStatus.UNDER_REVIEW);

        validCredential = new MerchantApiCredential();
        validCredential.setId(UUID.randomUUID());
        validCredential.setMerchant(approvedMerchant);
        validCredential.setIsActive(true);
    }

    @Test
    void shouldResolveMerchantWithValidApiKey() {
        when(merchantCredentialService.findValidCredential("sk_live_12345"))
                .thenReturn(Optional.of(validCredential));

        Merchant result = merchantOrderService.resolveMerchant("sk_live_12345", null, null);
        assertNotNull(result);
        assertEquals(approvedMerchant.getId(), result.getId());
    }

    @Test
    void shouldFailResolveWithInvalidApiKey() {
        when(merchantCredentialService.findValidCredential("sk_live_bad"))
                .thenReturn(Optional.empty());

        assertThrows(UnauthenticatedException.class, () ->
                merchantOrderService.resolveMerchant("sk_live_bad", null, null));
    }

    @Test
    void shouldFailResolveWithPendingMerchantStatus() {
        MerchantApiCredential pendingCred = new MerchantApiCredential();
        pendingCred.setMerchant(pendingMerchant);
        pendingCred.setIsActive(true);

        when(merchantCredentialService.findValidCredential("sk_live_pending"))
                .thenReturn(Optional.of(pendingCred));

        assertThrows(UnauthorizedException.class, () ->
                merchantOrderService.resolveMerchant("sk_live_pending", null, null));
    }

    @Test
    void shouldResolveMerchantWithCurrentUserId() {
        when(merchantRepository.findByUserId(approvedMerchant.getUserId()))
                .thenReturn(Optional.of(approvedMerchant));

        Merchant result = merchantOrderService.resolveMerchant(null, null, approvedMerchant.getUserId());
        assertNotNull(result);
        assertEquals(approvedMerchant.getId(), result.getId());
    }

    @Test
    void shouldCreateOrderSuccessfullyAndSave() {
        MerchantOrderCreateRequest req = new MerchantOrderCreateRequest();
        req.setMerchantOrderRef("ORD-999");
        req.setRecipient(new MerchantRecipientRequest("Alice", "+94770000000"));
        req.setPickupAddress(new MerchantOrderAddressRequest("Pickup St", "Colombo", "Colombo", "10000"));
        req.setDeliveryAddress(new MerchantOrderAddressRequest("Drop St", "Galle", "Galle", "80000"));
        req.setWeightKg(new BigDecimal("1.500"));
        req.setSize("MD");
        req.setPaymentMethod("COD");

        when(pricingServiceClient.calculateEstimate(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("450.00"));
        when(parcelServiceClient.bookParcel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ParcelServiceClient.BookParcelResult("prc_999", "SWF-2026-999", "CREATED"));

        when(merchantOrderRepository.save(any(MerchantOrder.class)))
                .thenAnswer(inv -> {
                    MerchantOrder o = inv.getArgument(0);
                    o.setId(UUID.randomUUID());
                    o.setCreatedAt(Instant.now());
                    o.setUpdatedAt(Instant.now());
                    return o;
                });

        MerchantOrderResponse response = merchantOrderService.createOrder(approvedMerchant, req, "idemp-key-1");
        assertNotNull(response);
        assertEquals("ORD-999", response.merchantOrderRef());
        assertEquals("SWF-2026-999", response.trackingNumber());
        assertEquals("prc_999", response.parcelId());
        assertEquals(new BigDecimal("450.00"), response.totalFee());
        verify(merchantOrderRepository).save(any(MerchantOrder.class));
    }

    @Test
    void shouldReturnExistingOrderWhenIdempotencyKeyMatches() {
        MerchantOrder existing = new MerchantOrder();
        existing.setId(UUID.randomUUID());
        existing.setMerchant(approvedMerchant);
        existing.setMerchantOrderRef("ORD-EXISTING");
        existing.setTrackingNumber("SWF-2026-EXISTING");
        existing.setParcelId("prc_existing");
        existing.setStatus("CREATED");
        existing.setRecipientName("Bob");
        existing.setRecipientPhone("+94771111111");
        existing.setRecipientAddress("Drop St");
        existing.setPickupAddress("Pickup St");
        existing.setWeightKg(new BigDecimal("1.000"));
        existing.setParcelSize("STANDARD");
        existing.setPaymentMethod("COD");
        existing.setDeliveryFee(new BigDecimal("350.00"));
        existing.setTotalFee(new BigDecimal("350.00"));
        existing.setCurrency("LKR");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(merchantOrderRepository.findByMerchantIdAndIdempotencyKey(approvedMerchant.getId(), "existing-key"))
                .thenReturn(Optional.of(existing));

        MerchantOrderCreateRequest req = new MerchantOrderCreateRequest();
        req.setMerchantOrderRef("ORD-EXISTING");
        req.setWeightKg(BigDecimal.ONE);

        MerchantOrderResponse response = merchantOrderService.createOrder(approvedMerchant, req, "existing-key");
        assertNotNull(response);
        assertEquals("ORD-EXISTING", response.merchantOrderRef());
        verify(parcelServiceClient, never()).bookParcel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldHandleBulkOrdersWithPartialFailures() {
        MerchantOrderCreateRequest req1 = new MerchantOrderCreateRequest();
        req1.setMerchantOrderRef("ORD-BULK-1");
        req1.setWeightKg(new BigDecimal("1.0"));

        MerchantOrderCreateRequest req2 = new MerchantOrderCreateRequest();
        req2.setMerchantOrderRef("ORD-BULK-2");
        req2.setWeightKg(new BigDecimal("2.0"));

        when(pricingServiceClient.calculateEstimate(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("350.00"))
                .thenThrow(new RuntimeException("Pricing service down"));

        when(parcelServiceClient.bookParcel(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ParcelServiceClient.BookParcelResult("prc_1", "SWF-1", "CREATED"));

        when(merchantOrderRepository.save(any(MerchantOrder.class)))
                .thenAnswer(inv -> {
                    MerchantOrder o = inv.getArgument(0);
                    o.setId(UUID.randomUUID());
                    o.setCreatedAt(Instant.now());
                    o.setUpdatedAt(Instant.now());
                    return o;
                });

        BulkMerchantOrderRequest bulkReq = new BulkMerchantOrderRequest(List.of(req1, req2));
        BulkMerchantOrderResponse bulkResponse = merchantOrderService.createBulkOrders(approvedMerchant, bulkReq);

        assertNotNull(bulkResponse);
        assertEquals(2, bulkResponse.getTotalCount());
        assertEquals(1, bulkResponse.getSuccessCount());
        assertEquals(1, bulkResponse.getFailureCount());
        assertFalse(bulkResponse.isSuccess());
        assertEquals(1, bulkResponse.getSuccessfulOrders().size());
        assertEquals(1, bulkResponse.getFailedOrders().size());
    }

    @Test
    void shouldListOrders() {
        MerchantOrder order = new MerchantOrder();
        order.setId(UUID.randomUUID());
        order.setMerchant(approvedMerchant);
        order.setMerchantOrderRef("ORD-LIST");
        order.setTrackingNumber("SWF-LIST");
        order.setParcelId("prc_list");
        order.setStatus("CREATED");
        order.setRecipientName("Bob");
        order.setRecipientPhone("+94771111111");
        order.setRecipientAddress("Drop St");
        order.setPickupAddress("Pickup St");
        order.setWeightKg(BigDecimal.ONE);
        order.setParcelSize("STANDARD");
        order.setPaymentMethod("COD");
        order.setDeliveryFee(new BigDecimal("350.00"));
        order.setTotalFee(new BigDecimal("350.00"));
        order.setCurrency("LKR");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        PageImpl<MerchantOrder> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(merchantOrderRepository.findByMerchantIdOrderByCreatedAtDesc(eq(approvedMerchant.getId()), any(Pageable.class)))
                .thenReturn(page);
        lenient().when(merchantOrderRepository.searchOrders(eq(approvedMerchant.getId()), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        MerchantOrderPageResponse response = merchantOrderService.listOrders(
                approvedMerchant, null, null, null, null, null, PageRequest.of(0, 20));
        assertNotNull(response);
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getData().size());
    }

    @Test
    void shouldGetOrderByIdentifier() {
        MerchantOrder order = new MerchantOrder();
        order.setId(UUID.randomUUID());
        order.setMerchant(approvedMerchant);
        order.setMerchantOrderRef("ORD-FIND");
        order.setTrackingNumber("SWF-FIND");
        order.setParcelId("prc_find");
        order.setStatus("CREATED");
        order.setRecipientName("Bob");
        order.setRecipientPhone("+94771111111");
        order.setRecipientAddress("Drop St");
        order.setPickupAddress("Pickup St");
        order.setWeightKg(BigDecimal.ONE);
        order.setParcelSize("STANDARD");
        order.setPaymentMethod("COD");
        order.setDeliveryFee(new BigDecimal("350.00"));
        order.setTotalFee(new BigDecimal("350.00"));
        order.setCurrency("LKR");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        when(merchantOrderRepository.findByMerchantIdAndTrackingNumber(approvedMerchant.getId(), "SWF-FIND"))
                .thenReturn(Optional.of(order));

        MerchantOrderResponse response = merchantOrderService.getOrder(approvedMerchant, "SWF-FIND");
        assertNotNull(response);
        assertEquals("ORD-FIND", response.merchantOrderRef());
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(merchantOrderRepository.findByMerchantIdAndTrackingNumber(approvedMerchant.getId(), "NON_EXIST"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                merchantOrderService.getOrder(approvedMerchant, "NON_EXIST"));
    }
}
