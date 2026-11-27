package com.swiftdrop.parcel.exception;

public class CourierAssignmentMismatchException extends RuntimeException {
    public CourierAssignmentMismatchException() {
        this("Courier is not the currently assigned courier for this parcel");
    }

    public CourierAssignmentMismatchException(String message) {
        super(message);
    }
}
