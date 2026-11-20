package com.swiftdrop.courier.repository;

import com.swiftdrop.courier.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, String> {
}
