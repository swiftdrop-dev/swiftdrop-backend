package com.swiftdrop.notification.provider;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.NotificationChannel;

public interface NotificationProvider {

    NotificationChannel getChannel();

    ProviderSendResult send(Notification notification);
}
