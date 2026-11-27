package com.swiftdrop.dispatch.exception;

import com.swiftdrop.dispatch.entity.DispatchJob;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchConflictResponseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void directoryFailureAndConcurrentUpdatesUseConflictStatuses() {
        var unavailable = handler.handleDirectoryUnavailable(
                new CourierDirectoryUnavailableException("Courier directory is unavailable"));
        var conflict = handler.handleAssignmentConflict(
                new AssignmentConflictException("Courier already has an active offer or assignment"));
        var locked = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(DispatchJob.class, UUID.randomUUID()));

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getBody().getMessage()).isEqualTo("Courier directory is unavailable");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(locked.getBody().getMessage()).isEqualTo("The dispatch job changed. Refresh and try again.");
    }
}
