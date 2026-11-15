package com.swiftdrop.merchant.security;

import java.security.Principal;

public class UserPrincipal implements Principal {

    private final String email;
    private final String role;

    public UserPrincipal(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getName() {
        return email;
    }
}
