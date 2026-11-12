package com.swiftdrop.merchant.client;
import org.springframework.http.HttpHeaders;
public final class UnavailableParcelServiceAuthentication implements ParcelServiceAuthentication {
    public void apply(HttpHeaders headers) { throw new ParcelClientException(503,false); }
}
