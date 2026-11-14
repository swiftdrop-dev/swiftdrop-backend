package com.swiftdrop.parcel.customer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

/** Frontend-equivalent demonstration pricing. Never enabled in Neon profile. */
@Component
@Profile("demo")
class DemoPricing implements PricingPort {
    private static final Map<String, Integer> BASE = Map.of("xs", 150, "sm", 250, "md", 350, "lg", 550, "xl", 850);
    private static final Set<String> FAR = Set.of("Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu", "Hambantota", "Batticaloa", "Trincomalee", "Ampara");
    @Override public Quote quote(BookingContracts.CreateBooking request) {
        String district = request.recipientAddress().district().trim();
        boolean far = FAR.contains(district);
        BigDecimal fee = BigDecimal.valueOf(BASE.get(request.size()) + (far ? 300 : district.equals("Colombo") ? 0 : 100) + (request.fragile() ? 75 : 0))
                .add(request.weight().setScale(0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(50)));
        return new Quote(fee, far ? 3 : district.equals("Colombo") ? 1 : 2);
    }
}
