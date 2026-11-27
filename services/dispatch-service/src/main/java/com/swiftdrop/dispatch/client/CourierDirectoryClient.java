package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.exception.CourierDirectoryUnavailableException;
import com.swiftdrop.dispatch.exception.InvalidDispatchStateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Component
public class CourierDirectoryClient {

    public static final String SERVICE_TOKEN_HEADER = "X-Dispatch-Service-Token";

    private final RestClient restClient;
    private final String serviceToken;

    @Autowired
    public CourierDirectoryClient(
            @Value("${services.courier-service.url:http://localhost:8086}") String courierServiceUrl,
            @Value("${dispatch.service-auth.token:}") String serviceToken) {
        this(buildClient(courierServiceUrl), serviceToken);
    }

    CourierDirectoryClient(RestClient restClient, String serviceToken) {
        this.restClient = restClient;
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    private static RestClient buildClient(String courierServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return RestClient.builder()
                .baseUrl(courierServiceUrl)
                .requestFactory(factory)
                .build();
    }

    public CourierSnapshot requireEligible(String courierId) {
        String requestedId = requireCourierId(courierId);
        CourierSnapshot snapshot = fetch("/api/v1/couriers/{id}", true, requestedId);
        if (snapshot == null || snapshot.id() == null || !snapshot.id().equals(requestedId)
                || !Boolean.TRUE.equals(snapshot.available())) {
            throw new InvalidDispatchStateException("Courier is not eligible for assignment");
        }
        return snapshot;
    }

    public String currentProfileId() {
        CourierSnapshot snapshot = fetch("/api/v1/couriers/me", false);
        if (snapshot == null || snapshot.id() == null || snapshot.id().isBlank()) {
            throw new CourierDirectoryUnavailableException("Courier profile is unavailable");
        }
        return snapshot.id();
    }

    private CourierSnapshot fetch(String path, boolean allowServiceCredential, Object... uriVariables) {
        String userAuthorization = currentUserAuthorization();
        boolean useServiceCredential = userAuthorization == null;
        if (useServiceCredential && (!allowServiceCredential || serviceToken.isBlank())) {
            throw new CourierDirectoryUnavailableException("Courier directory is unavailable");
        }
        try {
            var request = restClient.get().uri(path, uriVariables);
            if (useServiceCredential) {
                request = request.header(SERVICE_TOKEN_HEADER, serviceToken);
            } else {
                request = request.header(HttpHeaders.AUTHORIZATION, userAuthorization);
            }
            return request.retrieve().body(CourierSnapshot.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        } catch (RestClientException exception) {
            throw new CourierDirectoryUnavailableException("Courier directory is unavailable");
        }
    }

    private String currentUserAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization;
    }

    private String requireCourierId(String courierId) {
        if (courierId == null) {
            throw new InvalidDispatchStateException("Courier is not eligible for assignment");
        }
        String trimmed = courierId.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64 || !trimmed.equals(courierId)) {
            throw new InvalidDispatchStateException("Courier is not eligible for assignment");
        }
        return trimmed;
    }
}
