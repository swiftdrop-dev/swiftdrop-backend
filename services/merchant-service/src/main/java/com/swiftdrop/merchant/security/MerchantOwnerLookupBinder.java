package com.swiftdrop.merchant.security;

import com.swiftdrop.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Component;

@Component
public class MerchantOwnerLookupBinder {

    public MerchantOwnerLookupBinder(MerchantRepository merchantRepository) {
        SecurityUtils.bindMerchantOwnerLookup(merchantId ->
                merchantRepository.findById(merchantId).map(merchant -> merchant.getUserId()));
    }
}
