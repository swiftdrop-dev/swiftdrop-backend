package com.swiftdrop.merchant.service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WebhookSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityValidator.class);

    private final boolean allowLocalhost;
    private final boolean allowInsecureHttp;
    private final Set<Integer> allowedPorts;

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookSecurityValidator(
            @Value("${merchant.webhook.allow-localhost:false}") boolean allowLocalhost,
            @Value("${merchant.webhook.allow-insecure-http:false}") boolean allowInsecureHttp,
            @Value("${merchant.webhook.allowed-ports:80,443,8443}") String allowedPortsConfig) {
        this.allowLocalhost = allowLocalhost;
        this.allowInsecureHttp = allowInsecureHttp;
        if (allowedPortsConfig != null && !allowedPortsConfig.isBlank()) {
            this.allowedPorts = Arrays.stream(allowedPortsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
        } else {
            this.allowedPorts = Set.of(80, 443, 8443);
        }
    }

    public WebhookSecurityValidator(boolean allowLocalhost, boolean allowInsecureHttp) {
        this(allowLocalhost, allowInsecureHttp, "80,443,8443");
    }

    public void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("Webhook URL cannot be blank.");
        }
        if (urlString.length() > 1024) {
            throw new IllegalArgumentException("Webhook URL exceeds maximum length of 1024 characters.");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook URL syntax: " + e.getMessage(), e);
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not contain embedded user credentials.");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Webhook URL must include a protocol scheme (https://).");
        }
        scheme = scheme.toLowerCase();
        if (!allowInsecureHttp && !scheme.equals("https")) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS protocol.");
        }
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("Webhook URL must use HTTP or HTTPS protocol.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must contain a valid host.");
        }

        int port = uri.getPort();
        if (port != -1 && !allowLocalhost) {
            if (!allowedPorts.contains(port)) {
                throw new IllegalArgumentException("Webhook URL specifies a prohibited port: " + port +
                        ". Allowed ports are: " + allowedPorts);
            }
        }

        if (allowLocalhost) {
            log.debug("Localhost/private IP checking bypassed by configuration for host: {}", host);
            return;
        }

        String lowerHost = host.toLowerCase();
        if (isProhibitedHostname(lowerHost)) {
            throw new IllegalArgumentException("Webhook URL targets a prohibited hostname or cloud metadata domain: " + host);
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("Cannot resolve webhook host: " + host);
            }
            for (InetAddress address : addresses) {
                if (isProhibitedAddress(address)) {
                    throw new IllegalArgumentException("Webhook URL resolves to a prohibited private, reserved, or loopback address: " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve webhook host: " + host, e);
        }
    }

    public boolean isProhibitedHostname(String host) {
        String lower = host.toLowerCase().trim();
        return lower.equals("localhost")
                || lower.endsWith(".localhost")
                || lower.endsWith(".internal")
                || lower.endsWith(".local")
                || lower.endsWith(".lan")
                || lower.endsWith(".home.arpa")
                || lower.endsWith(".corp")
                || lower.endsWith(".cluster.local")
                || lower.equals("metadata.google.internal")
                || lower.equals("169.254.169.254")
                || lower.contains("169.254.169.254")
                || lower.contains("127.0.0.1.nip.io")
                || lower.contains("169.254.169.254.nip.io");
    }

    public boolean isProhibitedAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        // Check for IPv4-mapped IPv6 address (::ffff:x.x.x.x)
        if (bytes.length == 16) {
            boolean isMappedIpv4 = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isMappedIpv4 = false;
                    break;
                }
            }
            if (isMappedIpv4 && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] ipv4Bytes = Arrays.copyOfRange(bytes, 12, 16);
                return isProhibitedIpv4(ipv4Bytes);
            }
            return isProhibitedIpv6(bytes);
        } else if (bytes.length == 4) {
            return isProhibitedIpv4(bytes);
        }

        return false;
    }

    private boolean isProhibitedIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;

        // 0.0.0.0/8 (Current network)
        if (b0 == 0) return true;

        // 10.0.0.0/8 (Private-Use RFC 1918)
        if (b0 == 10) return true;

        // 100.64.0.0/10 (Shared Address Space / CGNAT RFC 6598: 100.64.0.0 – 100.127.255.255)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;

        // 127.0.0.0/8 (Loopback RFC 1122)
        if (b0 == 127) return true;

        // 169.254.0.0/16 (Link Local RFC 3927)
        if (b0 == 169 && b1 == 254) return true;

        // 172.16.0.0/12 (Private-Use RFC 1918: 172.16.0.0 – 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

        // 192.0.0.0/24 (IETF Protocol Assignments RFC 6890)
        if (b0 == 192 && b1 == 0 && b2 == 0) return true;

        // 192.0.2.0/24 (TEST-NET-1 Documentation RFC 5737)
        if (b0 == 192 && b1 == 0 && b2 == 2) return true;

        // 192.88.99.0/24 (6to4 Relay Anycast RFC 7526)
        if (b0 == 192 && b1 == 88 && b2 == 99) return true;

        // 192.168.0.0/16 (Private-Use RFC 1918)
        if (b0 == 192 && b1 == 168) return true;

        // 198.18.0.0/15 (Benchmarking RFC 2544: 198.18.0.0 – 198.19.255.255)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;

        // 198.51.100.0/24 (TEST-NET-2 Documentation RFC 5737)
        if (b0 == 198 && b1 == 51 && b2 == 100) return true;

        // 203.0.113.0/24 (TEST-NET-3 Documentation RFC 5737)
        if (b0 == 203 && b1 == 0 && b2 == 113) return true;

        // 224.0.0.0/4 (Multicast RFC 5771: 224.0.0.0 – 239.255.255.255)
        if (b0 >= 224 && b0 <= 239) return true;

        // 240.0.0.0/4 (Reserved RFC 1112: 240.0.0.0 – 255.255.255.254)
        if (b0 >= 240) return true;

        // 255.255.255.255 (Limited Broadcast)
        if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) return true;

        return false;
    }

    private boolean isProhibitedIpv6(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;

        // ::1 (Loopback)
        boolean isAllZeroExceptLast = true;
        for (int i = 0; i < 15; i++) {
            if (bytes[i] != 0) {
                isAllZeroExceptLast = false;
                break;
            }
        }
        if (isAllZeroExceptLast && (bytes[15] & 0xFF) == 1) return true;

        // :: (Unspecified)
        if (isAllZeroExceptLast && (bytes[15] & 0xFF) == 0) return true;

        // fc00::/7 (Unique Local Address RFC 4193: fc00::/7 -> 0xFC or 0xFD)
        if ((b0 & 0xFE) == 0xFC) return true;

        // fe80::/10 (Link-Local Unicast RFC 4291)
        if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;

        // fec0::/10 (Site-Local Unicast RFC 3879 deprecated)
        if (b0 == 0xFE && (b1 & 0xC0) == 0xC0) return true;

        // ff00::/8 (Multicast RFC 4291)
        if (b0 == 0xFF) return true;

        // 2001:db8::/32 (Documentation RFC 3849: 0x2001 0x0db8)
        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8) return true;

        // 100::/64 (Discard-Only Address Block RFC 6666)
        if (b0 == 0x01 && b1 == 0x00) return true;

        // 2001:10::/28 (ORCHIDv2 RFC 7343)
        if (b0 == 0x20 && b1 == 0x01 && (b2 & 0xF0) == 0x10) return true;

        return false;
    }
}
