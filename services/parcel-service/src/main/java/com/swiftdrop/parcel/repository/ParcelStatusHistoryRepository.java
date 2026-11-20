package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, Long> {

    List<ParcelStatusHistory> findByParcelIdOrderByChangedAtAscIdAsc(String parcelId);
}
