package com.swiftdrop.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "couriers", schema = "public")
public class Courier {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "vehicle_type", length = 32)
    private String vehicleType;

    @Column(name = "vehicle_plate", length = 32)
    private String vehiclePlate;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "is_available")
    private Boolean available;

    @Column(name = "deliveries_today")
    private Integer deliveriesToday;

    @Column(name = "total_deliveries")
    private Integer totalDeliveries;

    protected Courier() {
    }

    public Courier(String id, String name, String email, String phone, String vehicleType,
                   String vehiclePlate, Double rating, Boolean available,
                   Integer deliveriesToday, Integer totalDeliveries) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.rating = rating;
        this.available = available;
        this.deliveriesToday = deliveriesToday;
        this.totalDeliveries = totalDeliveries;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getVehicleType() { return vehicleType; }
    public String getVehiclePlate() { return vehiclePlate; }
    public Double getRating() { return rating; }
    public Boolean getAvailable() { return available; }
    public Integer getDeliveriesToday() { return deliveriesToday; }
    public Integer getTotalDeliveries() { return totalDeliveries; }
}
