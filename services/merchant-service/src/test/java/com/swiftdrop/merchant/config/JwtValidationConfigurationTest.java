package com.swiftdrop.merchant.config;

import com.swiftdrop.merchant.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtValidationConfiguration.class);

    @Test
    void missingSecretFailsConfigurationStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "JWT_SECRET must be configured for Merchant Service");
        });
    }

    @Test
    void validTestSecretCreatesValidator() {
        contextRunner.withPropertyValues(
                        "merchant.security.jwt-secret=test-only-merchant-jwt-key-material-at-least-32-bytes")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTokenValidator.class);
                });
    }
}
