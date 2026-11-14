package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.PricingTier;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantAuditLogRepository;
import com.swiftdrop.merchant.repository.MerchantDocumentRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAdminServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantDocumentRepository documentRepository;

    @Mock
    private MerchantAuditLogRepository auditLogRepository;

    @Mock
    private MerchantCredentialService credentialService;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private MerchantMapper merchantMapper = new MerchantMapper();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MerchantAdminService adminService;

    private UUID merchantId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should approve merchant in SUBMITTED status and generate initial API key and outbox event")
    void testApproveMerchant_Success() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setUserId(UUID.randomUUID());
        merchant.setBusinessName("Test Enterprise");
        merchant.setStatus(MerchantStatus.SUBMITTED);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantResponse response = adminService.approveMerchant(merchantId, PricingTier.GOLD, adminId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(response.getPricingTier()).isEqualTo(PricingTier.GOLD);
        assertThat(response.getApprovedBy()).isEqualTo(adminId);

        verify(credentialService).generateApiKey(eq(merchantId), any());
        verify(outboxEventService).publishEvent(eq("Merchant"), eq(merchantId.toString()), eq("MerchantApprovedEvent"), any());
    }

    @Test
    @DisplayName("Should reject merchant and save rejection reason")
    void testRejectMerchant_Success() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setUserId(UUID.randomUUID());
        merchant.setStatus(MerchantStatus.SUBMITTED);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantResponse response = adminService.rejectMerchant(merchantId, "Business registration could not be verified", adminId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Business registration could not be verified");

        verify(outboxEventService).publishEvent(eq("Merchant"), eq(merchantId.toString()), eq("MerchantRejectedEvent"), any());
    }

    @Test
    @DisplayName("Should throw exception when attempting to approve merchant in DRAFT status")
    void testApproveMerchant_InvalidStatus() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setStatus(MerchantStatus.DRAFT);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> adminService.approveMerchant(merchantId, PricingTier.STANDARD, adminId))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Only SUBMITTED or UNDER_REVIEW");
    }
}
