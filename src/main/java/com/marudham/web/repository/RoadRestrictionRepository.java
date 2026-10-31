package com.marudham.web.repository;

import com.marudham.web.model.RoadRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadRestrictionRepository extends JpaRepository<RoadRestriction, Long> {
    List<RoadRestriction> findByActiveTrue();
    List<RoadRestriction> findAllByOrderByActivatedAtDesc();
    Optional<RoadRestriction> findByRoadIssueIdAndActiveTrue(Long roadIssueId);
    List<RoadRestriction> findByRoadIssueId(Long roadIssueId);
}
