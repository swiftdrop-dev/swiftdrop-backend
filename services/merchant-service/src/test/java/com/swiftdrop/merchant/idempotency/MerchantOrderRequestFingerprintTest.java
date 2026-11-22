package com.swiftdrop.merchant.idempotency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.OrderTestData;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MerchantOrderRequestFingerprintTest {
    @Test void normalizesNumericScaleAndDetectsChanges() {
        var f=new MerchantOrderRequestFingerprint(new ObjectMapper()); var a=OrderTestData.request();
        var b=new MerchantOrderCreateRequest(a.merchantOrderRef(),a.recipient(),a.pickupAddress(),a.deliveryAddress(),a.description(),new BigDecimal("1"),a.size(),a.declaredValue(),a.fragile(),a.specialInstructions(),a.paymentMethod());
        assertEquals(f.fingerprint(a),f.fingerprint(b)); assertNotEquals(f.keyDigest("a"),f.keyDigest("b"));
        var c=new MerchantOrderCreateRequest("other",a.recipient(),a.pickupAddress(),a.deliveryAddress(),a.description(),a.weightKg(),a.size(),a.declaredValue(),a.fragile(),a.specialInstructions(),a.paymentMethod());
        assertNotEquals(f.fingerprint(a),f.fingerprint(c));
    }
}
