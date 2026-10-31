package com.marudham.web.repository;

import com.marudham.web.model.CollectionPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollectionPointRepository extends JpaRepository<CollectionPoint, String> {
    List<CollectionPoint> findByActiveTrueOrderByNameAsc();
    List<CollectionPoint> findAllByOrderByIdAsc();
}
