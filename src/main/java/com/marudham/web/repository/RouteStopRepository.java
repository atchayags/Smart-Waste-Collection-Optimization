package com.marudham.web.repository;

import com.marudham.web.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findByRoutePlanIdOrderByStopOrderAsc(Long routePlanId);
    Optional<RouteStop> findFirstByRoutePlanIdAndStatusOrderByStopOrderAsc(Long routePlanId, String status);
}
