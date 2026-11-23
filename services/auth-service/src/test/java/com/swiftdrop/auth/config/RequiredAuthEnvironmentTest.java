package com.swiftdrop.auth.config;

import com.swiftdrop.auth.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredAuthEnvironmentTest {

    @Test
    void missingDbUrlFailsConfiguration() {
        runner().withPropertyValues(validValuesExcept("DB_URL"))
                .run(context -> assertStartupFailureNames(context.getStartupFailure(), "DB_URL"));
    }

    @Test
    void missingDbUsernameFailsConfiguration() {
        runner().withPropertyValues(validValuesExcept("DB_USERNAME"))
                .run(context -> assertStartupFailureNames(context.getStartupFailure(), "DB_USERNAME"));
    }

    @Test
    void missingDbPasswordFailsConfiguration() {
        runner().withPropertyValues(validValuesExcept("DB_PASSWORD"))
                .run(context -> assertStartupFailureNames(context.getStartupFailure(), "DB_PASSWORD"));
    }

    @Test
    void missingJwtSecretFailsConfiguration() {
        runner().withPropertyValues(validValuesExcept("JWT_SECRET"))
                .run(context -> assertStartupFailureNames(context.getStartupFailure(), "JWT_SECRET"));
    }

    @Test
    void blankDbPasswordIsRejectedUnderTestProfile() {
        runner().withPropertyValues(
                        "DB_URL=jdbc:postgresql://localhost:5432/auth_test",
                        "DB_USERNAME=test-user", "DB_PASSWORD=", validSecret(),
                        "spring.profiles.active=test")
                .run(context -> assertStartupFailureNames(context.getStartupFailure(), "DB_PASSWORD"));
    }

    @Test
    void jwtUtilRejectsShortKeyMaterialWithoutEchoingIt() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("short-test-key", 60_000));
        assertTrue(exception.getMessage().contains("JWT_SECRET"));
        assertFalse(exception.getMessage().contains("short-test-key"));
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    ConfigurableEnvironment environment = context.getEnvironment();
                    environment.getPropertySources().remove(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    environment.getPropertySources().remove(
                            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                })
                .withUserConfiguration(RequiredEnvironmentTestConfiguration.class);
    }

    private String[] validValuesExcept(String missingName) {
        return java.util.Arrays.stream(new String[]{
                        "DB_URL=jdbc:postgresql://localhost:5432/auth_test",
                        "DB_USERNAME=test-user",
                        "DB_PASSWORD=test-only-password",
                        "JWT_SECRET=test-only-key-material-at-least-thirty-two-bytes"
                })
                .filter(property -> !property.startsWith(missingName + "="))
                .toArray(String[]::new);
    }

    private String validSecret() {
        return "JWT_SECRET=test-only-key-material-at-least-thirty-two-bytes";
    }

    private void assertStartupFailureNames(Throwable failure, String propertyName) {
        assertTrue(failure != null, "Expected configuration to fail for " + propertyName);
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains(propertyName),
                "Expected failure to identify " + propertyName);
        assertFalse(messages.toString().contains("test-only-password"));
        assertFalse(messages.toString().contains("test-only-key-material"));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RequiredAuthEnvironment.class)
    static class RequiredEnvironmentTestConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
