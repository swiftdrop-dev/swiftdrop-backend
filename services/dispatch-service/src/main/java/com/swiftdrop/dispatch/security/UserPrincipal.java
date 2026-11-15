package com.swiftdrop.dispatch.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated Auth Service identity. It intentionally contains no user, courier or dispatcher UUID. */
public final class UserPrincipal implements UserDetails {

    private final String email;
    private final String role;

    public UserPrincipal(String email, String role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() { return email; }

    public String getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT)));
    }

    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
