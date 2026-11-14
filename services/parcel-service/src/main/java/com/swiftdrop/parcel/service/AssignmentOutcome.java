package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelAssignment;

public record AssignmentOutcome(ParcelAssignment assignment, boolean created) {
}
