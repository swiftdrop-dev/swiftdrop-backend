package com.swiftdrop.parcel.customer;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PricingIntegrationTest {
    @Test void configuredHttpAdapterSendsBookingAndReadsQuoteOverHttp() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var method = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/api/v1/pricing/estimate", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            method.set(exchange.getRequestMethod());
            byte[] response = "{\"totalFee\":325,\"estimatedDays\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            RemotePricing pricing = new RemotePricing("http://127.0.0.1:" + server.getAddress().getPort());
            PricingPort.Quote quote = pricing.quote(request());
            assertThat(quote.totalFee()).isEqualByComparingTo("325");
            assertThat(quote.estimatedDays()).isEqualTo(1);
            assertThat(method.get()).isEqualTo("POST");
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(received.get());
            assertThat(payload.get("weight").decimalValue()).isEqualByComparingTo("1.2");
            assertThat(payload.at("/recipientAddress/district").asText()).isEqualTo("Colombo");
        } finally { server.stop(0); }
    }

    private BookingContracts.CreateBooking request() {
        var address = new BookingContracts.Address("12 Road", "Colombo", "Colombo", null);
        return new BookingContracts.CreateBooking("Recipient", "0771234567", address, address, "Books", new BigDecimal("1.2"), "sm", null, false, null, "cod");
    }

    @Test void usesM3QuoteAndFailsClosedOnUnavailableOrInvalidResponses() {
        for (String body : new String[]{"{\"totalFee\":450,\"estimatedDays\":2}",
                "{\"totalFee\":-1,\"estimatedDays\":2}", "{\"totalFee\":10,\"estimatedDays\":0}",
                "{\"totalFee\":10000000000,\"estimatedDays\":2}", "{}", "not-json"}) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://pricing.test");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://pricing.test/api/v1/pricing/estimate")).andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            RemotePricing pricing = new RemotePricing(builder.build());
            if (body.contains("450")) assertThat(pricing.quote(request()).totalFee()).isEqualByComparingTo("450");
            else assertThatThrownBy(() -> pricing.quote(request())).isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            server.verify();
        }
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pricing.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://pricing.test/api/v1/pricing/estimate")).andRespond(withServerError());
        RemotePricing pricing = new RemotePricing(builder.build());
        assertThatThrownBy(() -> pricing.quote(request())).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test void pricingFailureNeverWritesBooking() {
        CustomerParcelRepository repository = mock(CustomerParcelRepository.class);
        PricingPort pricing = mock(PricingPort.class);
        when(pricing.quote(any())).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        CustomerBookingService service = new CustomerBookingService(repository, pricing);
        assertThatThrownBy(() -> service.create(new CustomerBookingService.Customer("customer-a", "Test", ""), request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
