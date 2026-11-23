package com.swiftdrop.parcel.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "parcel.otp.local-delivery.enabled", havingValue = "true")
public class LocalOtpDeliveryGateway implements OtpDeliveryGateway {
    private static final Logger log = LoggerFactory.getLogger(LocalOtpDeliveryGateway.class);
    private static final Map<UUID, String> RECENT_CODES = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(OtpDeliveryCommand command) {
        log.info("Dispatched local delivery OTP for parcel {}, challenge {}", command.parcelId(), command.challengeId());
        if (command.otpCode() != null) {
            RECENT_CODES.put(command.challengeId(), new String(command.otpCode()));
        }
    }

    public static String getRecentCode(UUID challengeId) {
        return RECENT_CODES.get(challengeId);
    }
}
