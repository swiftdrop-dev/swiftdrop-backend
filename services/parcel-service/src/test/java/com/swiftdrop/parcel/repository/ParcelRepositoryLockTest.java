package com.swiftdrop.parcel.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParcelRepositoryLockTest {
    @Test
    void parcelLookupUsesPessimisticWriteLock() throws NoSuchMethodException {
        var method = ParcelRepository.class.getMethod("findByIdForUpdate", String.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
