package com.swiftdrop.dispatch.config;

import com.swiftdrop.dispatch.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtValidationConfiguration.class);

    @Test
    void missingSecretFailsConfigurationWithoutEchoingValue() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.getStartupFailure().getMessage()).doesNotContain("JWT_SECRET value");
        });
    }

    @Test
    void shortSecretFailsConfiguration() {
        contextRunner.withPropertyValues("jwt.secret=short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void validSecretCreatesValidator() {
        contextRunner.withPropertyValues("jwt.secret=dispatch-test-signing-key-material-32-bytes-minimum").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtTokenValidator.class);
        });
    }
}
