package com.swiftdrop.parcel.customer;

import jakarta.persistence.*;

@Embeddable
public class StoredAddress {
    @Column(length = 200) String street;
    @Column(length = 100) String city;
    @Column(length = 100) String district;
    @Column(length = 5) String postalCode;

    protected StoredAddress() {}
    StoredAddress(BookingContracts.Address address) {
        street = address.street().trim();
        city = address.city().trim();
        district = address.district().trim();
        postalCode = address.postalCode();
    }
    BookingContracts.Address view() { return new BookingContracts.Address(street, city, district, postalCode); }
}
