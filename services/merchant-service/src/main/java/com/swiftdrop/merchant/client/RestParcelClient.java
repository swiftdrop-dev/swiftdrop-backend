package com.swiftdrop.merchant.client;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.config.ParcelClientProperties;
import com.swiftdrop.merchant.dto.request.MerchantOrderQuery;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import org.springframework.web.client.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.function.Supplier;

public final class RestParcelClient implements ParcelClient {
    private final RestClient client;
    private final ParcelServiceAuthentication authentication;
    private final MerchantOrderMapper mapper;
    public RestParcelClient(ParcelClientProperties properties,ParcelServiceAuthentication authentication,MerchantOrderMapper mapper) {
        this(build(properties),authentication,mapper);
    }
    /** Injectable transport for isolated mock-server tests; not a production authentication bypass. */
    public RestParcelClient(RestClient client,ParcelServiceAuthentication authentication,MerchantOrderMapper mapper) {
        this.client=client; this.authentication=authentication; this.mapper=mapper;
    }
    private static RestClient build(ParcelClientProperties p) {
        var http=HttpClient.newBuilder().connectTimeout(p.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(http); factory.setReadTimeout(p.readTimeout());
        return RestClient.builder().baseUrl(p.baseUrl().toString()).requestFactory(factory).build();
    }
    public ParcelResponse createMerchantParcel(UUID merchant,UUID requestId,ParcelCreateRequest request) {
        return call(()->{
            var response=client.post().uri("/internal/v1/merchants/{merchant}/parcels",merchant)
                .headers(authentication::apply).header("Idempotency-Key",requestId.toString()).body(request).retrieve().body(ParcelResponse.class);
            mapper.order(merchant,response); return response;
        },true);
    }
    public ParcelPageResponse listMerchantParcels(UUID merchant,MerchantOrderQuery query) {
        return call(()->{
            var response=client.get().uri(b->b.path("/internal/v1/merchants/{merchant}/parcels")
                .queryParam("page",query.page()).queryParam("size",query.size()).queryParam("sort","createdAt,desc;id,desc")
                .queryParamIfPresent("status",java.util.Optional.ofNullable(query.status()))
                .queryParamIfPresent("merchantOrderRef",java.util.Optional.ofNullable(query.merchantOrderRef()))
                .queryParamIfPresent("trackingNumber",java.util.Optional.ofNullable(query.trackingNumber()))
                .queryParamIfPresent("createdFrom",java.util.Optional.ofNullable(query.createdFrom()))
                .queryParamIfPresent("createdTo",java.util.Optional.ofNullable(query.createdTo())).build(merchant))
                .headers(authentication::apply).retrieve().body(ParcelPageResponse.class);
            mapper.page(merchant,response,query); return response;
        },false);
    }
    public ParcelResponse getMerchantParcel(UUID merchant,UUID parcel) {
        return call(()->{
            var response=client.get().uri("/internal/v1/merchants/{merchant}/parcels/{parcel}",merchant,parcel)
                .headers(authentication::apply).retrieve().body(ParcelResponse.class);
            if (!parcel.equals(mapper.order(merchant,response).id())) throw new ParcelClientException(502,false);
            return response;
        },false);
    }
    private <T> T call(Supplier<T> action,boolean create) {
        try { return action.get(); }
        catch (ParcelClientException e) { throw e; }
        catch (java.util.concurrent.CancellationException e) { throw new ParcelClientException(504,false); }
        catch (RestClientResponseException e) {
            int status=e.getStatusCode().value();
            boolean definitive=create && (status==400 || status==409 || status==422);
            throw new ParcelClientException(definitive ? status : !create && status==404 ? 404 : 502,definitive);
        }
        catch (ResourceAccessException e) {
            boolean timeout=false;
            for (Throwable t=e; t!=null; t=t.getCause()) if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) timeout=true;
            throw new ParcelClientException(timeout ? 504 : 503,false);
        }
        catch (RestClientException | IllegalArgumentException e) { throw new ParcelClientException(502,false); }
    }
}
