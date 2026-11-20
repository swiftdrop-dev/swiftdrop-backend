package com.swiftdrop.parcel.exception;

public class AssignmentConflictException extends RuntimeException {
    public AssignmentConflictException(String parcelId, Throwable cause) {
        super("Parcel already has an active assignment: " + parcelId, cause);
    }
}
