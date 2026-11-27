package com.swiftdrop.parcel.security;

import com.swiftdrop.parcel.repository.ParcelRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ParcelSecurityIntegrationTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParcelRepository parcelRepository;

    private String createToken(String role, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("userId", UUID.randomUUID().toString())
                .claim("email", role + "@swiftdrop.lk")
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(KEY)
                .compact();
    }

    private String validToken(String role) {
        return createToken(role, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("Direct access: Missing JWT on admin endpoint is rejected with 401")
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Invalid JWT on admin endpoint is rejected with 401")
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer invalid-garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Expired JWT on admin endpoint is rejected with 401")
    void expiredJwtReturns401() throws Exception {
        String expired = createToken("admin", Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Customer JWT cannot access admin endpoint (403)")
    void customerJwtReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer " + validToken("customer")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN without token cannot elevate (401)")
    void forgedHeaderWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Forged X-User-Roles: ROLE_ADMIN with Customer token cannot elevate (403)")
    void forgedHeaderWithCustomerTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer " + validToken("customer"))
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct access: Valid Admin JWT succeeds (200 OK)")
    void validAdminJwtSucceeds() throws Exception {
        when(parcelRepository.searchParcels(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer " + validToken("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Valid Dispatcher JWT succeeds on admin/all (200 OK)")
    void validDispatcherJwtSucceeds() throws Exception {
        when(parcelRepository.searchParcels(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/parcels/admin/all")
                        .header("Authorization", "Bearer " + validToken("dispatcher")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct access: Parcel track endpoint requires authentication")
    void parcelTrackRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/track/TRK-12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct access: Owner can read a parcel and another customer cannot")
    void ownerCanReadParcelAndOtherCustomerIsHidden() throws Exception {
        UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID otherId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        com.swiftdrop.parcel.entity.Parcel parcel = new com.swiftdrop.parcel.entity.Parcel(
                "prc_sec", null, "TRK-SEC", "CREATED", null, "Owner", "1", "Address");
        parcel.setSenderId(ownerId.toString());
        when(parcelRepository.findById("prc_sec")).thenReturn(java.util.Optional.of(parcel));

        mockMvc.perform(get("/api/v1/parcels/prc_sec")
                        .header("Authorization", "Bearer " + tokenFor("customer", ownerId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/parcels/prc_sec")
                        .header("Authorization", "Bearer " + tokenFor("customer", otherId))
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Direct access: Dispatcher and admin can read a parcel they do not own")
    void dispatcherAndAdminCanReadParcel() throws Exception {
        UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        com.swiftdrop.parcel.entity.Parcel parcel = new com.swiftdrop.parcel.entity.Parcel(
                "prc_sec", null, "TRK-SEC", "CREATED", null, "Owner", "1", "Address");
        parcel.setSenderId(ownerId.toString());
        when(parcelRepository.findById("prc_sec")).thenReturn(java.util.Optional.of(parcel));

        mockMvc.perform(get("/api/v1/parcels/prc_sec")
                        .header("Authorization", "Bearer " + tokenFor("dispatcher", UUID.randomUUID())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/parcels/prc_sec")
                        .header("Authorization", "Bearer " + tokenFor("admin", UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    private String tokenFor(String role, UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("email", role + "@swiftdrop.lk")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(KEY)
                .compact();
    }

    @Test
    @DisplayName("Direct access: Public actuator health accessible without token (200 OK)")
    void publicActuatorHealthAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
