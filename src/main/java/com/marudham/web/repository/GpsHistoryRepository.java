package com.marudham.web.repository;

import com.marudham.web.model.GpsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GpsHistoryRepository extends JpaRepository<GpsHistory, Long> {
    List<GpsHistory> findTop50ByTruckIdOrderByRecordedAtDesc(String truckId);
}
