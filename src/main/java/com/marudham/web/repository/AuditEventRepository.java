package com.marudham.web.repository;

import com.marudham.web.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop100ByOrderByCreatedAtDesc();
}
