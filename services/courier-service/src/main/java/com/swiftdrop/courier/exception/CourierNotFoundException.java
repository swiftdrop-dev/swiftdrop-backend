package com.swiftdrop.courier.exception;

public class CourierNotFoundException extends RuntimeException {
    public CourierNotFoundException(String id) {
        super("Courier not found: " + id);
    }
}
