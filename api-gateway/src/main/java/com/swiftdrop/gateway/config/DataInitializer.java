package com.swiftdrop.gateway.config;

import com.swiftdrop.gateway.entity.CourierEntity;
import com.swiftdrop.gateway.entity.ParcelEntity;
import com.swiftdrop.gateway.entity.UserEntity;
import com.swiftdrop.gateway.repository.CourierRepository;
import com.swiftdrop.gateway.repository.ParcelRepository;
import com.swiftdrop.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final ParcelRepository parcelRepository;
    private final CourierRepository courierRepository;
    private final UserRepository userRepository;

    public DataInitializer(ParcelRepository parcelRepository,
                           CourierRepository courierRepository,
                           UserRepository userRepository) {
        this.parcelRepository = parcelRepository;
        this.courierRepository = courierRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        try {
            seedUsers();
            seedCouriers();
            seedParcels();
            logger.info("SwiftDrop Database initialization check completed successfully.");
        } catch (Exception e) {
            logger.warn("Database initialization skipped or encountered error: {}", e.getMessage());
        }
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            logger.info("Seeding initial users into PostgreSQL database...");
            userRepository.saveAll(List.of(
                    new UserEntity("usr_001", "customer@demo.com", "Amal Perera", "customer", "077-1234567"),
                    new UserEntity("usr_002", "merchant@demo.com", "Lanka Traders", "merchant", "011-5678900"),
                    new UserEntity("usr_003", "courier@demo.com", "Nimal Senanayake", "courier", "076-9876543"),
                    new UserEntity("usr_004", "dispatcher@demo.com", "Saman Kumarasinghe", "dispatcher", "011-2345678")
            ));
        }
    }

    private void seedCouriers() {
        if (courierRepository.count() == 0) {
            logger.info("Seeding initial couriers into PostgreSQL database...");
            CourierEntity c1 = new CourierEntity();
            c1.setId("cou_001");
            c1.setName("Nimal Senanayake");
            c1.setEmail("nimal@swiftdrop.lk");
            c1.setPhone("076-9876543");
            c1.setVehicleType("Bike");
            c1.setVehiclePlate("WP-2345");
            c1.setRating(4.8);
            c1.setIsAvailable(true);
            c1.setDeliveriesToday(8);
            c1.setTotalDeliveries(342);

            CourierEntity c2 = new CourierEntity();
            c2.setId("cou_002");
            c2.setName("Kasun Fernando");
            c2.setEmail("kasun@swiftdrop.lk");
            c2.setPhone("071-2345678");
            c2.setVehicleType("Scooter");
            c2.setVehiclePlate("CP-9876");
            c2.setRating(4.6);
            c2.setIsAvailable(true);
            c2.setDeliveriesToday(5);
            c2.setTotalDeliveries(210);

            CourierEntity c3 = new CourierEntity();
            c3.setId("cou_003");
            c3.setName("Priya Ranasinghe");
            c3.setEmail("priya@swiftdrop.lk");
            c3.setPhone("070-5566778");
            c3.setVehicleType("Van");
            c3.setVehiclePlate("SP-1122");
            c3.setRating(4.9);
            c3.setIsAvailable(false);
            c3.setDeliveriesToday(12);
            c3.setTotalDeliveries(580);

            courierRepository.saveAll(List.of(c1, c2, c3));
        }
    }

    private void seedParcels() {
        if (parcelRepository.count() == 0) {
            logger.info("Seeding initial parcels into PostgreSQL database...");

            ParcelEntity p1 = new ParcelEntity();
            p1.setId("prc_001");
            p1.setTrackingNumber("SWF-2024-001234");
            p1.setStatus("delivered");
            p1.setSenderId("usr_001");
            p1.setSenderName("Amal Perera");
            p1.setSenderPhone("077-1234567");
            p1.setSenderType("customer");
            p1.setRecipientName("Kamani Silva");
            p1.setRecipientPhone("072-3456789");
            p1.setRecipientAddress("45 Galle Road, Colombo");
            p1.setPickupAddress("12 Kandy Road, Colombo");
            p1.setDescription("Electronics - Laptop");
            p1.setWeight(2.5);
            p1.setSize("md");
            p1.setDeliveryFee(350.0);
            p1.setTotalFee(435.0);
            p1.setPaymentStatus("paid");
            p1.setPaymentMethod("online");
            p1.setCourierId("cou_001");
            p1.setCourierName("Nimal Senanayake");
            p1.setCourierPhone("076-9876543");
            p1.setCreatedAt(LocalDateTime.now().minusDays(3));
            p1.setDeliveredAt(LocalDateTime.now().minusDays(2));
            p1.setEstimatedDeliveryDate("2024-11-01");

            ParcelEntity p2 = new ParcelEntity();
            p2.setId("prc_002");
            p2.setTrackingNumber("SWF-2024-001235");
            p2.setStatus("in_transit");
            p2.setSenderId("usr_001");
            p2.setSenderName("Amal Perera");
            p2.setSenderPhone("077-1234567");
            p2.setSenderType("customer");
            p2.setRecipientName("Roshan Mendis");
            p2.setRecipientPhone("071-8765432");
            p2.setRecipientAddress("88 Flower Road, Kandy");
            p2.setPickupAddress("12 Kandy Road, Colombo");
            p2.setDescription("Clothes & Accessories");
            p2.setWeight(0.8);
            p2.setSize("sm");
            p2.setDeliveryFee(350.0);
            p2.setTotalFee(380.0);
            p2.setPaymentStatus("paid");
            p2.setPaymentMethod("online");
            p2.setCourierId("cou_002");
            p2.setCourierName("Kasun Fernando");
            p2.setCourierPhone("071-2345678");
            p2.setCreatedAt(LocalDateTime.now().minusDays(1));
            p2.setEstimatedDeliveryDate("2024-11-10");

            ParcelEntity p3 = new ParcelEntity();
            p3.setId("prc_003");
            p3.setTrackingNumber("SWF-2024-001236");
            p3.setStatus("pending");
            p3.setSenderId("usr_001");
            p3.setSenderName("Amal Perera");
            p3.setSenderPhone("077-1234567");
            p3.setSenderType("customer");
            p3.setRecipientName("Dilini Jayasekara");
            p3.setRecipientPhone("075-5554433");
            p3.setRecipientAddress("22 Main Street, Galle");
            p3.setPickupAddress("12 Kandy Road, Colombo");
            p3.setDescription("Books");
            p3.setWeight(1.2);
            p3.setSize("sm");
            p3.setDeliveryFee(350.0);
            p3.setTotalFee(350.0);
            p3.setPaymentStatus("pending");
            p3.setPaymentMethod("cod");
            p3.setCourierName("Unassigned");
            p3.setCourierPhone("-");
            p3.setCreatedAt(LocalDateTime.now());
            p3.setEstimatedDeliveryDate("2024-11-12");

            parcelRepository.saveAll(List.of(p1, p2, p3));
        }
    }
}
