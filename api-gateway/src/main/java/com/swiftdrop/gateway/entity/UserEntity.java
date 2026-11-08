package com.swiftdrop.gateway.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "phone", length = 32)
    private String phone;

    public UserEntity() {
    }

    public UserEntity(String id, String email, String name, String role, String phone) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
