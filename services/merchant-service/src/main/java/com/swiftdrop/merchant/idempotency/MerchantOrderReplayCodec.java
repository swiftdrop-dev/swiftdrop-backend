package com.swiftdrop.merchant.idempotency;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import com.swiftdrop.merchant.exception.MerchantOrderException;
public final class MerchantOrderReplayCodec {
    private final ObjectMapper mapper;
    public MerchantOrderReplayCodec(ObjectMapper mapper) { this.mapper=mapper; }
    public String encode(MerchantOrderResponse response) {
        try { return mapper.writeValueAsString(response); } catch (Exception e) { throw invalid(); }
    }
    public MerchantOrderResponse decode(String version,String snapshot) {
        try { if (!"v1".equals(version)) throw invalid(); return mapper.readValue(snapshot,MerchantOrderResponse.class); }
        catch (Exception e) { throw invalid(); }
    }
    private MerchantOrderException invalid() { return new MerchantOrderException(503,"REPLAY_UNAVAILABLE","Stored response unavailable"); }
}
