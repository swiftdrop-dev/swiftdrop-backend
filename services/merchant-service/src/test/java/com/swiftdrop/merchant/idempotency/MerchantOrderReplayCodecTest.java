package com.swiftdrop.merchant.idempotency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.OrderTestData;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MerchantOrderReplayCodecTest {
    @Test void roundTripAndUnknownVersionRejection() {
        var codec=new MerchantOrderReplayCodec(new ObjectMapper().findAndRegisterModules()); var order=OrderTestData.response(UUID.randomUUID(),UUID.randomUUID()).order();
        assertEquals(order,codec.decode("v1",codec.encode(order)));
        assertThrows(com.swiftdrop.merchant.exception.MerchantException.class,()->codec.decode("v2","{}"));
        assertThrows(com.swiftdrop.merchant.exception.MerchantException.class,()->codec.decode("v1","not json"));
    }
}
