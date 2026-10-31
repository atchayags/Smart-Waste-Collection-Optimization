package com.marudham.web.repository;

import com.marudham.web.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, String> {
    Optional<Truck> findByAssignedDriverUsername(String assignedDriverUsername);
    List<Truck> findByStatus(String status);
}
