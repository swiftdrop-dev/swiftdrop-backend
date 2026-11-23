package com.swiftdrop.parcel.otp;

public class OtpDeliveryUnavailableException extends RuntimeException {
    public OtpDeliveryUnavailableException() {
        super("OTP delivery is not configured");
    }
}
