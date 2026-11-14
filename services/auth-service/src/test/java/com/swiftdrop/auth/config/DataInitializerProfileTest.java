package com.swiftdrop.auth.config;

import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerProfileTest {

    @Test
    void demoInitializerIsAbsentForDefaultTestProdAndDevPlusProdProfiles() {
        assertInitializerAbsent();
        assertInitializerAbsent("test");
        assertInitializerAbsent("prod");
        assertInitializerAbsent("dev", "prod");
    }

    @Test
    void demoInitializerRunsUnderDevelopmentProfileOnly() throws Exception {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("test-only-password-hash");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.registerBean(UserRepository.class, () -> repository);
            context.registerBean(PasswordEncoder.class, () -> encoder);
            context.register(DataInitializer.class);
            context.refresh();

            context.getBean(CommandLineRunner.class).run();
            verify(repository, times(4)).save(any(User.class));
        }
    }

    private void assertInitializerAbsent(String... profiles) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            if (profiles.length > 0) {
                context.getEnvironment().setActiveProfiles(profiles);
            }
            context.register(DataInitializer.class);
            context.refresh();
            assertTrue(context.getBeansOfType(CommandLineRunner.class).isEmpty());
        }
    }
}
