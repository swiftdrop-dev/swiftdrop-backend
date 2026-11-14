package com.swiftdrop.parcel.customer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "swiftdrop.auth.jwt-secret=customer-booking-test-secret-32-bytes-minimum")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CustomerBookingApiTest {
    private static final String BASE = "/api/v1/customer";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CustomerParcelRepository repository;

    @BeforeEach void clear() { repository.deleteAll(); }

    private RequestPostProcessor customer(String id) {
        return jwt().jwt(jwt -> jwt.claim("userId", id).claim("name", "Test Customer").claim("phone", "0771234567"))
                .authorities(new SimpleGrantedAuthority("ROLE_customer"));
    }

    private ObjectNode booking() {
        ObjectNode request = json.createObjectNode();
        request.put("recipientName", "Recipient"); request.put("recipientPhone", "0771234567");
        request.putObject("pickupAddress").put("street", "12 Test Road").put("city", "Colombo").put("district", "Colombo");
        request.putObject("recipientAddress").put("street", "34 Sample Road").put("city", "Kandy").put("district", "Kandy");
        request.put("description", "Books"); request.put("weight", 1.2); request.put("size", "sm");
        request.put("fragile", false); request.put("paymentMethod", "cod");
        return request;
    }

    private JsonNode create(String owner, ObjectNode request) throws Exception {
        MvcResult result = mvc.perform(post(BASE + "/parcels").with(customer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test void bookingPersistsAddressesInitialHistoryServerFeeAndEta() throws Exception {
        JsonNode parcel = create("customer-a", booking());
        assertThat(parcel.get("senderId").asText()).isEqualTo("customer-a");
        assertThat(parcel.get("trackingNumber").asText()).matches("SWF-\\d{4}-[A-F0-9]{32}");
        assertThat(parcel.get("status").asText()).isEqualTo("pending");
        assertThat(parcel.get("totalFee").decimalValue()).isEqualByComparingTo("450");
        assertThat(parcel.get("statusHistory").size()).isEqualTo(1);
        assertThat(parcel.at("/statusHistory/0/status").asText()).isEqualTo("pending");
        LocalDate created = Instant.parse(parcel.get("createdAt").asText()).atZone(ZoneId.of("Asia/Colombo")).toLocalDate();
        assertThat(LocalDate.parse(parcel.get("estimatedDeliveryDate").asText())).isEqualTo(created.plusDays(2));
        mvc.perform(get(BASE + "/parcels/" + parcel.get("id").asText()).with(customer("customer-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickupAddress.street").value("12 Test Road"))
                .andExpect(jsonPath("$.recipientAddress.city").value("Kandy"));
    }

    @Test void requiresAuthenticationAndCustomerRole() throws Exception {
        mvc.perform(get(BASE + "/parcels")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/parcels").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_courier"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/parcels").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_customer"))))
                .andExpect(status().isUnauthorized());
    }

    @Test void cannotReadOrChangeAnotherCustomersParcel() throws Exception {
        String id = create("customer-a", booking()).get("id").asText();
        mvc.perform(get(BASE + "/parcels/" + id).with(customer("customer-b"))).andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/parcels/" + id + "/feedback").with(customer("customer-b"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/parcels").with(customer("customer-b")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(BASE + "/dashboard").with(customer("customer-b")))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void rejectsInvalidBookingWithoutPersisting() throws Exception {
        List<ObjectNode> invalid = new ArrayList<>();
        invalid.add(booking().put("weight", 0));
        invalid.add(booking().put("recipientPhone", "invalid"));
        invalid.add(booking().put("recipientName", "  "));
        invalid.add(booking().put("size", "huge"));
        invalid.add(booking().put("paymentMethod", "paid"));
        invalid.add(booking().put("declaredValue", -10));
        ObjectNode missingAddress = booking(); missingAddress.remove("pickupAddress"); invalid.add(missingAddress);
        ObjectNode badDistrict = booking(); ((ObjectNode) badDistrict.get("recipientAddress")).put("district", "Unknown"); invalid.add(badDistrict);
        for (ObjectNode request : invalid) {
            mvc.perform(post(BASE + "/parcels").with(customer("customer-a"))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isZero();
    }

    @Test void rejectsClientSuppliedIdentityFeeAndStatus() throws Exception {
        for (String field : List.of("senderId", "totalFee", "status", "paymentStatus")) {
            mvc.perform(post(BASE + "/parcels").with(customer("customer-a"))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(booking().put(field, "forged"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isZero();
    }

    @Test void searchFiltersDatesAndPaginationStayWithinOwner() throws Exception {
        ObjectNode first = booking().put("recipientName", "Alice");
        String alice = create("customer-a", first).get("id").asText();
        create("customer-a", booking().put("recipientName", "Bob"));
        create("customer-b", booking().put("recipientName", "Alice"));
        CustomerParcel parcel = repository.findById(alice).orElseThrow();
        parcel.status = ParcelStatus.DELIVERED;
        parcel.createdAt = Instant.parse("2026-10-20T20:00:00Z"); // 21 October in Colombo.
        repository.save(parcel);
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a"))
                        .param("search", "ALICE").param("status", "delivered")
                        .param("fromDate", "2026-10-21").param("toDate", "2026-10-21"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alice));
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param("sort", "oldest"))
                .andExpect(jsonPath("$.content[0].id").value(alice));
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param("search", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void rejectsInvalidQueryParameters() throws Exception {
        for (String[] parameter : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"}, new String[]{"size", "101"},
                new String[]{"sort", "unsafe"}, new String[]{"status", "unknown"}, new String[]{"fromDate", "bad-date"})) {
            mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param("fromDate", "2026-10-22").param("toDate", "2026-10-21"))
                .andExpect(status().isBadRequest());
    }

    @Test void dashboardAndInProgressFilterReflectStoredStates() throws Exception {
        String id = create("customer-a", booking()).get("id").asText();
        create("customer-a", booking());
        CustomerParcel parcel = repository.findById(id).orElseThrow(); parcel.status = ParcelStatus.IN_TRANSIT; repository.save(parcel);
        mvc.perform(get(BASE + "/dashboard").with(customer("customer-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.inProgress").value(1));
        mvc.perform(get(BASE + "/parcels").with(customer("customer-a")).param("status", "in_progress"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test void feedbackRequiresDeliveryAndValidRating() throws Exception {
        String id = create("customer-a", booking()).get("id").asText();
        String path = BASE + "/parcels/" + id + "/feedback";
        mvc.perform(put(path).with(customer("customer-a")).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isConflict());
        CustomerParcel parcel = repository.findById(id).orElseThrow(); parcel.status = ParcelStatus.DELIVERED; repository.save(parcel);
        mvc.perform(put(path).with(customer("customer-a")).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).with(customer("customer-a")).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\" Thank you \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.feedbackComment").value("Thank you"));
        assertThat(repository.findById(id).orElseThrow().rating).isEqualTo(5);
    }

    @Test void feeEstimatePrecedesBookingAndOnlineBookingIsNotMarkedPaid() throws Exception {
        mvc.perform(post(BASE + "/parcels/estimate").with(customer("customer-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(booking())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalFee").value(450));
        assertThat(repository.count()).isZero();
        assertThat(create("customer-a", booking().put("paymentMethod", "online")).get("paymentStatus").asText()).isEqualTo("pending");
    }

    private String token(String issuer, String audience, Instant expires, boolean includeUser, String signingSecret) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("customer-a")
                .expirationTime(Date.from(expires)).issueTime(new Date()).claim("role", "customer");
        if (includeUser) claims.claim("userId", "customer-a");
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        token.sign(new MACSigner(signingSecret));
        return token.serialize();
    }

    @Test void actualJwtSignatureIssuerAudienceExpiryAndIdentityAreValidated() throws Exception {
        String secret = "customer-booking-test-secret-32-bytes-minimum";
        Instant future = Instant.now().plusSeconds(300);
        mvc.perform(get(BASE + "/parcels").header("Authorization", "Bearer " + token("swiftdrop", "swiftdrop-api", future, true, secret)))
                .andExpect(status().isOk());
        for (String bad : List.of(token("wrong", "swiftdrop-api", future, true, secret),
                token("swiftdrop", "wrong", future, true, secret), token("swiftdrop", "swiftdrop-api", Instant.now().minusSeconds(120), true, secret),
                token("swiftdrop", "swiftdrop-api", future, false, secret), token("swiftdrop", "swiftdrop-api", future, true, "different-secret-32-bytes-minimum-long"))) {
            mvc.perform(get(BASE + "/parcels").header("Authorization", "Bearer " + bad)).andExpect(status().isUnauthorized());
        }
    }
}
