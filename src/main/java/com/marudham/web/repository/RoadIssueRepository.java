package com.marudham.web.repository;

import com.marudham.web.model.RoadIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoadIssueRepository extends JpaRepository<RoadIssue, Long> {
    List<RoadIssue> findAllByOrderByCreatedAtDesc();
    List<RoadIssue> findByStatusOrderByCreatedAtDesc(String status);
    List<RoadIssue> findByReportingDriverUsernameOrderByCreatedAtDesc(String driverUsername);
    List<RoadIssue> findByTruckIdOrderByCreatedAtDesc(String truckId);
    List<RoadIssue> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
}
