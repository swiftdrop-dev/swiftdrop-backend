package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.security.JwtUtil;
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
        "DB_URL=jdbc:h2:mem:auth-service-admin-tests;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=test-only-h2-password",
        "DB_USERNAME=sa",
        "DB_PASSWORD=test-only-h2-password",
        "JWT_SECRET=test-only-not-a-production-secret-with-32-bytes-minimum",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerIntegrationTest {

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
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        adminUser = new User("Admin", "admin@swiftdrop.lk", passwordEncoder.encode("admin123"), "0112345678", Role.admin, true);
        adminUser = userRepository.save(adminUser);

        customerUser = new User("Kasun", "kasun@swiftdrop.lk", passwordEncoder.encode("kasun123"), "0771234567", Role.customer, true);
        customerUser = userRepository.save(customerUser);

        adminToken = jwtUtil.generateToken(adminUser.getId(), adminUser.getEmail(), "admin");
        customerToken = jwtUtil.generateToken(customerUser.getId(), customerUser.getEmail(), "customer");
    }

    @Test
    @DisplayName("Unauthenticated request to admin endpoint is rejected")
    void unauthenticatedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer role request to admin endpoint is rejected with 403")
    void customerRoleRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can list users with pagination, role, status and search query")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("query", "Kasun")
                        .param("role", "customer")
                        .param("status", "active")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Kasun"))
                .andExpect(jsonPath("$.content[0].email").value("kasun@swiftdrop.lk"))
                .andExpect(jsonPath("$.content[0].role").value("customer"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Admin can get user details by id")
    void adminCanGetUserById() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users/" + customerUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerUser.getId()))
                .andExpect(jsonPath("$.email").value("kasun@swiftdrop.lk"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Admin can deactivate a user and deactivated user cannot log in")
    void adminCanDeactivateUserAndLoginIsBlocked() throws Exception {
        // Deactivate
        mockMvc.perform(patch("/api/v1/auth/admin/users/" + customerUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User-Id", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        // Verify login fails
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kasun@swiftdrop.lk\",\"password\":\"kasun123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Account is deactivated")));
    }

    @Test
    @DisplayName("Admin cannot deactivate themselves")
    void adminCannotDeactivateThemselves() throws Exception {
        mockMvc.perform(patch("/api/v1/auth/admin/users/" + adminUser.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User-Id", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot deactivate their own account")));
    }

    @Test
    @DisplayName("Admin can update user role")
    void adminCanUpdateUserRole() throws Exception {
        mockMvc.perform(put("/api/v1/auth/admin/users/" + customerUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User-Id", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"courier\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("courier"));
    }

    @Test
    @DisplayName("Admin cannot change their own role")
    void adminCannotChangeTheirOwnRole() throws Exception {
        mockMvc.perform(put("/api/v1/auth/admin/users/" + adminUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-User-Id", adminUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"dispatcher\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot modify their own role")));
    }

    @Test
    @DisplayName("Admin can get user stats")
    void adminCanGetUserStats() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.customers").value(1))
                .andExpect(jsonPath("$.admins").value(1));
    }
}
