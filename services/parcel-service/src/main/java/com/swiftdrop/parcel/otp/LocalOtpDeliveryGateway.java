package com.swiftdrop.parcel.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "parcel.otp.local-delivery.enabled", havingValue = "true")
public class LocalOtpDeliveryGateway implements OtpDeliveryGateway {
    private static final Logger log = LoggerFactory.getLogger(LocalOtpDeliveryGateway.class);

    @Override
    public void sendOtp(OtpDeliveryCommand command) {
        log.info("Dispatched local delivery OTP for parcel {}, challenge {}", command.parcelId(), command.challengeId());
    }
}
