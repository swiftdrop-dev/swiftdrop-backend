package com.swiftdrop.parcel.otp;

/** Internal boundary for a future authenticated notification integration. No provider is configured yet. */
public interface OtpDeliveryGateway {
    void sendOtp(OtpDeliveryCommand command);
}
