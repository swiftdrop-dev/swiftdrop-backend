package com.swiftdrop.merchant.config;
import java.net.URI;
import java.time.Duration;
public record ParcelClientProperties(URI baseUrl,Duration connectTimeout,Duration readTimeout) {
    public ParcelClientProperties {
        if (baseUrl==null || !"https".equals(baseUrl.getScheme()) || baseUrl.getHost()==null || baseUrl.getUserInfo()!=null || baseUrl.getQuery()!=null || baseUrl.getFragment()!=null)
            throw new IllegalArgumentException("Approved HTTPS parcel base URL required");
        if (connectTimeout==null || readTimeout==null || connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
            || connectTimeout.compareTo(Duration.ofSeconds(30))>0 || readTimeout.compareTo(Duration.ofSeconds(30))>0)
            throw new IllegalArgumentException("Bounded parcel timeouts required");
    }
}
