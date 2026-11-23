package com.swiftdrop.notification.provider;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatedEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailProvider.class);

    @Value("${notification.provider.email.simulate-failure:false}")
    private boolean simulateFailure;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderSendResult send(Notification notification) {
        String refId = "SIM-EML-" + UUID.randomUUID().toString().substring(0, 8);

        if (simulateFailure) {
            log.warn("[SimulatedEmailProvider] Simulated email failure triggered for recipient [{}] on parcel [{}]",
                    notification.getRecipientId(), notification.getTrackingNumber());
            return ProviderSendResult.failure(
                    "SimulatedEmailProvider",
                    refId,
                    "Simulated email delivery failure triggered by configuration"
            );
        }

        String target = notification.getRecipientEmail() != null && !notification.getRecipientEmail().isBlank()
                ? notification.getRecipientEmail()
                : notification.getRecipientId() + "@customer.swiftdrop.io";

        log.info("[SimulatedEmailProvider] Accepted simulated email for recipient [{}] target [{}] subject [{}]: notification [{}] (Simulation Mode: no external SMTP called)",
                notification.getRecipientId(), target, notification.getSubject(), notification.getId());

        return ProviderSendResult.success(
                "SimulatedEmailProvider",
                refId,
                "Email accepted by simulated provider for recipient: " + target + " (Simulated dispatch - external mail server not invoked)"
        );
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
