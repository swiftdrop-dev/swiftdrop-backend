package com.swiftdrop.auth.entity;

public enum Role {
    customer,
    merchant,
    courier,
    dispatcher;

    public static Role fromString(String value) {
        if (value == null) return null;
        return Role.valueOf(value.trim().toLowerCase());
    }
}


