package com.marudham.web.repository;

import com.marudham.web.model.RouteRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRevisionRepository extends JpaRepository<RouteRevision, Long> {
    List<RouteRevision> findByRoutePlanIdOrderByRevisionNumberDesc(Long routePlanId);
    List<RouteRevision> findByRoadIssueIdOrderByRevisedAtDesc(Long roadIssueId);
    List<RouteRevision> findAllByOrderByRevisedAtDesc();
}
