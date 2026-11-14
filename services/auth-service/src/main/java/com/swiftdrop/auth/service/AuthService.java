package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegisterRequest;
import com.swiftdrop.auth.dto.response.AuthResponse;
import com.swiftdrop.auth.entity.Role;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ── Register ─────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        // 1. Duplicate email check
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        // 2. Parse and validate role (case-insensitive; frontend sends lowercase)
        Role role;
        try {
            role = Role.fromString(request.getRole());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid role: " + request.getRole());
        }

        // 3. Hash password and persist user
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(
                request.getName(),
                request.getEmail(),
                hashedPassword,
                request.getPhone(),
                role
        );
        userRepository.save(user);

        // 4. Generate JWT and return response
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }

    // ── Login ────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        // 1. Find user — deliberately use the same error message for both
        //    "user not found" and "wrong password" to prevent user enumeration
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        // 2. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        // 3. Generate JWT and return response
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }

    // ── Helpers ──────────────────────────────────────────────

    private AuthResponse buildAuthResponse(String token, User user) {
        // Role is lowercased to match the frontend UserRole union type
        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name().toLowerCase()
        );
    }
}

