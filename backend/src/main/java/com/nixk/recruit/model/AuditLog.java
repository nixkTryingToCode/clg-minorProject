package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long actorId;
    public String action;
    public String entity;
    public Long entityId;
    @Column(length = 1000)
    public String details;
    public Instant createdAt = Instant.now();
}
