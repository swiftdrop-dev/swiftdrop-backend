package com.swiftdrop.gateway.model;

public class CourierRecord {

    private String id;
    private String name;
    private String phone;
    private String vehicleType;
    private String vehiclePlate;
    private double rating;
    private boolean isAvailable;

    public CourierRecord() {
    }

    public CourierRecord(String id, String name, String phone, String vehicleType, String vehiclePlate, double rating, boolean isAvailable) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.vehicleType = vehicleType;
        this.vehiclePlate = vehiclePlate;
        this.rating = rating;
        this.isAvailable = isAvailable;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }
}
