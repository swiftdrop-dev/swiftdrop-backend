package com.swiftdrop.gateway.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "couriers")
public class CourierEntity {

    @Id
    @Column(name = "id", length = 64)
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
    private Boolean isAvailable;

    @Column(name = "deliveries_today")
    private Integer deliveriesToday;

    @Column(name = "total_deliveries")
    private Integer totalDeliveries;

    public CourierEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    public Integer getDeliveriesToday() { return deliveriesToday; }
    public void setDeliveriesToday(Integer deliveriesToday) { this.deliveriesToday = deliveriesToday; }

    public Integer getTotalDeliveries() { return totalDeliveries; }
    public void setTotalDeliveries(Integer totalDeliveries) { this.totalDeliveries = totalDeliveries; }
}
