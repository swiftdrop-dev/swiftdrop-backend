package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parcels", schema = "public")
public class Parcel {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    protected Parcel() {
    }

    public Parcel(String id, String courierId) {
        this.id = id;
        this.courierId = courierId;
    }

    public String getId() { return id; }
    public String getCourierId() { return courierId; }

    public void assignTo(String courierId) {
        this.courierId = courierId;
    }
}
