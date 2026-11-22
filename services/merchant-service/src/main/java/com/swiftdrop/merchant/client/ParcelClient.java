package com.swiftdrop.merchant.client;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.dto.request.MerchantOrderQuery;
import java.util.UUID;
public interface ParcelClient {
    ParcelResponse createMerchantParcel(UUID merchantId,UUID downstreamRequestId,ParcelCreateRequest request);
    ParcelPageResponse listMerchantParcels(UUID merchantId,MerchantOrderQuery query);
    ParcelResponse getMerchantParcel(UUID merchantId,UUID parcelId);
}
