package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.enums.ContactType;

import java.util.UUID;

public class MerchantContactResponse {

    private UUID id;
    private ContactType contactType;
    private String name;
    private String phone;
    private String email;
    private String designation;

    public MerchantContactResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ContactType getContactType() {
        return contactType;
    }

    public void setContactType(ContactType contactType) {
        this.contactType = contactType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }
}
