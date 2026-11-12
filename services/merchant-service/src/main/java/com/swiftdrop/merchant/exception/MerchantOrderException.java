package com.swiftdrop.merchant.exception;
public class MerchantOrderException extends MerchantException {
    public MerchantOrderException(int status,String code,String message) { super(status,code,message); }
}
