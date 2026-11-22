package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.security.VerifiedMerchantOrderAccess;
import com.swiftdrop.merchant.service.MerchantOrderService;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/v1/merchant/orders")
@ConditionalOnProperty(name={"merchant.persistence.enabled","merchant.orders.enabled"},havingValue="true")
public class MerchantOrderController {
    private final MerchantOrderService service;
    public MerchantOrderController(MerchantOrderService service) { this.service=service; }
    @PostMapping public ResponseEntity<MerchantOrderResponse> create(@AuthenticationPrincipal VerifiedMerchantOrderAccess actor,
        @RequestHeader HttpHeaders headers,@RequestBody MerchantOrderCreateRequest request) {
        var keys=headers.get("Idempotency-Key");
        if (keys==null || keys.size()!=1) throw new MerchantOrderException(400,"INVALID_REQUEST","One Idempotency-Key required");
        var result=service.create(actor,keys.getFirst(),request);
        return ResponseEntity.created(URI.create("/api/v1/merchant/orders/"+result.id())).cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping public ResponseEntity<MerchantOrderPageResponse> list(@AuthenticationPrincipal VerifiedMerchantOrderAccess actor,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String status,
        @RequestParam(required=false) String merchantOrderRef,@RequestParam(required=false) String trackingNumber,
        @RequestParam(required=false) Instant createdFrom,@RequestParam(required=false) Instant createdTo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(actor,
            new MerchantOrderQuery(page,size,status,merchantOrderRef,trackingNumber,createdFrom,createdTo)));
    }
    @GetMapping("/{id}") public ResponseEntity<MerchantOrderResponse> detail(@AuthenticationPrincipal VerifiedMerchantOrderAccess actor,@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(actor,id));
    }
}
