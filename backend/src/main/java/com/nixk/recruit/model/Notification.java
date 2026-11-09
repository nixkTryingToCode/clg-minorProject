package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long userId;
    public String type;
    @Column(length = 1000)
    public String message;
    public Long linkId;
    public Boolean seen = false;
    public Instant createdAt = Instant.now();
}
