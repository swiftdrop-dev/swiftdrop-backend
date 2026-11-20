package com.swiftdrop.gateway.config;

import com.swiftdrop.gateway.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtValidationConfiguration.class);

    @Test
    void missingSecretFailsConfigurationStartupWithoutExposingASecret() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("JWT_SECRET must be configured for API Gateway");
        });
    }

    @Test
    void shortSecretFailsConfigurationStartup() {
        contextRunner.withPropertyValues("gateway.security.jwt-secret=short-test-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "JWT_SECRET must contain at least 32 UTF-8 bytes of key material");
                });
    }

    @Test
    void validTestKeyCreatesValidator() {
        contextRunner.withPropertyValues(
                        "gateway.security.jwt-secret=test-only-gateway-key-material-with-at-least-32-bytes")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTokenValidator.class);
                });
    }
}
