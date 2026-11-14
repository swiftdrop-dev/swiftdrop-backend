package com.swiftdrop.courier.service;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.entity.Courier;
import com.swiftdrop.courier.exception.CourierNotFoundException;
import com.swiftdrop.courier.repository.CourierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {
    @Mock
    private CourierRepository courierRepository;

    @InjectMocks
    private CourierService courierService;

    @Test
    void returnsCourierDetailsWhenFound() {
        Courier courier = new Courier("cou_001", "Nimal Senanayake", "nimal@example.com",
                "076-9876543", "motorcycle", "ABC-1234", 4.8, true, 3, 120);
        when(courierRepository.findById("cou_001")).thenReturn(Optional.of(courier));

        CourierResponse response = courierService.getCourierById("cou_001");

        assertEquals("cou_001", response.id());
        assertEquals("Nimal Senanayake", response.name());
        assertEquals(true, response.available());
        assertEquals(120, response.totalDeliveries());
        verify(courierRepository).findById("cou_001");
    }

    @Test
    void throwsNotFoundWhenCourierDoesNotExist() {
        when(courierRepository.findById("missing")).thenReturn(Optional.empty());

        CourierNotFoundException exception = assertThrows(CourierNotFoundException.class,
                () -> courierService.getCourierById("missing"));

        assertEquals("Courier not found: missing", exception.getMessage());
    }
}
