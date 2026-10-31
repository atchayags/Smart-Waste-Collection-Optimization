package com.marudham.web.repository;

import com.marudham.web.model.Depot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepotRepository extends JpaRepository<Depot, Long> {
    Optional<Depot> findFirstByActiveTrueOrderByIdAsc();
}
