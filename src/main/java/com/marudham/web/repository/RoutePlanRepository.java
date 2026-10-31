package com.marudham.web.repository;

import com.marudham.web.model.RoutePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoutePlanRepository extends JpaRepository<RoutePlan, Long> {
    List<RoutePlan> findAllByOrderByCreatedAtDesc();
    List<RoutePlan> findByStatusOrderByCreatedAtDesc(String status);
    List<RoutePlan> findByTruckIdOrderByCreatedAtDesc(String truckId);
    Optional<RoutePlan> findFirstByTruckIdAndStatusInOrderByCreatedAtDesc(String truckId, List<String> statuses);
    long countByStatus(String status);
}
