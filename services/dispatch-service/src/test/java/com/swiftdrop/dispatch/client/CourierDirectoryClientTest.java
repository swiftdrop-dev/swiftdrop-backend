package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.exception.CourierDirectoryUnavailableException;
import com.swiftdrop.dispatch.exception.InvalidDispatchStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class CourierDirectoryClientTest {

    private static final String COURIER = "http://courier.test/api/v1/couriers/cou_001";

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void scheduledCallUsesTheServiceCredential() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(COURIER))
                .andExpect(header(CourierDirectoryClient.SERVICE_TOKEN_HEADER, "test-dispatch-service-credential"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"id\":\"cou_001\",\"available\":true}", MediaType.APPLICATION_JSON));

        CourierSnapshot snapshot = new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential")
                .requireEligible("cou_001");

        assertThat(snapshot.id()).isEqualTo("cou_001");
        assertThat(snapshot.available()).isTrue();
        server.verify();
    }

    @Test
    void missingServiceCredentialFailsBeforeCallingTheDirectory() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), " ").requireEligible("cou_001"))
                .isInstanceOf(CourierDirectoryUnavailableException.class);
        server.verify();
    }

    @Test
    void invalidServiceCredentialLeavesTheCallerWithAnUnavailableDirectory() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(COURIER))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), "wrong-credential").requireEligible("cou_001"))
                .isInstanceOf(CourierDirectoryUnavailableException.class);
        server.verify();
    }

    @Test
    void dispatcherCallForwardsTheUserTokenAndDoesNotSendTheServiceCredential() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer dispatcher-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(COURIER))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dispatcher-token"))
                .andExpect(headerDoesNotExist(CourierDirectoryClient.SERVICE_TOKEN_HEADER))
                .andRespond(withSuccess("{\"id\":\"cou_001\",\"available\":true}", MediaType.APPLICATION_JSON));

        new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential").requireEligible("cou_001");
        server.verify();
    }

    @Test
    void unavailableCourierIsRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(COURIER))
                .andRespond(withSuccess("{\"id\":\"cou_001\",\"available\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential").requireEligible("cou_001"))
                .isInstanceOf(InvalidDispatchStateException.class);
        server.verify();
    }

    @Test
    void mismatchedCourierIdIsRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(COURIER))
                .andRespond(withSuccess("{\"id\":\"cou_other\",\"available\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential").requireEligible("cou_001"))
                .isInstanceOf(InvalidDispatchStateException.class);
        server.verify();
    }

    @Test
    void missingCourierIsRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(COURIER)).andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential").requireEligible("cou_001"))
                .isInstanceOf(InvalidDispatchStateException.class);
        server.verify();
    }

    @Test
    void serviceCredentialCannotReadTheCourierProfile() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courier.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThatThrownBy(() -> new CourierDirectoryClient(builder.build(), "test-dispatch-service-credential").currentProfileId())
                .isInstanceOf(CourierDirectoryUnavailableException.class);
        server.verify();
    }
}
