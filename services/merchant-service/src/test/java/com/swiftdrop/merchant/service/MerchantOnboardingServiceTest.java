package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.request.AddMerchantAddressRequest;
import com.swiftdrop.merchant.dto.request.RegisterMerchantRequest;
import com.swiftdrop.merchant.dto.request.SetBankAccountRequest;
import com.swiftdrop.merchant.dto.response.MerchantAddressResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantAddress;
import com.swiftdrop.merchant.entity.MerchantBankAccount;
import com.swiftdrop.merchant.entity.MerchantDocument;
import com.swiftdrop.merchant.entity.enums.BusinessType;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.SettlementCycle;
import com.swiftdrop.merchant.exception.DuplicateResourceException;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantAddressRepository;
import com.swiftdrop.merchant.repository.MerchantBankAccountRepository;
import com.swiftdrop.merchant.repository.MerchantContactRepository;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantContactRepository contactRepository;

    @Mock
    private MerchantAddressRepository addressRepository;

    @Mock
    private MerchantBankAccountRepository bankAccountRepository;

    @Mock
    private MerchantDocumentRepository documentRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private MerchantMapper merchantMapper = new MerchantMapper();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MerchantOnboardingService onboardingService;

    private UUID userId;
    private UUID merchantId;
    private RegisterMerchantRequest registerRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();

        registerRequest = new RegisterMerchantRequest();
        registerRequest.setBusinessName("Acme Logistics Ltd");
        registerRequest.setTradeName("Acme Express");
        registerRequest.setRegistrationNumber("BR-2026-9999");
        registerRequest.setTaxId("TAX-8888");
        registerRequest.setBusinessType(BusinessType.PRIVATE_LIMITED);
        registerRequest.setContactName("John Doe");
        registerRequest.setContactPhone("+94771234567");
        registerRequest.setContactEmail("john@acme.com");
    }

    @Test
    @DisplayName("Should successfully register a new merchant in DRAFT status with outbox event")
    void testRegisterMerchant_Success() {
        when(merchantRepository.existsByUserId(userId)).thenReturn(false);
        when(merchantRepository.existsByRegistrationNumber(registerRequest.getRegistrationNumber())).thenReturn(false);

        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant m = invocation.getArgument(0);
            m.setId(merchantId);
            return m;
        });

        MerchantResponse response = onboardingService.registerMerchant(userId, registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(merchantId);
        assertThat(response.getStatus()).isEqualTo(MerchantStatus.DRAFT);
        assertThat(response.getBusinessName()).isEqualTo("Acme Logistics Ltd");

        verify(outboxEventService).publishEvent(eq("Merchant"), eq(merchantId.toString()), eq("MerchantRegisteredEvent"), any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when registering existing userId")
    void testRegisterMerchant_DuplicateUser() {
        when(merchantRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> onboardingService.registerMerchant(userId, registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Merchant already registered");
    }

    @Test
    @DisplayName("Should add pickup address and set isPrimaryPickup if first address")
    void testAddAddress_FirstAddressBecomesPrimary() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(addressRepository.findByMerchantId(merchantId)).thenReturn(Collections.emptyList());

        AddMerchantAddressRequest addressRequest = new AddMerchantAddressRequest();
        addressRequest.setAddressLabel("Main Hub");
        addressRequest.setStreetAddress("123 Galle Road");
        addressRequest.setCity("Colombo");
        addressRequest.setDistrict("Colombo");
        addressRequest.setLatitude(new BigDecimal("6.9271000"));
        addressRequest.setLongitude(new BigDecimal("79.8612000"));
        addressRequest.setContactPhone("+94771234567");

        when(addressRepository.save(any(MerchantAddress.class))).thenAnswer(invocation -> {
            MerchantAddress addr = invocation.getArgument(0);
            addr.setId(UUID.randomUUID());
            return addr;
        });

        MerchantAddressResponse response = onboardingService.addAddress(merchantId, addressRequest);

        assertThat(response).isNotNull();
        assertThat(response.getCity()).isEqualTo("Colombo");
        assertThat(response.getIsPrimaryPickup()).isTrue();
    }

    @Test
    @DisplayName("Should fail submitForVerification if mandatory steps are missing")
    void testSubmitForVerification_FailsWhenIncomplete() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setStatus(MerchantStatus.DRAFT);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        // Missing addresses, bank, documents
        assertThatThrownBy(() -> onboardingService.submitForVerification(merchantId))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("pickup address");
    }

    @Test
    @DisplayName("Should transition status to SUBMITTED and publish event when all steps complete")
    void testSubmitForVerification_Success() {
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setUserId(userId);
        merchant.setBusinessName("Acme Ltd");
        merchant.setStatus(MerchantStatus.DRAFT);

        MerchantAddress addr = new MerchantAddress();
        addr.setIsPrimaryPickup(true);
        merchant.addAddress(addr);

        MerchantBankAccount bank = new MerchantBankAccount();
        bank.setSettlementCycle(SettlementCycle.WEEKLY);
        merchant.addBankAccount(bank);

        MerchantDocument doc = new MerchantDocument();
        merchant.addDocument(doc);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantResponse response = onboardingService.submitForVerification(merchantId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MerchantStatus.SUBMITTED);
        verify(outboxEventService).publishEvent(eq("Merchant"), eq(merchantId.toString()), eq("MerchantSubmittedEvent"), any());
    }
}
