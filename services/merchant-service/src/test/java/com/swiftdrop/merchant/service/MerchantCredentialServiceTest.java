package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.dto.request.GenerateApiKeyRequest;
import com.swiftdrop.merchant.dto.response.ApiKeyCreatedResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantCredentialServiceTest {

    @Mock
    private MerchantApiCredentialRepository credentialRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Spy
    private MerchantMapper merchantMapper = new MerchantMapper();

    @InjectMocks
    private MerchantCredentialService credentialService;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setBusinessName("Acme Global");
    }

    @Test
    @DisplayName("Should generate secure API key with sk_live_ prefix and return raw key")
    void testGenerateApiKey_Production() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(credentialRepository.save(any(MerchantApiCredential.class))).thenAnswer(i -> {
            MerchantApiCredential c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        GenerateApiKeyRequest request = new GenerateApiKeyRequest();
        request.setLabel("Main Production Key");
        request.setEnvironment("PRODUCTION");

        ApiKeyCreatedResponse response = credentialService.generateApiKey(merchantId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRawApiKey()).startsWith("sk_live_");
        assertThat(response.getKeyPrefix()).startsWith("sk_live_");
        assertThat(response.getEnvironment()).isEqualTo("PRODUCTION");

        verify(credentialRepository).save(any(MerchantApiCredential.class));
    }

    @Test
    @DisplayName("Should generate sandbox API key with sk_test_ prefix")
    void testGenerateApiKey_Sandbox() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(credentialRepository.save(any(MerchantApiCredential.class))).thenAnswer(i -> {
            MerchantApiCredential c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        GenerateApiKeyRequest request = new GenerateApiKeyRequest();
        request.setLabel("Sandbox Key");
        request.setEnvironment("SANDBOX");

        ApiKeyCreatedResponse response = credentialService.generateApiKey(merchantId, request);

        assertThat(response).isNotNull();
        assertThat(response.getRawApiKey()).startsWith("sk_test_");
        assertThat(response.getKeyPrefix()).startsWith("sk_test_");
        assertThat(response.getEnvironment()).isEqualTo("SANDBOX");
    }

    @Test
    @DisplayName("Should revoke API key and mark inactive")
    void testRevokeCredential() {
        UUID credentialId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        MerchantApiCredential cred = new MerchantApiCredential();
        cred.setId(credentialId);
        cred.setMerchant(merchant);
        cred.setIsActive(true);

        when(credentialRepository.findById(credentialId)).thenReturn(Optional.of(cred));

        credentialService.revokeCredential(merchantId, credentialId, adminId, "Suspected compromise");

        assertThat(cred.getIsActive()).isFalse();
        assertThat(cred.getRevokedBy()).isEqualTo(adminId);
        assertThat(cred.getRevocationReason()).isEqualTo("Suspected compromise");
        verify(credentialRepository).save(cred);
    }
}
