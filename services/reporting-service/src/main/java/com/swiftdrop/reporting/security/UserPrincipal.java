package com.swiftdrop.reporting.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Authenticated identity holding canonical user UUID, email, and normalized role. */
public final class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String role;

    public UserPrincipal(UUID id, String email, String role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public UUID getId() { return id; }

    public String getEmail() { return email; }

    public String getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT)));
    }

    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return email != null ? email : (id != null ? id.toString() : ""); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
