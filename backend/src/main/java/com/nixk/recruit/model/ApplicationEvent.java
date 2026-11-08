package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_events")
public class ApplicationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long applicationId;
    public String eventType;
    @Column(length = 1000)
    public String details;
    public Instant createdAt = Instant.now();
}
