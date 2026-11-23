package com.swiftdrop.notification.provider;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatedSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsProvider.class);

    @Value("${notification.provider.sms.simulate-failure:false}")
    private boolean simulateFailure;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public ProviderSendResult send(Notification notification) {
        String refId = "SIM-SMS-" + UUID.randomUUID().toString().substring(0, 8);

        if (simulateFailure) {
            log.warn("[SimulatedSmsProvider] Simulated SMS failure triggered for recipient [{}] on parcel [{}]",
                    notification.getRecipientId(), notification.getTrackingNumber());
            return ProviderSendResult.failure(
                    "SimulatedSmsProvider",
                    refId,
                    "Simulated SMS delivery failure triggered by configuration"
            );
        }

        String phone = notification.getRecipientPhone() != null && !notification.getRecipientPhone().isBlank()
                ? notification.getRecipientPhone()
                : "+94770000000";

        log.info("[SimulatedSmsProvider] Accepted simulated SMS for recipient [{}] phone [{}]: notification [{}] (Simulation Mode: no external SMS gateway called)",
                notification.getRecipientId(), phone, notification.getId());

        return ProviderSendResult.success(
                "SimulatedSmsProvider",
                refId,
                "SMS accepted by simulated provider for recipient: " + phone + " (Simulated dispatch - external SMS provider not invoked)"
        );
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
