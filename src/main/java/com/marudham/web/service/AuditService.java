package com.marudham.web.service;

import com.marudham.web.model.AuditEvent;
import com.marudham.web.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String eventType, String description, String username, Long referenceId) {
        try {
            repository.save(new AuditEvent(eventType, description, username, referenceId));
        } catch (Exception ignored) {
            // Logging audit should not fail the main transaction
        }
    }
}
