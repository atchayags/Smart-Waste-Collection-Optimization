package com.marudham.web.repository;

import com.marudham.web.model.CollectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollectionRequestRepository extends JpaRepository<CollectionRequest, Long> {
    List<CollectionRequest> findAllByOrderByCreatedAtDesc();
    List<CollectionRequest> findAllByUsernameOrderByCreatedAtDesc(String username);
    List<CollectionRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<CollectionRequest> findByPointIdAndStatusIn(String pointId, List<String> statuses);
    long countByStatus(String status);
    long countByPriorityAndStatus(String priority, String status);
    List<CollectionRequest> findAllByAssignedRouteId(Long assignedRouteId);
}
