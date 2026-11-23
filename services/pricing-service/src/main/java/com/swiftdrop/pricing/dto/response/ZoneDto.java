package com.swiftdrop.pricing.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneDto {
    private Long id;

    @NotBlank(message = "Zone name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Zone code is required")
    @Size(max = 20, message = "Code must not exceed 20 characters")
    private String code;

    @Builder.Default
    private Boolean isActive = true;

    private Instant createdAt;
}
