package com.swiftdrop.notification.provider;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.DeliveryStatus;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<NotificationChannel, NotificationProvider> providers;

    @Value("${notification.provider.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.provider.sms.enabled:true}")
    private boolean smsEnabled;

    public NotificationDispatcher(List<NotificationProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(NotificationProvider::getChannel, Function.identity(), (a, b) -> a));
    }

    public ProviderSendResult dispatch(Notification notification) {
        NotificationChannel channel = notification.getChannel();
        if (channel == NotificationChannel.EMAIL && !emailEnabled) {
            log.info("Email channel disabled by configuration; marking notification [{}] as skipped", notification.getId());
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setFailureReason("Email channel disabled by configuration");
            return ProviderSendResult.failure("Dispatcher", "NONE", "Email channel disabled");
        }

        if (channel == NotificationChannel.SMS && !smsEnabled) {
            log.info("SMS channel disabled by configuration; marking notification [{}] as skipped", notification.getId());
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setFailureReason("SMS channel disabled by configuration");
            return ProviderSendResult.failure("Dispatcher", "NONE", "SMS channel disabled");
        }

        NotificationProvider provider = providers.get(channel);
        if (provider == null) {
            log.warn("No notification provider registered for channel [{}] on notification [{}]", channel, notification.getId());
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setFailureReason("No provider available for channel " + channel);
            return ProviderSendResult.failure("Dispatcher", "NONE", "No provider available for channel " + channel);
        }

        try {
            ProviderSendResult result = provider.send(notification);
            if (result.accepted()) {
                notification.setDeliveryStatus(DeliveryStatus.SENT);
                notification.setSentAt(Instant.now());
                notification.setFailureReason(null);
            } else {
                notification.setDeliveryStatus(DeliveryStatus.FAILED);
                notification.setFailureReason(result.message());
            }
            return result;
        } catch (Exception ex) {
            log.error("Exception occurred while dispatching notification [{}] via [{}]: {}",
                    notification.getId(), channel, ex.getMessage(), ex);
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notification.setFailureReason("Provider exception: " + ex.getMessage());
            return ProviderSendResult.failure(provider.getClass().getSimpleName(), "ERR", ex.getMessage());
        }
    }
}
