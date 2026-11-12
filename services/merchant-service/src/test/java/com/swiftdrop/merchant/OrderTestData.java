package com.swiftdrop.merchant;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.client.dto.ParcelResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class OrderTestData {
    private OrderTestData() {}
    public static MerchantOrderCreateRequest request() {
        return new MerchantOrderCreateRequest("REF-1",new MerchantOrderRecipientRequest("Recipient","+94112345678"),
            new MerchantOrderAddressRequest("Street","City","District",null),new MerchantOrderAddressRequest("Other street","City","District",null),
            "Goods",new BigDecimal("1.000"),MerchantOrderCreateRequest.Size.SM,null,false,null,MerchantOrderCreateRequest.PaymentMethod.COD);
    }
    public static ParcelResponse response(UUID merchant,UUID parcel) {
        return new ParcelResponse(merchant,new MerchantOrderResponse(parcel,"REF-1","TRACK-1","PENDING",request(),new BigDecimal("350.00"),new BigDecimal("350.00"),"LKR",Instant.parse("2026-11-13T00:00:00Z")));
    }
}
