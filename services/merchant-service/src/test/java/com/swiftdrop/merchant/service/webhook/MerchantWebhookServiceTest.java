package com.swiftdrop.merchant.service.webhook;

import com.swiftdrop.merchant.dto.request.RegisterWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.response.WebhookEndpointCreatedResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.entity.enums.BusinessType;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import com.swiftdrop.merchant.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantWebhookServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookDeliveryAttemptRepository attemptRepository;

    @Mock
    private WebhookSecurityValidator securityValidator;

    @Mock
    private WebhookSignatureService signatureService;

    @Spy
    private WebhookSecretCryptoService cryptoService = new WebhookSecretCryptoService(
            "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcy1sb25nISE=");

    @Mock
    private WebhookDispatcherService dispatcherService;

    @InjectMocks
    private MerchantWebhookService webhookService;

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
        merchant.setBusinessName("Test Merchant Store");
        merchant.setRegistrationNumber("REG-12345");
        merchant.setBusinessType(BusinessType.SOLE_PROPRIETORSHIP);

        setSecurityContext(merchantUserId, "ROLE_MERCHANT");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(UUID userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "merchant@example.com", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldRegisterEndpointSuccessfullyAndEncryptSecretAtRest() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(signatureService.generateSecret()).thenReturn("whsec_123456789012345678901234567890123456789012345678");
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> {
            WebhookEndpoint ep = invocation.getArgument(0);
            ep.setId(UUID.randomUUID());
            return ep;
        });

        RegisterWebhookEndpointRequest req = new RegisterWebhookEndpointRequest(
                "https://api.merchant.com/webhooks",
                "Primary order updates",
                List.of("PARCEL_CREATED", "PARCEL_DELIVERED")
        );

        WebhookEndpointCreatedResponse res = webhookService.registerEndpoint(merchantId, req);

        assertNotNull(res);
        assertNotNull(res.getId());
        assertEquals("whsec_123456789012345678901234567890123456789012345678", res.getSecret());
        assertTrue(res.getMaskedSecret().contains("****"));
        assertEquals("https://api.merchant.com/webhooks", res.getUrl());
        verify(securityValidator).validateUrl("https://api.merchant.com/webhooks");

        // Verify stored entity secret is encrypted at rest (starts with enc:v1:)
        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(endpointRepository).save(captor.capture());
        WebhookEndpoint savedEntity = captor.getValue();
        assertTrue(savedEntity.getSecret().startsWith("enc:v1:"));
    }

    @Test
    void shouldRejectAccessWhenMerchantUserDoesNotOwnResource() {
        UUID otherUserId = UUID.randomUUID();
        setSecurityContext(otherUserId, "ROLE_MERCHANT");
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThrows(UnauthorizedException.class, () -> webhookService.listEndpoints(merchantId));
    }

    @Test
    void shouldAllowAdminAccessToAnyMerchant() {
        UUID adminUserId = UUID.randomUUID();
        setSecurityContext(adminUserId, "ROLE_ADMIN");
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(merchantId)).thenReturn(Collections.emptyList());

        List<WebhookEndpointResponse> endpoints = webhookService.listEndpoints(merchantId);
        assertNotNull(endpoints);
    }

    @Test
    void shouldRotateSecretSuccessfullyAndStoreEncrypted() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.merchant.com/hook", cryptoService.encrypt("whsec_old"), "Desc", List.of());
        endpoint.setId(endpointId);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)).thenReturn(Optional.of(endpoint));
        when(signatureService.generateSecret()).thenReturn("whsec_new_secret_12345678901234567890123456789012345678");
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookEndpointCreatedResponse response = webhookService.rotateSecret(merchantId, endpointId);
        assertEquals("whsec_new_secret_12345678901234567890123456789012345678", response.getSecret());
        assertTrue(endpoint.getSecret().startsWith("enc:v1:"), "New secret must be encrypted at rest");
    }

    @Test
    void shouldToggleEndpointActiveState() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.merchant.com/hook", cryptoService.encrypt("whsec_old"), "Desc", List.of());
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)).thenReturn(Optional.of(endpoint));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookEndpointResponse response = webhookService.toggleEndpoint(merchantId, endpointId);
        assertFalse(response.isActive());
    }

    @Test
    void shouldSoftDeleteEndpointAndPreserveDeliveryAuditRecords() {
        UUID endpointId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint(merchant, "https://api.merchant.com/hook", cryptoService.encrypt("whsec_secret"), "Desc", List.of());
        endpoint.setId(endpointId);
        endpoint.setActive(true);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)).thenReturn(Optional.of(endpoint));

        webhookService.deleteEndpoint(merchantId, endpointId);

        // Verify endpoint was NOT deleted from DB (preventing cascade delete of deliveries)
        verify(endpointRepository, never()).delete(any());

        // Verify soft deletion fields were updated and secret scrambled
        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(endpointRepository).save(captor.capture());
        WebhookEndpoint saved = captor.getValue();
        assertNotNull(saved.getDeletedAt());
        assertFalse(saved.isActive());
        assertTrue(saved.getSecret().startsWith("deleted_"));
    }

    @Test
    void shouldRejectRetryIfAlreadySucceeded() {
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.SUCCESS);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));

        assertThrows(InvalidStateTransitionException.class, () -> webhookService.retryDelivery(merchantId, deliveryId));
    }

    @Test
    void shouldRejectRetryIfCurrentlyProcessing() {
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.PROCESSING);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));

        assertThrows(InvalidStateTransitionException.class, () -> webhookService.retryDelivery(merchantId, deliveryId));
    }

    @Test
    void shouldRejectRetryIfEndpointIsSoftDeleted() {
        UUID deliveryId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setDeletedAt(Instant.now()); // deleted

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setEndpoint(endpoint);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));

        assertThrows(InvalidStateTransitionException.class, () -> webhookService.retryDelivery(merchantId, deliveryId));
    }

    @Test
    void shouldEnforceMaxTotalAttemptsOnManualRetry() {
        UUID deliveryId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setActive(true);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setEndpoint(endpoint);
        delivery.setAttemptCount(8); // Absolute max reached

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));

        assertThrows(InvalidStateTransitionException.class, () -> webhookService.retryDelivery(merchantId, deliveryId));
    }

    @Test
    void shouldReactivateFailedDeliveryAndClaimAtomically() {
        UUID deliveryId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setActive(true);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setEndpoint(endpoint);
        delivery.setAttemptCount(5);
        delivery.setMaxAttempts(5);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));
        when(dispatcherService.claimForDispatch(eq(deliveryId), any())).thenReturn(true);

        WebhookDelivery dispatchedResult = new WebhookDelivery();
        dispatchedResult.setId(deliveryId);
        dispatchedResult.setStatus(WebhookDeliveryStatus.SUCCESS);
        dispatchedResult.setEndpoint(endpoint);
        when(dispatcherService.executeDelivery(deliveryId)).thenReturn(dispatchedResult);

        webhookService.retryDelivery(merchantId, deliveryId);

        // Verify reactivation incremented maxAttempts
        assertEquals(6, delivery.getMaxAttempts());
        verify(deliveryRepository).save(delivery);
        verify(dispatcherService).claimForDispatch(eq(deliveryId), any());
        verify(dispatcherService).executeDelivery(deliveryId);
    }

    @Test
    void shouldRejectConcurrentManualRetryWhenClaimFails() {
        UUID deliveryId = UUID.randomUUID();
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setActive(true);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setStatus(WebhookDeliveryStatus.FAILED);
        delivery.setEndpoint(endpoint);
        delivery.setAttemptCount(2);
        delivery.setMaxAttempts(5);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(deliveryRepository.findByIdAndMerchantId(deliveryId, merchantId)).thenReturn(Optional.of(delivery));
        // Atomic claim fails (another thread claimed it first)
        when(dispatcherService.claimForDispatch(eq(deliveryId), any())).thenReturn(false);

        assertThrows(InvalidStateTransitionException.class, () -> webhookService.retryDelivery(merchantId, deliveryId));
    }
}
