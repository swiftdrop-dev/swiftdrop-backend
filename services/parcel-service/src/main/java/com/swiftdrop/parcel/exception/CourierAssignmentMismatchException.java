package com.swiftdrop.parcel.exception;

public class CourierAssignmentMismatchException extends RuntimeException {
    public CourierAssignmentMismatchException() {
        super("Courier is not the currently assigned courier for this parcel");
    }
}
