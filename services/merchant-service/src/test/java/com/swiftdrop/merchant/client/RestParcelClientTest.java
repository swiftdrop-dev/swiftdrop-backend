package com.swiftdrop.merchant.client;
import com.swiftdrop.merchant.*;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.config.ParcelClientProperties;
import com.swiftdrop.merchant.dto.request.MerchantOrderQuery;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class RestParcelClientTest {
    static ValidatorFactory factory; MockRestServiceServer server; RestParcelClient client;
    final UUID merchant=UUID.randomUUID();
    final String parcel=OrderTestData.PARCEL_ID;
    @BeforeAll static void validation() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("http://localhost"); server=MockRestServiceServer.bindTo(builder).build();
        client=new RestParcelClient(builder.build(),h->{},new MerchantOrderMapper(factory.getValidator()));
    }
    @Test void createSendsStableRequestIdAndValidatesResponse() throws Exception {
        UUID request=UUID.randomUUID(); String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(OrderTestData.response(merchant,parcel));
        server.expect(requestTo("http://localhost/internal/v1/merchants/"+merchant+"/parcels")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key",request.toString())).andExpect(headerDoesNotExist("X-API-Key")).andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
        assertEquals(parcel,client.createMerchantParcel(merchant,request,new ParcelCreateRequest(OrderTestData.request())).order().id()); server.verify();
    }
    @Test void upstreamBodiesAndCredentialsAreNotExposed() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private downstream secret"));
        var error=assertThrows(ParcelClientException.class,()->client.getMerchantParcel(merchant,parcel));
        assertEquals(502,error.status()); assertFalse(error.toString().contains("private")); assertNull(error.getCause()); server.verify();
    }
    @Test void rejectsMalformedAndWrongOwnerResponses() throws Exception {
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(OrderTestData.response(UUID.randomUUID(),parcel));
        server.expect(anything()).andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
        assertEquals(502,assertThrows(ParcelClientException.class,()->client.getMerchantParcel(merchant,parcel)).status());
    }
    @Test void detailUsesCanonicalStringPathAndRequiresExactId() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        String url="http://localhost/internal/v1/merchants/"+merchant+"/parcels/"+parcel;
        server.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(mapper.writeValueAsString(OrderTestData.response(merchant,parcel)),MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(mapper.writeValueAsString(OrderTestData.response(merchant,"prc_1234567890ABCDEF")),MediaType.APPLICATION_JSON));
        assertEquals(parcel,client.getMerchantParcel(merchant,parcel).order().id());
        assertEquals(502,assertThrows(ParcelClientException.class,()->client.getMerchantParcel(merchant,parcel)).status());
        server.verify();
    }
    @Test void rejectsDownstreamIdsThatCannotBeUsedInAnOrderLocation() throws Exception {
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(OrderTestData.response(merchant,"../other"));
        server.expect(anything()).andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
        assertEquals(502,assertThrows(ParcelClientException.class,()->client.createMerchantParcel(
            merchant,UUID.randomUUID(),new ParcelCreateRequest(OrderTestData.request()))).status());
        server.verify();
    }
    @Test void malformedJsonIsSanitized() { server.expect(anything()).andRespond(withSuccess("not json",MediaType.APPLICATION_JSON)); assertEquals(502,assertThrows(ParcelClientException.class,()->client.getMerchantParcel(merchant,parcel)).status()); }
    @Test void rejectionIsDefinitiveOnlyForAgreedCreationStatuses() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        assertTrue(assertThrows(ParcelClientException.class,()->client.createMerchantParcel(merchant,UUID.randomUUID(),new ParcelCreateRequest(OrderTestData.request()))).definitive()); server.verify();
    }
    @Test void missingServiceAuthenticationFailsBeforeNetwork() {
        var blocked=new RestParcelClient(RestClient.builder().baseUrl("http://localhost").build(),new UnavailableParcelServiceAuthentication(),new MerchantOrderMapper(factory.getValidator()));
        assertEquals(503,assertThrows(ParcelClientException.class,()->blocked.getMerchantParcel(merchant,parcel)).status());
    }
    @Test void listForwardsEncodedFilters() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("status=PENDING"))).andExpect(queryParam("merchantOrderRef","REF%201"))
            .andRespond(withSuccess("{\"data\":[],\"total\":0,\"page\":0,\"size\":20}",MediaType.APPLICATION_JSON));
        assertEquals(0,client.listMerchantParcels(merchant,new MerchantOrderQuery(0,20,"PENDING","REF 1",null,null,null)).total()); server.verify();
    }
    @Test void productionPropertiesRejectUnsafeUrlsAndUnboundedTimeouts() {
        assertThrows(IllegalArgumentException.class,()->new ParcelClientProperties(URI.create("http://localhost"),Duration.ofSeconds(1),Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,()->new ParcelClientProperties(URI.create("https://parcel.example"),Duration.ofSeconds(1),Duration.ofSeconds(31)));
    }
    @Test void realLoopbackServerHonorsReadTimeoutWithoutRetry() throws Exception {
        var local=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        var count=new java.util.concurrent.atomic.AtomicInteger();
        local.createContext("/",exchange->{count.incrementAndGet(); try { Thread.sleep(300); exchange.sendResponseHeaders(200,-1); } catch(Exception ignored) {} finally { exchange.close(); }});
        local.start();
        try {
            var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()); transport.setReadTimeout(Duration.ofMillis(50));
            var adapter=new RestParcelClient(RestClient.builder().baseUrl("http://localhost:"+local.getAddress().getPort()).requestFactory(transport).build(),h->{},new MerchantOrderMapper(factory.getValidator()));
            assertEquals(504,assertThrows(ParcelClientException.class,()->adapter.getMerchantParcel(merchant,parcel)).status()); assertTrue(count.get()<=1);
        } finally { local.stop(0); }
    }
}
