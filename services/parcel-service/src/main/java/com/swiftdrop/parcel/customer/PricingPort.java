package com.swiftdrop.parcel.customer;

import java.math.BigDecimal;

public interface PricingPort {
    record Quote(BigDecimal totalFee, int estimatedDays) {}
    Quote quote(BookingContracts.CreateBooking request);
}
