package com.swiftdrop.auth.dto.response;

/**
 * Returned after a successful login or registration.
 * Never includes the password or its hash.
 */
public class AuthResponse {

    private String token;
    private String id;
    private String name;
    private String email;
    private String role;   // lowercase to match the frontend UserRole type

    public AuthResponse() {}

    public AuthResponse(String token, String id, String name, String email, String role) {
        this.token = token;
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

