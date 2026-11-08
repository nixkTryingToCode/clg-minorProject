package com.nixk.recruit.service;

import com.nixk.recruit.model.AuditLog;
import com.nixk.recruit.repo.AuditLogRepo;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepo repo;

    public AuditService(AuditLogRepo repo) {
        this.repo = repo;
    }

    public void log(Long actorId, String action, String entity, Long entityId, String details) {
        AuditLog l = new AuditLog();
        l.actorId = actorId;
        l.action = action;
        l.entity = entity;
        l.entityId = entityId;
        l.details = details != null && details.length() > 900 ? details.substring(0, 900) : details;
        repo.save(l);
    }
}
