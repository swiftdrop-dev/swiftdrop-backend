package com.swiftdrop.parcel.service;

public class DeliveryCompletionPolicyNotApprovedException extends RuntimeException {
    public DeliveryCompletionPolicyNotApprovedException() {
        super("Delivery completion is disabled until proof validity and delivered_at policies are approved");
    }
}
