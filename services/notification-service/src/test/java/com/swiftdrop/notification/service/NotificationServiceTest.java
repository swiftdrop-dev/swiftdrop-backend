package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationPageResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.DeliveryStatus;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import com.swiftdrop.notification.entity.enums.NotificationType;
import com.swiftdrop.notification.exception.AccessDeniedException;
import com.swiftdrop.notification.exception.ResourceNotFoundException;
import com.swiftdrop.notification.exception.UnauthorizedException;
import com.swiftdrop.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new Notification(
                "evt-1", "parcel-1", "SWD-9999",
                "cust-user-1", "customer@example.com", "+123456789",
                NotificationChannel.EMAIL, NotificationType.BOOKING_CONFIRMED,
                "Booking Confirmed", "Your booking SWD-9999 is confirmed", DeliveryStatus.SENT
        );
    }

    @Test
    void getCustomerNotifications_requiresAuthentication() {
        assertThatThrownBy(() -> service.getCustomerNotifications(null, null, 0, 20, false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication required");

        assertThatThrownBy(() -> service.getCustomerNotifications("   ", null, 0, 20, false))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getCustomerNotifications_returnsPagedHistoryAndUnreadCount() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByRecipientIdOrderByCreatedAtDesc("cust-user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(testNotification), pageable, 1));
        when(repository.countByRecipientIdAndIsRead("cust-user-1", false))
                .thenReturn(1L);

        NotificationPageResponse<NotificationResponse> result =
                service.getCustomerNotifications("cust-user-1", "ROLE_CUSTOMER", 0, 20, false);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.unreadCount()).isEqualTo(1);
        assertThat(result.content().getFirst().trackingNumber()).isEqualTo("SWD-9999");
    }

    @Test
    void getCustomerNotifications_unreadOnlyFilter() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByRecipientIdAndIsReadOrderByCreatedAtDesc("cust-user-1", false, pageable))
                .thenReturn(new PageImpl<>(List.of(testNotification), pageable, 1));
        when(repository.countByRecipientIdAndIsRead("cust-user-1", false))
                .thenReturn(1L);

        NotificationPageResponse<NotificationResponse> result =
                service.getCustomerNotifications("cust-user-1", "ROLE_CUSTOMER", 0, 20, true);

        assertThat(result.content()).hasSize(1);
        verify(repository).findByRecipientIdAndIsReadOrderByCreatedAtDesc("cust-user-1", false, pageable);
    }

    @Test
    void getNotificationById_requiresAuthentication() {
        assertThatThrownBy(() -> service.getNotificationById("notif-1", null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getNotificationById_notFound_throwsException() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationById("unknown-id", "cust-user-1", "ROLE_CUSTOMER"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void getNotificationById_deniesAccessToDifferentCustomer() {
        when(repository.findById(testNotification.getId())).thenReturn(Optional.of(testNotification));

        // Different customer ID attempting to view cust-user-1's notification
        assertThatThrownBy(() -> service.getNotificationById(testNotification.getId(), "cust-attacker", "ROLE_CUSTOMER"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getNotificationById_allowsOwnerCustomer() {
        when(repository.findById(testNotification.getId())).thenReturn(Optional.of(testNotification));

        NotificationResponse response = service.getNotificationById(testNotification.getId(), "cust-user-1", "ROLE_CUSTOMER");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testNotification.getId());
        assertThat(response.recipientId()).isEqualTo("cust-user-1");
    }

    @Test
    void getNotificationById_allowsPrivilegedRoles() {
        when(repository.findById(testNotification.getId())).thenReturn(Optional.of(testNotification));

        // Admin accessing customer's notification
        NotificationResponse adminResponse = service.getNotificationById(testNotification.getId(), "admin-1", "ROLE_ADMIN");
        assertThat(adminResponse).isNotNull();

        // Dispatcher accessing customer's notification
        NotificationResponse dispatcherResponse = service.getNotificationById(testNotification.getId(), "disp-1", "ROLE_DISPATCHER");
        assertThat(dispatcherResponse).isNotNull();
    }

    @Test
    void markAsRead_successForOwner() {
        when(repository.findById(testNotification.getId())).thenReturn(Optional.of(testNotification));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.markAsRead(testNotification.getId(), "cust-user-1", "ROLE_CUSTOMER");

        assertThat(response.isRead()).isTrue();
        assertThat(response.readAt()).isNotNull();
        verify(repository).save(testNotification);
    }

    @Test
    void markAsRead_deniesNonOwner() {
        when(repository.findById(testNotification.getId())).thenReturn(Optional.of(testNotification));

        assertThatThrownBy(() -> service.markAsRead(testNotification.getId(), "cust-other", "ROLE_CUSTOMER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void markAllAsRead_marksAllUnreadNotifications() {
        Notification n1 = new Notification("e1", "p1", "TRK1", "cust-user-1", null, null, NotificationChannel.EMAIL, NotificationType.BOOKING_CONFIRMED, "S1", "M1", DeliveryStatus.SENT);
        Notification n2 = new Notification("e2", "p2", "TRK2", "cust-user-1", null, null, NotificationChannel.EMAIL, NotificationType.OUT_FOR_DELIVERY, "S2", "M2", DeliveryStatus.SENT);

        when(repository.findByRecipientIdAndIsReadFalse("cust-user-1")).thenReturn(List.of(n1, n2));

        long count = service.markAllAsRead("cust-user-1");

        assertThat(count).isEqualTo(2);
        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(repository).saveAll(List.of(n1, n2));
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(repository.countByRecipientIdAndIsRead("cust-user-1", false)).thenReturn(5L);

        long count = service.getUnreadCount("cust-user-1");

        assertThat(count).isEqualTo(5L);
    }
}
