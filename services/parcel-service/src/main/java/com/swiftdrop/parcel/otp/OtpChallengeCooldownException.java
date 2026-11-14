package com.swiftdrop.parcel.otp;

public class OtpChallengeCooldownException extends RuntimeException {
    public OtpChallengeCooldownException() {
        super("A new OTP can be requested after the resend cooldown");
    }
}
