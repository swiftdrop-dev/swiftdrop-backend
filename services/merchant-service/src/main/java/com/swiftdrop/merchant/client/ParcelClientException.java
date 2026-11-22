package com.swiftdrop.merchant.client;
/** No upstream body, URL, credentials or cause is retained. */
public final class ParcelClientException extends RuntimeException {
    private final int status;
    private final boolean definitive;
    public ParcelClientException(int status,boolean definitive) { super("Parcel service request failed"); this.status=status; this.definitive=definitive; }
    public int status() { return status; }
    public boolean definitive() { return definitive; }
}
