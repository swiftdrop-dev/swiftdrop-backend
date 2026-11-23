package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.response.UserAdminResponse;
import com.swiftdrop.auth.dto.response.UserStatsResponse;
import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.exception.AccountDeactivatedException;
import com.swiftdrop.auth.exception.ResourceNotFoundException;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceAdminTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    @DisplayName("Login blocks deactivated user")
    void loginBlocksDeactivatedUser() {
        User deactivatedUser = new User("Jane", "jane@demo.com", "encodedPass", "0771234567", Role.customer, false);
        when(userRepository.findByEmail("jane@demo.com")).thenReturn(Optional.of(deactivatedUser));
        when(passwordEncoder.matches("pass1234", "encodedPass")).thenReturn(true);

        assertThrows(AccountDeactivatedException.class, () ->
                authService.login(new LoginRequest("jane@demo.com", "pass1234"))
        );
    }

    @Test
    @DisplayName("Get users returns mapped UserAdminResponse page")
    void getUsersReturnsMappedPage() {
        User u1 = new User("Alice", "alice@test.com", "hash", "0771112233", Role.customer, true);
        Page<User> page = new PageImpl<>(List.of(u1));
        when(userRepository.searchUsers(any(), any(), any(), any())).thenReturn(page);

        Page<UserAdminResponse> res = authService.getUsers("Alice", "customer", true, PageRequest.of(0, 10));
        assertEquals(1, res.getTotalElements());
        assertEquals("Alice", res.getContent().getFirst().name());
        assertEquals("alice@test.com", res.getContent().getFirst().email());
        assertTrue(res.getContent().getFirst().active());
        assertEquals("ACTIVE", res.getContent().getFirst().status());
    }

    @Test
    @DisplayName("Get user by id succeeds when user exists")
    void getUserByIdSucceeds() {
        User u = new User("Bob", "bob@test.com", "hash", "0772223344", Role.courier, true);
        when(userRepository.findById("usr_bob")).thenReturn(Optional.of(u));

        UserAdminResponse res = authService.getUserById("usr_bob");
        assertEquals("Bob", res.name());
        assertEquals("courier", res.role());
    }

    @Test
    @DisplayName("Get user by id throws when not found")
    void getUserByIdThrowsWhenNotFound() {
        when(userRepository.findById("usr_none")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.getUserById("usr_none"));
    }

    @Test
    @DisplayName("Admin self-deactivation is prevented")
    void preventAdminSelfDeactivation() {
        assertThrows(IllegalArgumentException.class, () ->
                authService.updateUserStatus("admin_123", false, "admin_123")
        );
    }

    @Test
    @DisplayName("Admin can deactivate another user")
    void adminCanDeactivateOtherUser() {
        User target = new User("Target", "target@test.com", "hash", "0770001122", Role.customer, true);
        when(userRepository.findById("target_123")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserAdminResponse res = authService.updateUserStatus("target_123", false, "admin_123");
        assertFalse(res.active());
        assertEquals("DEACTIVATED", res.status());
    }

    @Test
    @DisplayName("Admin self-role change is prevented")
    void preventAdminSelfRoleChange() {
        assertThrows(IllegalArgumentException.class, () ->
                authService.updateUserRole("admin_123", "dispatcher", "admin_123")
        );
    }

    @Test
    @DisplayName("Admin can update role of another user")
    void adminCanUpdateRoleOfOtherUser() {
        User target = new User("Courier Guy", "c@test.com", "hash", "0779998877", Role.courier, true);
        when(userRepository.findById("usr_courier")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserAdminResponse res = authService.updateUserRole("usr_courier", "dispatcher", "admin_123");
        assertEquals("dispatcher", res.role());
    }

    @Test
    @DisplayName("Get user stats aggregates role counts")
    void getUserStatsAggregatesRoleCounts() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByActive(true)).thenReturn(8L);
        when(userRepository.countByActive(false)).thenReturn(2L);
        when(userRepository.countByRole(Role.customer)).thenReturn(4L);
        when(userRepository.countByRole(Role.merchant)).thenReturn(2L);
        when(userRepository.countByRole(Role.courier)).thenReturn(2L);
        when(userRepository.countByRole(Role.dispatcher)).thenReturn(1L);
        when(userRepository.countByRole(Role.admin)).thenReturn(1L);

        UserStatsResponse stats = authService.getUserStats();
        assertEquals(10L, stats.totalUsers());
        assertEquals(8L, stats.activeUsers());
        assertEquals(2L, stats.deactivatedUsers());
        assertEquals(4L, stats.customers());
        assertEquals(2L, stats.merchants());
        assertEquals(2L, stats.couriers());
        assertEquals(1L, stats.dispatchers());
        assertEquals(1L, stats.admins());
    }
}
