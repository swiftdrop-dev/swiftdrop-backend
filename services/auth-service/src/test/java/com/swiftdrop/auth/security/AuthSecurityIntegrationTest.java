package com.swiftdrop.auth.security;

import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "DB_URL=jdbc:h2:mem:auth-sec-tests;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=test-only-h2-password",
        "DB_USERNAME=sa",
        "DB_PASSWORD=test-only-h2-password",
        "JWT_SECRET=test-only-not-a-production-secret-with-32-bytes-minimum",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User adminUser;
    private User customerUser;
    private User dispatcherUser;
    private String adminToken;
    private String customerToken;
    private String dispatcherToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        adminUser = new User("Admin", "admin@swiftdrop.lk", passwordEncoder.encode("admin123"), "0112345678", Role.admin, true);
        adminUser = userRepository.save(adminUser);

        customerUser = new User("Customer", "customer@swiftdrop.lk", passwordEncoder.encode("cust123"), "0771234567", Role.customer, true);
        customerUser = userRepository.save(customerUser);

        dispatcherUser = new User("Dispatcher", "dispatcher@swiftdrop.lk", passwordEncoder.encode("disp123"), "0779998877", Role.dispatcher, true);
        dispatcherUser = userRepository.save(dispatcherUser);

        adminToken = jwtUtil.generateToken(adminUser.getId(), adminUser.getEmail(), "admin");
        customerToken = jwtUtil.generateToken(customerUser.getId(), customerUser.getEmail(), "customer");
        dispatcherToken = jwtUtil.generateToken(dispatcherUser.getId(), dispatcherUser.getEmail(), "dispatcher");
    }

    @Test
    @DisplayName("Direct Access Condition 1: Missing JWT on protected admin endpoint is rejected (403/401)")
    void missingJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 2: Invalid/malformed JWT on protected endpoint is rejected (403/401)")
    void invalidJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer invalid.jwt.signature"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 3: Customer JWT denied admin operations (403)")
    void customerJwtDeniedAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 4: Dispatcher JWT denied admin operations (403)")
    void dispatcherJwtDeniedAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 5: Forged X-User-Roles header cannot elevate customer to admin (403)")
    void forgedHeaderCannotElevateCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("X-User-Roles", "ROLE_ADMIN,ROLE_DISPATCHER")
                        .header("X-User-Id", adminUser.getId())
                        .header("X-User-Email", "admin@swiftdrop.lk"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 5b: Forged X-User-Roles header without token cannot access admin (403)")
    void forgedHeaderWithoutTokenDenied() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Id", adminUser.getId())
                        .header("X-User-Email", "admin@swiftdrop.lk"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct Access Condition 6: Valid Admin JWT is permitted (200)")
    void validAdminJwtPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    @DisplayName("Direct Access Condition 7a: Public login endpoint is accessible without JWT")
    void publicLoginAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@swiftdrop.lk\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Direct Access Condition 7b: Public register endpoint is accessible without JWT")
    void publicRegisterAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New User\",\"email\":\"newuser@swiftdrop.lk\",\"password\":\"Pass123!\",\"phone\":\"0771234567\",\"role\":\"customer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Direct Access Condition 7c: Public actuator health is accessible without JWT")
    void publicHealthAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
