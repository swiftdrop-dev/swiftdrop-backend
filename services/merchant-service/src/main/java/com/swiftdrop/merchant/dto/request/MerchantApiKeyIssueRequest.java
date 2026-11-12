package com.swiftdrop.merchant.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
public record MerchantApiKeyIssueRequest(@NotBlank @Size(max=100) String name,
    @NotEmpty @Size(max=2) Set<@NotBlank String> scopes, Instant expiresAt) {}
