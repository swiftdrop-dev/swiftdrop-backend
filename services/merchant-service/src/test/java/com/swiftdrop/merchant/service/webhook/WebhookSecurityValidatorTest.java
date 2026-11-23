package com.swiftdrop.merchant.service.webhook;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSecurityValidatorTest {

    private final WebhookSecurityValidator strictValidator = new WebhookSecurityValidator(false, false, "80,443,8443");
    private final WebhookSecurityValidator devValidator = new WebhookSecurityValidator(true, true, "80,443,8443,8080");

    @Test
    void shouldRejectBlankOrNullUrl() {
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> strictValidator.validateUrl(null));
        assertTrue(ex1.getMessage().contains("blank"));

        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> strictValidator.validateUrl("   "));
        assertTrue(ex2.getMessage().contains("blank"));
    }

    @Test
    void shouldRejectExcessiveUrlLength() {
        String longUrl = "https://example.com/" + "a".repeat(1025);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> strictValidator.validateUrl(longUrl));
        assertTrue(ex.getMessage().contains("maximum length"));
    }

    @Test
    void shouldRejectMalformedUrl() {
        assertThrows(IllegalArgumentException.class, () -> strictValidator.validateUrl("not-a-valid-url:::"));
    }

    @Test
    void shouldRejectUserCredentialsInUrl() {
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://admin:secret@example.com/webhook"));
        assertTrue(ex.getMessage().contains("user credentials"));
    }

    @Test
    void shouldRejectNonHttpsInStrictMode() {
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("http://example.com/webhook"));
        assertTrue(ex.getMessage().contains("HTTPS"));
    }

    @Test
    void shouldRejectProhibitedPortsInStrictMode() {
        // Ports like 22 (SSH), 3306 (MySQL), 5432 (Postgres), 8080 (Internal HTTP)
        Exception ex1 = assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://example.com:22/webhook"));
        assertTrue(ex1.getMessage().contains("prohibited port"));

        Exception ex2 = assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://example.com:5432/webhook"));
        assertTrue(ex2.getMessage().contains("prohibited port"));

        Exception ex3 = assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://example.com:8080/webhook"));
        assertTrue(ex3.getMessage().contains("prohibited port"));
    }

    @Test
    void shouldAllowAllowedPorts() {
        assertDoesNotThrow(() -> strictValidator.validateUrl("https://1.1.1.1:443/webhook"));
        assertDoesNotThrow(() -> strictValidator.validateUrl("https://1.1.1.1:8443/webhook"));
    }

    @Test
    void shouldRejectLocalhostAndInternalDomains() {
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://localhost/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://service.internal/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://my-host.local/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://app.localhost/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://router.lan/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://device.home.arpa/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://service.corp/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://service.cluster.local/webhook"));
    }

    @Test
    void shouldRejectCloudMetadataDomains() {
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://metadata.google.internal/computeMetadata/v1/"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://169.254.169.254.nip.io/latest/meta-data"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://127.0.0.1.nip.io/webhook"));
    }

    @Test
    void shouldRejectPrivateAndLoopbackIps() {
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://127.0.0.1/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://10.0.0.1/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://192.168.1.100/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://172.20.0.5/webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> strictValidator.validateUrl("https://169.254.169.254/latest/meta-data"));
    }

    @Test
    void shouldValidateProhibitedIpv4RangesDirectly() throws UnknownHostException {
        // CGNAT (100.64.0.0/10)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("100.127.255.254")));

        // Documentation test nets (192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("192.0.2.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("198.51.100.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("203.0.113.1")));

        // Benchmark (198.18.0.0/15)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("198.18.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("198.19.255.1")));

        // Multicast & Reserved (224.0.0.0/4, 240.0.0.0/4)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("224.0.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("240.0.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("255.255.255.255")));

        // Valid public IP should NOT be prohibited
        assertFalse(strictValidator.isProhibitedAddress(InetAddress.getByName("1.1.1.1")));
        assertFalse(strictValidator.isProhibitedAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void shouldValidateProhibitedIpv6RangesDirectly() throws UnknownHostException {
        // IPv6 Loopback (::1)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("::1")));

        // IPv6 Unique Local (fc00::/7)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("fc00::1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("fd12:3456:789a::1")));

        // IPv6 Link-Local (fe80::/10)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("fe80::1")));

        // IPv6 Documentation (2001:db8::/32)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("2001:db8::1")));

        // IPv6 Multicast (ff02::1)
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("ff02::1")));

        // IPv4-mapped IPv6 address pointing to loopback or private IP
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("::ffff:127.0.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("::ffff:10.0.0.1")));
        assertTrue(strictValidator.isProhibitedAddress(InetAddress.getByName("::ffff:169.254.169.254")));

        // Valid public IPv6
        assertFalse(strictValidator.isProhibitedAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void shouldAllowLocalhostAndHttpInDevMode() {
        assertDoesNotThrow(() -> devValidator.validateUrl("http://localhost:8080/webhook"));
        assertDoesNotThrow(() -> devValidator.validateUrl("http://127.0.0.1:3000/webhook"));
    }
}
