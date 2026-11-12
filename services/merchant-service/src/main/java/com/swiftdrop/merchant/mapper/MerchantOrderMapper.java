package com.swiftdrop.merchant.mapper;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.client.ParcelClientException;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.dto.request.MerchantOrderQuery;
import jakarta.validation.Validator;
import java.util.*;
public final class MerchantOrderMapper {
    private static final Set<String> STATUSES=Set.of("PENDING","ASSIGNED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED","CANCELLED","RETURNED");
    private final Validator validator;
    public MerchantOrderMapper(Validator validator) { this.validator=validator; }
    public MerchantOrderResponse order(UUID merchant,ParcelResponse response) {
        if (response==null || !merchant.equals(response.merchantId()) || response.order()==null) throw invalid();
        var order=response.order();
        if (order.id()==null || order.merchantOrderRef()==null || order.merchantOrderRef().isBlank() || order.trackingNumber()==null || order.trackingNumber().isBlank()
            || !STATUSES.contains(order.status()==null ? "" : order.status()) || order.createdAt()==null || order.details()==null
            || !validator.validate(order.details()).isEmpty() || !order.merchantOrderRef().equals(order.details().merchantOrderRef())
            || order.deliveryFee()==null || order.deliveryFee().signum()<0 || order.totalFee()==null || order.totalFee().signum()<0
            || order.currency()==null || !order.currency().matches("[A-Z]{3}")) throw invalid();
        return order;
    }
    public MerchantOrderPageResponse page(UUID merchant,ParcelPageResponse response,MerchantOrderQuery query) {
        if (response==null || response.data()==null || response.page()!=query.page() || response.size()!=query.size()
            || response.total()<0 || response.data().size()>query.size() || response.total()<response.data().size()) throw invalid();
        var orders=response.data().stream().map(p->order(merchant,p)).toList();
        if (orders.stream().map(MerchantOrderResponse::id).distinct().count()!=orders.size()) throw invalid();
        return new MerchantOrderPageResponse(orders,response.total(),response.page(),response.size());
    }
    private ParcelClientException invalid() { return new ParcelClientException(502,false); }
}
