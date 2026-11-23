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
    private final com.swiftdrop.auth.security.GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this(userRepository, passwordEncoder, jwtUtil, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       com.swiftdrop.auth.security.GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.googleTokenVerifier = googleTokenVerifier;
    }


    // ── Register ─────────────────────────────────────────────

    public AuthResponse register(RegisterRequest request) {
        // 1. Duplicate email check
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        // Public self-registration always creates a customer. Privileged roles are
        // assigned only through a separate, authorized provisioning process.
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(
                request.getName(),
                request.getEmail(),
                hashedPassword,
                request.getPhone(),
                Role.customer
        );
        userRepository.save(user);

        // 4. Generate JWT and return response
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
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

        // 3. Check active account status
        if (!user.isActive()) {
            throw new com.swiftdrop.auth.exception.AccountDeactivatedException("Account is deactivated. Please contact support.");
        }

        // 4. Generate JWT and return response
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }

    // ── Google OAuth 2.0 ─────────────────────────────────────

    @org.springframework.transaction.annotation.Transactional
    public AuthResponse authenticateWithGoogle(com.swiftdrop.auth.dto.request.GoogleAuthRequest request) {
        if (googleTokenVerifier == null) {
            throw new IllegalStateException("Google OAuth 2.0 is not configured on this server.");
        }

        if (request == null || request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new IllegalArgumentException("Google ID token is required.");
        }

        // 1. Cryptographically verify the Google token
        com.swiftdrop.auth.security.GoogleUserClaims claims = googleTokenVerifier.verify(request.getIdToken());

        // 2. Verified email check
        if (!claims.emailVerified()) {
            throw new com.swiftdrop.auth.exception.GoogleTokenValidationException("Google account email is not verified.");
        }

        String googleSub = claims.subject();
        String email = claims.email().toLowerCase().trim();

        // 3. Check if user exists by provider identity
        User user = userRepository.findByProviderAndProviderId("google", googleSub).orElse(null);

        if (user != null) {
            // Existing Google-linked user
            if (!user.isActive()) {
                throw new com.swiftdrop.auth.exception.AccountDeactivatedException("Account is deactivated. Please contact support.");
            }
        } else {
            // Check if user exists by email (Account Linking scenario)
            user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                // Account already exists with this email
                if (!user.isActive()) {
                    throw new com.swiftdrop.auth.exception.AccountDeactivatedException("Account is deactivated. Please contact support.");
                }

                // If user is already linked to a DIFFERENT provider identity, prevent collision/takeover
                if (user.getProviderId() != null && !user.getProviderId().equals(googleSub)) {
                    throw new IllegalArgumentException("This account is already linked to a different provider identity.");
                }

                // Ensure this Google identity is not already linked to another account
                if (userRepository.existsByProviderAndProviderId("google", googleSub)) {
                    throw new IllegalArgumentException("This Google identity is already linked to another account.");
                }

                // Safe linking: link provider and providerId without modifying existing password or role
                user.setProvider("google");
                user.setProviderId(googleSub);
                try {
                    user = userRepository.saveAndFlush(user);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    user = userRepository.findByProviderAndProviderId("google", googleSub)
                            .or(() -> userRepository.findByEmail(email))
                            .orElseThrow(() -> ex);
                    if (!user.isActive()) {
                        throw new com.swiftdrop.auth.exception.AccountDeactivatedException("Account is deactivated. Please contact support.");
                    }
                }
            } else {
                // New registration via Google: Assign default CUSTOMER role
                String name = (claims.name() != null && !claims.name().isBlank())
                        ? claims.name().trim()
                        : email.split("@")[0];

                // Generate secure random unusable password hash so schema non-null constraint is satisfied
                String unusablePasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

                user = new User(
                        name,
                        email,
                        unusablePasswordHash,
                        null,
                        Role.customer,
                        true,
                        "google",
                        googleSub
                );
                try {
                    user = userRepository.saveAndFlush(user);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    user = userRepository.findByProviderAndProviderId("google", googleSub)
                            .or(() -> userRepository.findByEmail(email))
                            .orElseThrow(() -> ex);
                    if (!user.isActive()) {
                        throw new com.swiftdrop.auth.exception.AccountDeactivatedException("Account is deactivated. Please contact support.");
                    }
                }
            }
        }

        // 4. Issue SwiftDrop application JWT
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return buildAuthResponse(token, user);
    }


    // ── Admin User Management ────────────────────────────────

    public org.springframework.data.domain.Page<com.swiftdrop.auth.dto.response.UserAdminResponse> getUsers(
            String query, String roleStr, Boolean active, org.springframework.data.domain.Pageable pageable) {
        Role role = null;
        if (roleStr != null && !roleStr.isBlank() && !roleStr.equalsIgnoreCase("all")) {
            role = Role.fromString(roleStr);
        }
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        if (cleanQuery == null && role == null && active == null) {
            return userRepository.findAll(pageable).map(this::toAdminResponse);
        }

        String searchPattern = cleanQuery != null ? "%" + cleanQuery.toLowerCase() + "%" : null;
        return userRepository.searchUsers(searchPattern, role, active, pageable)
                .map(this::toAdminResponse);
    }

    public com.swiftdrop.auth.dto.response.UserAdminResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftdrop.auth.exception.ResourceNotFoundException("User not found: " + userId));
        return toAdminResponse(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public com.swiftdrop.auth.dto.response.UserAdminResponse updateUserStatus(String userId, boolean active, String callerUserId) {
        if (userId.equals(callerUserId) && !active) {
            throw new IllegalArgumentException("Admins cannot deactivate their own account.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftdrop.auth.exception.ResourceNotFoundException("User not found: " + userId));
        user.setActive(active);
        User saved = userRepository.save(user);
        return toAdminResponse(saved);
    }

    @org.springframework.transaction.annotation.Transactional
    public com.swiftdrop.auth.dto.response.UserAdminResponse updateUserRole(String userId, String newRoleStr, String callerUserId) {
        Role newRole = Role.fromString(newRoleStr);
        if (newRole == null) {
            throw new IllegalArgumentException("Invalid role: " + newRoleStr);
        }
        if (userId.equals(callerUserId)) {
            throw new IllegalArgumentException("Admins cannot modify their own role.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.swiftdrop.auth.exception.ResourceNotFoundException("User not found: " + userId));
        user.setRole(newRole);
        User saved = userRepository.save(user);
        return toAdminResponse(saved);
    }

    public com.swiftdrop.auth.dto.response.UserStatsResponse getUserStats() {
        long total = userRepository.count();
        long active = userRepository.countByActive(true);
        long deactivated = userRepository.countByActive(false);
        long customers = userRepository.countByRole(Role.customer);
        long merchants = userRepository.countByRole(Role.merchant);
        long couriers = userRepository.countByRole(Role.courier);
        long dispatchers = userRepository.countByRole(Role.dispatcher);
        long admins = userRepository.countByRole(Role.admin);
        return new com.swiftdrop.auth.dto.response.UserStatsResponse(
                total, active, deactivated, customers, merchants, couriers, dispatchers, admins);
    }

    private com.swiftdrop.auth.dto.response.UserAdminResponse toAdminResponse(User user) {
        return new com.swiftdrop.auth.dto.response.UserAdminResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name().toLowerCase(),
                user.isActive(),
                user.isActive() ? "ACTIVE" : "DEACTIVATED",
                user.getCreatedAt()
        );
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

