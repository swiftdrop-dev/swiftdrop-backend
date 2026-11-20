package com.swiftdrop.merchant.security;

import java.security.Principal;
import java.util.UUID;

public class UserPrincipal implements Principal {

    private final UUID id;
    private final String email;
    private final String role;

    public UserPrincipal(UUID id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getName() {
        return email != null ? email : (id != null ? id.toString() : "");
    }
}
