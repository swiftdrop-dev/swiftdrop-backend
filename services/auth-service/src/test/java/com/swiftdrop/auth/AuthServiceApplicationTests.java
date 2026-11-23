package com.swiftdrop.auth;

import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "DB_URL=jdbc:h2:mem:auth-service-tests;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=test-only-h2-password",
        "DB_USERNAME=sa",
        "DB_PASSWORD=test-only-h2-password",
        "JWT_SECRET=test-only-not-a-production-secret-with-32-bytes-minimum",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Test
    void publicRegistrationAlwaysCreatesCustomerAndIgnoresSubmittedPrivilegedRoles() throws Exception {
        String[] privilegedRoles = {"courier", "dispatcher", "merchant"};
        for (int i = 0; i < privilegedRoles.length; i++) {
            String email = "public-register-" + i + "@example.test";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType("application/json")
                            .content("""
                                    {"name":"Test User","email":"%s","password":"test-password-123",
                                     "role":"%s"}
                                    """.formatted(email, privilegedRoles[i])))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("customer"))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.password").doesNotExist());

            var user = userRepository.findByEmail(email).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(Role.customer, user.getRole());
        }
    }

    @Test
    void loginStillReturnsExistingAuthResponseContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"Login User","email":"login@example.test",
                                 "password":"test-password-123"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"login@example.test","password":"test-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("login@example.test"))
                .andExpect(jsonPath("$.role").value("customer"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void demoAccountsAreNotInitializedUnderTestOrDefaultProfiles() {
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmail("courier@demo.com").isEmpty());
    }
}
